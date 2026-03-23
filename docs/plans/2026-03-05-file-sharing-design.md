# File Sharing Module — Design Brainstorm (Consolidated)

**Status**: Draft — architecture decisions made, implementation not started
**Date**: 2026-03-05 (updated 2026-03-23)

## Goal

Allow users to share small files (up to 5 MB) between their Octi-linked devices. Files are ephemeral — available for 48 hours, then automatically cleaned up.

**Use cases**: sharing a PDF, a config file, a photo, a small document between your own devices.

## Decisions Made

| Topic | Decision |
|-------|----------|
| **Transport** | Separate blob storage — file content NOT embedded in module JSON |
| **Blob architecture** | New `BlobStore` interface in `sync-core` |
| **Blob scoping** | Per-device + module namespace: `(deviceId, moduleId, blobKey)` |
| **Blob addressing** | UUID keys (not content-addressed). Content hash in metadata for integrity. Supports future mutable use cases |
| **Blob metadata** | Minimal: `size`, `createdAt`, `checksum` — just enough for server enforcement |
| **Backends** | Both GDrive + KServer from day one |
| **Cardinality** | Multiple files per device (with cap) |
| **Per-file limit** | 5 MB |
| **Account limit** | 25 MB total across all devices |
| **Quota enforcement** | Client-side + server-side (KServer real, GDrive faked) |
| **Blob API** | Okio `Source`/`Sink` streaming from the start |
| **Encryption** | Streaming AES-GCM cipher wrapping Source/Sink, not buffer-then-encrypt |
| **Expiry** | 48h client-side auto-cleanup, 72h KServer server-side enforcement |
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

    // Check quota (used / total, account-wide)
    suspend fun getQuota(): BlobQuota
}

// UUID-based key — supports both immutable and mutable blob patterns
@JvmInline
value class BlobKey(val id: String) // UUID string

// Minimal metadata — just enough for server-side enforcement
data class BlobMetadata(
    val size: Long,          // plaintext size in bytes (for user-facing quota)
    val createdAt: Instant,  // for server-side 72h expiry
    val checksum: String,    // SHA-256 of plaintext (for client-side integrity verification)
)

data class BlobQuota(
    val usedBytes: Long,
    val totalBytes: Long,
)

class BlobQuotaExceededException(
    val quota: BlobQuota,
    val requestedBytes: Long,
) : IOException()
```

**BlobManager** aggregates BlobStores from all configured connectors (like SyncManager aggregates ConnectorHubs). Replicates blobs to all backends.

### 2. Backend Implementations

**KServer (`syncs-kserver`)**
- New REST endpoints:
  - `PUT /v1/device/{deviceId}/module/{moduleId}/blob/{blobKey}` — upload raw bytes (streaming)
  - `GET /v1/device/{deviceId}/module/{moduleId}/blob/{blobKey}` — download raw bytes
  - `DELETE /v1/device/{deviceId}/module/{moduleId}/blob/{blobKey}` — delete
  - `GET /v1/device/{deviceId}/module/{moduleId}/blobs` — list blob keys
  - `GET /v1/account/blob-quota` — returns used/total (account-wide)
- Server-side enforcement: reject PUT if quota exceeded (return specific HTTP error)
- Server-side expiry: auto-delete blobs older than 72h
- Payload encryption: streaming AES-GCM before upload

**GDrive (`syncs-gdrive`)**
- Each blob stored as a separate file in AppData folder
  - Path: `{deviceFolder}/{moduleId}/blob_{blobKey}`
  - Raw encrypted bytes, no JSON wrapping, no base64
- `getQuota()`: faked client-side — sum blob sizes from local metadata
- Quota enforcement: client-side check before upload, throw `BlobQuotaExceededException`
- No server-side expiry — relies on client cleanup

### 3. Streaming Encryption

Existing `PayloadEncryption` (Crypti) operates on full `ByteString` — not suitable for streaming.

New component: **`StreamingCipher`** (in `sync-core` or `app-common`)

```kotlin
// Wraps a Source with encryption (for upload)
fun Source.encrypt(key: SecretKey): Source

// Wraps a Source with decryption (for download)
fun Source.decrypt(key: SecretKey): Source
```

**Approach**: AES-GCM with chunked streaming
- Split plaintext into fixed-size chunks (e.g., 64 KB)
- Each chunk encrypted independently with unique nonce (chunk index as nonce component)
- Chunk format: `[nonce | ciphertext | auth_tag]`
- File header: `[version | total_chunks | original_size]`
- Decryption verifies each chunk's auth tag independently
- Uses the same key material as Crypti (derived from the device's sync encryption key)

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
        @SerialName("blobKey") val blobKey: String,  // references BlobStore UUID key
        @SerialName("sharedAt") val sharedAt: Instant,
        @SerialName("expiresAt") val expiresAt: Instant,  // sharedAt + 48h
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

### 5. Data Flow

**Sharing a file (upload):**
```
User picks file via SAF (ACTION_OPEN_DOCUMENT)
  → ContentResolver.openInputStream(uri) → Okio source
  → Generate blobKey (UUID)
  → BlobManager.put(ourDeviceId, FILES_MODULE_ID, blobKey, source, metadata)
    → KServerBlobStore.put() — streaming encrypt → upload
    → GDriveBlobStore.put() — streaming encrypt → upload to AppData
  → Update FileShareInfo: add SharedFile(name, mime, size, blobKey, sharedAt, expiresAt)
  → Normal module sync writes metadata
```

**Receiving a file (download):**
```
FileShareRepo.state.others contains remote devices' FileShareInfo
  → User sees file list on dashboard / full screen
  → User taps "Save" on a file
  → SAF (ACTION_CREATE_DOCUMENT) → user picks save location
  → BlobManager.get(remoteDeviceId, FILES_MODULE_ID, blobKey) → Source
  → Streaming decrypt → ContentResolver.openOutputStream(uri) → Okio sink
```

**Expiry cleanup:**
```
On each sync cycle:
  → FileShareHandler filters out files where now > expiresAt
  → For expired files: BlobManager.delete(deviceId, FILES_MODULE_ID, blobKey)
  → Updated FileShareInfo (without expired entries) syncs normally
  → KServer independently deletes blobs > 72h (safety net)
```

### 6. Quota Flow

```
Before upload:
  1. Check file size ≤ 5 MB (client-side, instant)
  2. BlobManager.getQuota() → aggregate from all BlobStores
     - KServer: real server quota API
     - GDrive: sum of local metadata sizes
  3. If usedBytes + fileSize > 25 MB → show error to user
  4. If OK → proceed with upload
  5. KServer may still reject (race condition) → catch BlobQuotaExceededException → show error
```

### 7. UI

**Dashboard tile** (`FileShareModuleTile`)
- Shows file count: "3 files shared" or "No files"
- Shows latest file name + time ago
- Tap → navigate to full screen list

**Full screen file list** (`FileShareListScreen`)
- Nav destination: `Nav.Main.FileShareList(deviceId: DeviceId)`
- List of shared files with: name, size, mime icon, time remaining
- Actions per file:
  - "Save" → SAF save dialog
  - "Delete" (own device only) → remove from list + delete blob
- Own device: FAB to share a new file (SAF pick dialog)
- Show account quota usage (e.g., "12 MB / 25 MB used")

---

## Metadata Split

Two layers of metadata serve different audiences:

| Where | What | Why |
|-------|------|-----|
| `BlobMetadata` (stored with blob) | size, createdAt, checksum | Server enforcement: quota, 72h expiry, integrity |
| `FileShareInfo.SharedFile` (module sync) | name, mimeType, blobKey, sharedAt, expiresAt | App logic: UI display, client-side 48h expiry |

---

## Open Items (to resolve during implementation)

1. **BlobManager multi-backend replication**: Upload to all backends in parallel? Sequential? What if one fails?
2. **Notifications for incoming files**: Notify when another device shares a file? Or just show on next dashboard visit?
3. **Progress indication**: Streaming enables progress tracking (bytes written / total). Wire to UI?
4. **Offline handling**: Retry queue if one backend is unreachable during upload?
5. **KServer API details**: Auth, rate limiting, content-type headers for blob endpoints
6. **Streaming cipher details**: Chunk size, auth tag handling, nonce derivation
7. **Module ordering**: Where does file share tile appear in dashboard order?

---

## Files to Create/Modify

### New: `sync-core` additions
- `BlobStore.kt` — interface + BlobMetadata + BlobQuota
- `BlobKey.kt` — value class
- `BlobQuotaExceededException.kt`
- `BlobManager.kt` — aggregator
- `StreamingCipher.kt` — AES-GCM streaming encryption

### New: `syncs-kserver` additions
- `KServerBlobStore.kt` — KServer BlobStore implementation
- `KServerBlobApi.kt` — Retrofit endpoints for blob CRUD + quota

### New: `syncs-gdrive` additions
- `GDriveBlobStore.kt` — GDrive AppData BlobStore implementation

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
- KServer (separate repo) — new blob endpoints + quota + 72h cleanup

## Next Steps

1. Implement `BlobStore` interface + `StreamingCipher` in `sync-core`
2. Implement `KServerBlobStore` + KServer API changes
3. Implement `GDriveBlobStore`
4. Implement `modules-files` module (data model → serializer → handler → settings → cache → sync → repo → DI)
5. Implement UI (dashboard tile → file list screen)
6. Integration testing across both backends
