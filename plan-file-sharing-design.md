# File Sharing Module — Design Brainstorm (Consolidated)

**Status**: Implemented (pending commit). Hardening + supervisor review applied 2026-04-16; see `plan-file-sharing-hardening.md` for the review layer. Open-items list below tracks explicit deferrals.
**Date**: 2026-03-05 (updated 2026-04-16)

## Goal

Allow users to share small files between their Octi-linked devices. Exact size, quota, and retention limits are connector-specific. For OctiServer v1, the target policy is 5 MB per file and 25 MB total. Files are logically ephemeral via synced metadata expiry; connectors may retain physical blobs longer as a cleanup safety net.

**Use cases**: sharing a PDF, a config file, a photo, a small document between your own devices.

## Decisions Made

| Topic | Decision |
|-------|----------|
| **Transport** | Separate blob storage — file content NOT embedded in module JSON |
| **Blob architecture** | New `BlobStore` interface in `sync-core` |
| **Blob scoping** | Per-device + module namespace: `(deviceId, moduleId, blobKey)` |
| **Blob addressing** | UUID keys, immutable in v1. New content gets a new `blobKey` |
| **Blob metadata** | Minimal: `size`, `createdAt`, `checksum` — just enough for server enforcement |
| **Backends** | Both GDrive + OctiServer from day one, with different capabilities/policies |
| **Cardinality** | Multiple files per device (with cap) |
| **Per-file limit** | Connector-specific. OctiServer v1 policy: 5 MB |
| **Account limit** | Connector-specific. OctiServer v1 policy: 25 MB total across all devices |
| **Quota enforcement** | Per connector. No global aggregated blob quota |
| **Blob API** | Okio `Source`/`Sink` streaming from the start |
| **Encryption** | OctiServer: vetted streaming AEAD using existing sync key material. GDrive: none (matches existing module pattern) |
| **Expiry** | Logical `expiresAt` in synced metadata (initial policy: 48h) + connector-specific physical cleanup. OctiServer hard-deletes at 72h as safety net |
| **Replication** | Best-effort per connector. Synced metadata records which connectors already have the blob; local retry state fills in missing mirrors |
| **Manual delete** | Yes, from file list screen |
| **UI** | Dashboard tile + full screen file list |
| **Serialization** | kotlinx.serialization (`@Serializable` / `@SerialName`) |
| **File picker** | SAF (`ACTION_OPEN_DOCUMENT` upload, `ACTION_CREATE_DOCUMENT` download) |

---

## Architecture

### 1. BlobStore — New Sync Layer Abstraction

Lives in `sync-core` alongside `SyncConnector`/`SyncManager`:

```kotlin
interface BlobStore {
    val connectorId: ConnectorId

    // Upload a blob (streaming, scoped to device + module)
    suspend fun put(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey, source: Source, metadata: BlobMetadata)

    // Download a blob (streaming)
    suspend fun get(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey): Source?

    // Get blob metadata without downloading content
    suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey): BlobMetadata?

    // Delete a blob
    suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey)

    // List all blobs for a device+module
    suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<BlobKey>

    // Connector-specific constraints/caps, if any
    suspend fun getConstraints(): BlobStoreConstraints

    // Connector-specific quota, if meaningful
    suspend fun getQuota(): BlobStoreQuota?
}

// UUID-based key — immutable in v1
@JvmInline
value class BlobKey(val id: String) // UUID string

// Minimal metadata — just enough for server-side enforcement
data class BlobMetadata(
    val size: Long,          // plaintext size in bytes (for user-facing quota)
    val createdAt: Instant,  // for server-side 72h expiry
    val checksum: String,    // SHA-256 of plaintext (for client-side integrity verification)
)

data class BlobStoreConstraints(
    val maxFileBytes: Long? = null,
    val maxTotalBytes: Long? = null,
)

data class BlobStoreQuota(
    val connectorId: ConnectorId,
    val usedBytes: Long,
    val totalBytes: Long,
)

class BlobQuotaExceededException(
    val quota: BlobStoreQuota,
    val requestedBytes: Long,
) : IOException()
```

**BlobManager** aggregates BlobStores from all configured connectors (like SyncManager aggregates ConnectorHubs). It uploads blobs best-effort per connector, keeps local retry state for failed mirrors, and does **not** expose a single aggregated quota. Read selection uses synced file metadata (`availableOn`) intersected with currently configured connectors.

### 2. Backend Implementations

Both backends use consistent patterns where possible, with documented differences where constraints diverge.

**OctiServer (`syncs-octiserver`)**
- New REST endpoints (matching existing query-param convention):
  - `PUT /v1/blob/{blobKey}?device-id={deviceId}&module-id={moduleId}` — upload raw bytes (streaming)
  - `GET /v1/blob/{blobKey}?device-id={deviceId}&module-id={moduleId}` — download raw bytes
  - `DELETE /v1/blob/{blobKey}?device-id={deviceId}&module-id={moduleId}` — delete single blob
  - `GET /v1/blobs?device-id={deviceId}&module-id={moduleId}` — list blob keys (params optional for broader queries)
  - `DELETE /v1/blobs?device-id={deviceId}` — bulk delete (e.g., device removal cleanup)
  - `GET /v1/blob-quota` — returns used/total (account-wide)
- Query params enable flexible filtering (advantage over path nesting):
  - Both params → specific device+module blobs (primary use case)
  - device-id only → all blobs for a device (device removal)
  - module-id only → all blobs for a module (module disable)
  - No params → all account blobs (admin/quota overview)
- Server-side enforcement: reject PUT if quota exceeded (return specific HTTP error)
- Server-side expiry: auto-delete blobs older than 72h
- Encryption: vetted streaming AEAD using existing sync key material before upload
- Blob metadata (size, createdAt, checksum) stored server-side in DB alongside blob

**GDrive (`syncs-gdrive`)**
- Each blob stored as a separate file in AppData folder
  - Path: `blob-store/{deviceId}/{moduleId}/{blobKey}` (separate top-level namespace to avoid colliding with existing module files under `devices/{deviceId}/{moduleId}`)
  - Raw bytes (no encryption — matches existing GDrive module data pattern)
- Blob metadata derived from GDrive file properties:
  - `size` → GDrive file size
  - `createdAt` → GDrive file `createdTime`
  - `checksum` → stored in GDrive custom file property
- No module-specific Octi quota in v1
  - `getQuota()` may expose connector-specific storage info if useful, but is not treated as a shared module quota
  - Upload validation is based on this connector's own constraints/capabilities, not a faked aggregate blob quota
- No server-side expiry — relies on client cleanup

### Backend Comparison

| Aspect | OctiServer | GDrive |
|--------|-----------|--------|
| **Encryption** | Vetted streaming AEAD (E2E) | None (trusts Google account security) |
| **Quota** | Real connector quota + enforcement (v1 policy: 5 MB/file, 25 MB total) | No Octi app blob quota; only connector capabilities / Drive errors |
| **Expiry** | Client tries at logical expiry; server hard-deletes at 72h | Client cleanup only |
| **Metadata storage** | Server DB | GDrive file properties |
| **API style** | Query params (matches existing module API) | GDrive SDK file operations |
| **Blob path** | `/v1/blob/{key}?device-id=&module-id=` | `blob-store/{deviceId}/{moduleId}/{key}` |

### 3. Streaming Encryption (OctiServer only)

Blobs on OctiServer must be encrypted at rest (matching existing module data pattern). GDrive blobs are unencrypted (also matching existing pattern). Existing `PayloadEncryption` (Crypti) operates on full `ByteString` — not suitable for streaming, but its key material should still be reused.

New component: **`StreamingCipher`** (in `sync-core` or `app-common`)

```kotlin
interface StreamingCipher {
    fun Source.encrypt(associatedData: ByteArray): Source
    fun Source.decrypt(associatedData: ByteArray): Source
}
```

**Approach**: use a vetted streaming AEAD design/library rather than inventing a custom chunk protocol.
- Reuse the same sync key material already managed by `PayloadEncryption`
- Preserve associated-data binding to `(deviceId, moduleId, blobKey)`
- Avoid custom nonce/chunk framing unless required by the chosen primitive
- Immutable blob keys in v1 mean "updated content" is modeled as a new blob, not an in-place overwrite

### 4. Module: `modules-files`

Follows existing module pattern (like clipboard) but metadata-only — no file content in module JSON.

**Data Model — `FileShareInfo`**
```kotlin
@Serializable
data class FileShareInfo(
    @SerialName("files") val files: List<SharedFile> = emptyList(),
) {
    @Serializable
    data class SharedFile(
        @SerialName("name") val name: String,
        @SerialName("mimeType") val mimeType: String,
        @SerialName("size") val size: Long,
        @SerialName("blobKey") val blobKey: String,  // immutable BlobStore UUID key
        @Serializable(with = InstantSerializer::class)
        @SerialName("sharedAt") val sharedAt: Instant,
        @Serializable(with = InstantSerializer::class)
        @SerialName("expiresAt") val expiresAt: Instant,  // logical expiry for UI/repo behavior
        @SerialName("availableOn") val availableOn: Set<String>,  // ConnectorId.idString values where blob upload already succeeded
    )
}
```

This is small JSON (a few KB) — syncs normally through the existing module pipeline.

**Module Components**

| Component | Role |
|-----------|------|
| `FileShareInfo` | Metadata-only data model (file list with blob keys) |
| `FileShareHandler` | `ModuleInfoSource` — manages file list, handles SAF pick/save, interacts with `BlobManager` |
| `FileShareSerializer` | `ModuleSerializer` — kotlinx.serialization JSON ↔ ByteString (metadata only) |
| `FileShareSettings` | `ModuleSettings` — DataStore with `isEnabled` |
| `FileShareCache` | `BaseModuleCache` — persists metadata to disk |
| `FileShareSync` | `BaseModuleSync` — syncs metadata via SyncManager |
| `FileShareRepo` | `BaseModuleRepo` — combines local + remote metadata |
| `FileShareModule` | Hilt DI — `@IntoSet` bindings |

### 5. Metadata Split

Two layers of metadata serve different audiences:

| Where | What | Why |
|-------|------|-----|
| `BlobMetadata` (stored with blob) | size, createdAt, checksum | Server enforcement: quota, 72h expiry, integrity |
| `FileShareInfo.SharedFile` (module sync) | name, mimeType, size, blobKey, sharedAt, expiresAt, availableOn | App logic: UI display, logical expiry, mirror discovery |
| Local retry state (not synced) | pending connector IDs, retry/backoff state, last error | Best-effort mirror completion without divergent per-connector file lists |

### 6. Data Flow

**Sharing a file (upload):**
```
User picks file via SAF (ACTION_OPEN_DOCUMENT)
  → ContentResolver.openInputStream(uri) → Okio source
  → Determine eligible BlobStores for this file (configured connectors + per-connector constraints)
  → Generate blobKey (UUID)
  → BlobManager.put(...) best-effort per eligible connector
    → OctiServerBlobStore.put() — vetted streaming AEAD encrypt → upload
    → GDriveBlobStore.put() — raw bytes → upload to AppData (no encryption)
  → If at least one connector succeeds:
      → Update FileShareInfo: add SharedFile(name, mime, size, blobKey, sharedAt, expiresAt, availableOn=<successful connector IDs>)
      → Normal module sync writes metadata
      → Local retry queue keeps retrying failed eligible connectors; each later success patches availableOn and re-syncs metadata
  → If all connectors fail:
      → No SharedFile entry is created
      → Show upload failure to user
```

**Receiving a file (download):**
```
FileShareRepo.state.others contains remote devices' FileShareInfo
  → User sees file list on dashboard / full screen
  → User taps "Save" on a file
  → SAF (ACTION_CREATE_DOCUMENT) → user picks save location
  → Candidate connectors = sharedFile.availableOn ∩ currently configured BlobStores
  → If candidate connectors is empty: show "not available on this device yet" and stop
  → BlobManager.get(remoteDeviceId, FILES_MODULE_ID, blobKey, candidates) → Source
    → Prefer user-configured/default connector order
    → If one candidate fails, try the next candidate
    → All successful mirrors are equivalent in v1 because blob keys are immutable
    → OctiServer: streaming decrypt / GDrive: raw bytes
  → Pipe to ContentResolver.openOutputStream(uri) → Okio sink
```

**Expiry cleanup:**
```
Always:
  → FileShareRepo / UI treat now > expiresAt as logically expired and hide the file

Connector-specific physical cleanup:
  → Local maintenance tries BlobManager.delete(...) for each connector in availableOn
  → As connector deletes succeed, availableOn shrinks and metadata syncs normally
  → Once availableOn is empty, remove the SharedFile entry entirely
  → OctiServer may still hard-delete blobs > 72h as a safety net
  → GDrive relies entirely on client cleanup
```

### 7. Quota Flow

```
Before upload:
  1. Determine eligible target BlobStores for this file
  2. Query each store's constraints/quota
     - OctiServer: real blob quota API + connector policy (v1: 5 MB/file, 25 MB total)
     - GDrive: connector-specific capabilities only; no Octi app blob quota in v1
  3. Validate against each eligible connector individually
     - effective upload behavior is derived from the connectors actually targeted for this file
     - there is no BlobManager-wide aggregate quota sum
  4. Attempt upload best-effort across eligible connectors
  5. Publish metadata only if at least one connector succeeded
  6. OctiServer may still reject due to races → catch BlobQuotaExceededException → surface connector-specific error
```

### 8. UI

**Dashboard tile** (`FileShareModuleTile`)
- Shows file count: "3 files shared" or "No files"
- Shows latest file name + time ago
- Tap → navigate to full screen list

**Full screen file list** (`FileShareListScreen`)
- Nav destination: `Nav.Main.FileShareList(deviceId: DeviceId)`
- List of shared files with: name, size, mime icon, time remaining
- If `availableOn` has no overlap with this device's configured connectors, show the file as unavailable/pending rather than offering "Save"
- Actions per file:
  - "Save" → SAF save dialog
  - "Delete" (own device only) → remove from list + delete blob
- Own device: FAB to share a new file (SAF pick dialog)
- Show connector-specific quota/limit information where meaningful (e.g., OctiServer "12 MB / 25 MB used")

---

## Open Items

Items still to resolve in follow-up work. Everything not listed here is resolved or covered by the hardening/review layer.

1. **Mirror read preference**: currently fixed iteration order over `candidates: Map<ConnectorId, RemoteBlobRef>` in `BlobManager.get`. User-selectable connector priority is not wired up — add if feedback shows the fixed order is wrong.
2. **Retry policy**: `BlobManager.retryBackoff` uses a static 5-minute backoff. Configurable backoff curve and persistence across process restarts are not implemented.
3. **Notifications for incoming files**: no push/system notification when another device shares a file. Files surface on next dashboard visit only.
4. **Progress indication**: streaming enables bytes-written / total tracking, but no UI surface for it yet. Add a progress channel on `FileShareService.shareFile` / `saveFile` when building the progress UI.
5. **Module ordering**: placement of the file-share tile on the dashboard is whatever falls out of the default injection order; intentional ordering TBD.

Resolved during implementation:

- **OctiServer API details** — basic auth via `Authorization` header, target device via `?device-id=`, content negotiation via kotlinx.serialization Json + `application/octet-stream` for raw blob body; rate-limiting handled server-side.
- **Streaming cipher choice** — Tink `AesGcmHkdfStreaming` with HKDF-SHA256 derivation from the existing sync keyset, AAD bound to `(deviceId, moduleId, blobKey)`. See `sync-core/.../blob/StreamingPayloadCipher.kt`.

---

## Files to Create/Modify

### New: `sync-core` additions
- `BlobStore.kt` — interface + BlobMetadata + BlobStoreConstraints + BlobStoreQuota
- `BlobKey.kt` — value class
- `BlobQuotaExceededException.kt`
- `BlobManager.kt` — aggregator + best-effort mirror coordination + retry tracking
- `StreamingCipher.kt` — vetted streaming AEAD wrapper (OctiServer only)

### New: `syncs-octiserver` additions
- `OctiServerBlobStore.kt` — OctiServer BlobStore implementation
- `OctiServerBlobApi.kt` — Retrofit endpoints for blob CRUD + quota + flexible list/delete

### New: `syncs-gdrive` additions
- `GDriveBlobStore.kt` — GDrive AppData BlobStore implementation
  - Storage path: `blob-store/{deviceId}/{moduleId}/{blobKey}`
  - Metadata via GDrive file properties (size, createdTime, custom checksum property)

### New: `modules-files/` (entire module)
- `build.gradle.kts`
- `FileShareInfo.kt`
- `FileShareHandler.kt`
- `FileShareSerializer.kt`
- `FileShareSettings.kt`
- `FileShareCache.kt`
- `FileShareSync.kt`
- `FileShareRepo.kt`
- `FileShareModule.kt`
- `ui/dashboard/FileShareModuleTile.kt`
- `ui/dashboard/FileShareDashState.kt`
- `ui/list/FileShareListScreen.kt`
- `ui/list/FileShareListVM.kt`
- `ui/list/FileShareListNavigation.kt`
- `src/main/res/values/strings.xml`
- `src/test/.../FileShareInfoSerializationTest.kt`

### Modified
- `settings.gradle` — add `:modules-files`
- `app/build.gradle.kts` — add `implementation(project(":modules-files"))`
- `app/.../Nav.kt` — add `Nav.Main.FileShareList`
- `app/.../DashboardVM.kt` — add `ModuleItem.FileShare`
- `sync-core/.../ConnectorHub.kt` — expose `BlobStore`
- `syncs-octiserver/.../OctiServerApi.kt` — add blob Retrofit endpoints
- OctiServer backend (separate repo) — new blob endpoints + quota + 72h cleanup

## Next Steps

1. Implement `BlobStore` interface + `StreamingCipher` in `sync-core`
2. Implement `OctiServerBlobStore` + OctiServer API changes
3. Implement `GDriveBlobStore`
4. Implement `modules-files` module (data model → serializer → handler → settings → cache → sync → repo → DI)
5. Implement UI (dashboard tile → file list screen)
6. Integration testing across both backends
