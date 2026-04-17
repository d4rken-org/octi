# File Sharing Hardening Plan — COMPLETED

**Status:** All 6 phases implemented and tested (2026-04-16). Supervisor review layer added on 2026-04-16 (see section below).

Comprehensive refactoring plan for the file sharing feature, based on code review, edge case analysis, and two rounds of server-side code review (`~/projects/octi/sync-server`).

Server hardening (8 fixes, 156 tests) has been completed separately. This plan covers client-side changes only.

## Supervisor Review Layer (2026-04-16)

A senior supervisor review (see `~/.claude/plans/swirling-mixing-book.md`) caught three critical bugs that the hardening phases did not address, traceable to a latent design gap in the `BlobStore` contract. Codex (`gpt-5.4`) second-opinion review found two additional low-severity items. All are now fixed. Unit tests green (full suite passes); `assembleDebug` clean.

### Root-cause refactor (fixes Bugs 1, 2, 3 with a single contract change)

**Bug 1** — Cross-device (and same-device-after-restart) downloads from OctiServer failed because `OctiServerBlobStore` resolved only from an in-memory `keyMapping` cache that never survives a receiving device / process restart.

**Bug 2** — `OctiServerConnector.writeServer()` fell back to the client logical UUID when `connectorRefs` lacked an entry for OctiServer, sending garbage blob IDs the server rejected. Additionally required every attachment to resolve, aborting the whole module commit on any miss — conflicting with best-effort replication.

**Bug 3** — `BlobMaintenance.pruneExpired()` updated `availableOn` but not `connectorRefs` on partial delete success, leaving stale refs that the next sync would still commit.

Refactor applied:

- **R1** — Added `@JvmInline @Serializable value class RemoteBlobRef(val value: String)` in `sync-core/BlobKey.kt`.
- **R2** — `BlobStore` contract: `put` returns `RemoteBlobRef`; `get/delete/getMetadata` take `RemoteBlobRef` (plus `BlobKey` on `get` for AAD); `list` returns `Set<RemoteBlobRef>`. Deleted `OctiServerBlobStore.keyMapping`, `resolveServerBlobId`, `resolveServerBlobIds`.
- **R3** — `BlobManager.get(candidates: Map<ConnectorId, RemoteBlobRef>)` and `delete(targets: Map<ConnectorId, RemoteBlobRef>)`. `PutResult.remoteRefs` now `Map<ConnectorId, RemoteBlobRef>`.
- **R4** — `OctiServerConnector.writeServer()` residency filter: `attachments.mapNotNull { it.connectorRefs[identifier.idString] }`. No logical-key fallback, no whole-module abort on partial resolution.
- **R5** — `FileShareHandler.updateLocations(blobKey, newAvailableOn, newConnectorRefs)` replaces `patchAvailableOn` + `patchConnectorRefs`. All four callers (`deleteOwnFile`, `retryMirrorUploads`, `pruneExpired`, `retryPendingDeletes`) now update both fields atomically.
- **R6** — Wire format: `SharedFile.connectorRefs` and `BlobAttachment.connectorRefs` stay `Map<String, String>` on the wire (JSON-friendly). Conversion to `Map<ConnectorId, RemoteBlobRef>` happens at API boundaries.

### Additional review fixes

- **Bug 4** — `OctiServerBlobStoreHub` now gates store creation on `capabilities.blobSupport == SUPPORTED` and adds capability TTLs (10 min for `LEGACY`/`UNKNOWN`, 1 h for `SUPPORTED`) with a `periodicTick` flow driving re-evaluation. An in-place server upgrade is picked up without app restart.
- **Bug 5** — `OctiServerBlobStore.getConstraints()` subtracts a conservative 1 KB buffer from the server's `maxBlobBytes` to account for Tink AEAD framing (~32 B header + 16 B per 1 MB segment tag). Files near the limit no longer pass preflight and then fail at session creation with 413.
- **Bug 6** — "Resumable upload" explicitly de-scoped to "chunked upload." Removed unused `OctiServerApi.getBlobSessionStatus` (HEAD endpoint). Server-side HEAD endpoint preserved for future reintroduction. Resume across process restarts is not implemented; current ≤10 MB blob sizes make restart-from-zero acceptable.

### Codex review follow-ups

- **Strict `connectorRefs` on read paths** — removed the `?: sharedFile.blobKey` fallback in `FileShareService.saveFile` / `deleteOwnFile` and all three `BlobMaintenance` maintenance methods. A connector listed in `availableOn` but missing from `connectorRefs` is now treated as unreachable. Safe because `shareFile` always writes both fields together and there is no pre-release production data.
- **Quota-flicker suppression** — `OctiServerBlobStoreHub.blobStores` now has `distinctUntilChangedBy { stores -> stores.map { it.connectorId }.toSet() }` so the `periodicTick` only propagates downstream when the effective store set actually changes, preventing `BlobManager.quotasFlow` from cycling through its placeholder-then-refetch every 10 minutes.

### New tests added in the review layer

- `BlobMaintenanceTest.pruneExpired — partial expiry shrinks both availableOn and connectorRefs together` (regression guard for Bug 3).
- `FileShareServiceTest.deleteOwnFile tombstone tracks remainingConnectors and both locations shrink together` (verifies the unified `updateLocations` pattern).
- `BlobManagerTest.FakeBlobStore` updated to the new contract (covers R2/R3).

### Not addressed (explicitly deferred)

- `commitModule` response ETag caching (extra round-trip per write) — defer until profiling shows it matters.
- Client-side session persistence / true resumable upload — revisit if blob size limits grow past ~10 MB.
- Narrow client↔server integration test (Codex recommendation) — best built as an additive PR alongside the refactor commit, not inside the refactor itself.

## Completion Summary

| Phase | Status | Key deliverables |
|-------|--------|-----------------|
| 1. BlobMaintenance characterization tests | ✅ | 10 tests across 3 @Nested groups |
| 2. Blob identity model | ✅ | connectorRefs on SharedFile, BlobAttachment, PutResult.remoteRefs, backfill |
| 3. OctiServer API fixes + capability detection | ✅ | Pre-encrypt + chunked upload (showstopper), If-Match/If-None-Match, sealed ModuleEtagResult, raw ETag handling, capability gating |
| 4. Upload protocol hardening | ✅ | 412 retry with fresh ETag |
| 5. Cleanup improvements | ✅ | PendingDelete tombstones (Map<String, PendingDelete>), age-gated startup temp cleanup |
| 6. Remaining tests + verification | ✅ | ~30 unit tests total, full test suite green, assembleDebug clean |

## Codex Feedback Integrated
- Every mutation of `availableOn` also mutates `connectorRefs` in the same transaction
- Seek to `session.offsetBytes` for resumable upload
- Raw ETag handling (no trim/re-quote) — forwards server-provided quoted ETag as-is
- Sealed `ModuleEtagResult` (Absent vs Present) replaces nullable String
- `Map<String, PendingDelete>` keyed by blobKey (not Set)
- Migration semantics: `emptySet()` = "try all configured connectors" (for forward compat)
- Age-gated startup temp cleanup (>1 hour old) to avoid racing
- Unresolvable blob refs abort commit (no logicalKey fallback that would create invalid server IDs)
- Backfill connectorRefs for existing SharedFiles via BlobMaintenance
- Single source of truth for capability resolution (BlobStoreHub)

## Client-Server Inconsistencies

### Critical: Must fix in client

**1. Plaintext size/checksum sent for ciphertext upload (SHOWSTOPPER)**

`FileShareService.kt:80` computes `BlobMetadata` (size, SHA-256) from the **plaintext** staged file. This metadata is passed to `BlobManager.put()`, which passes it to `OctiServerBlobStore.put()`. The store sends `metadata.size` to `createBlobSession(sizeBytes=...)` and `metadata.checksum` to `finalizeBlobSession(hashHex=...)`. But the actual uploaded bytes are **encrypted** via `StreamingPayloadCipher` — ciphertext is larger than plaintext (IV + segment tags + padding) and has a completely different SHA-256.

The server validates: `offsetBytes == expectedSizeBytes` at finalize (mismatch → 409), and computes `sha256(payload.part) != effectiveHex` (mismatch → 422). Both will always fail.

**This means no encrypted blob upload can succeed on the current server.**

GDrive is unaffected (no encryption, plaintext metadata matches plaintext upload).

**Fix:** The OctiServer upload path must:
1. Pre-encrypt the plaintext into a staging buffer/file
2. Compute ciphertext size and SHA-256 from the encrypted output
3. Create the session with ciphertext size/hash
4. Upload the ciphertext in ≤1 MB chunks
5. Finalize with the ciphertext hash

This is a fundamental redesign of `OctiServerBlobStore.put()`. The `BlobMetadata` passed to `BlobStore.put()` should remain plaintext metadata (for `FileShareInfo`), and each store computes wire-format metadata internally.

**2. `If-Match: *` is not HTTP wildcard — first module creation broken**

The Retrofit interface at `OctiServerApi.kt:230` hardcodes `@Header("If-Match") etag: String` — the client **cannot send `If-None-Match: *`** for first-ever module creation. The fallback `etag ?: "*"` sends `If-Match: *` which the server treats as a literal ETag (never matches) → 412.

**Fix:** Change `commitModule` to accept nullable `If-Match` and nullable `If-None-Match`. Set the correct header based on whether the module exists.

**3. `commitModule` response shape mismatch**

The server now returns `{"etag": "<hex>"}` as JSON (at `ModuleRoute.kt:250`). The client expects `ModuleCommitResponse` with `etag + modifiedAt + documentSizeBytes + blobRefs` (`OctiServerApi.kt:217`). The extra fields don't exist → deserialization fails.

**Fix:** Simplify `ModuleCommitResponse` to match server: `data class ModuleCommitResponse(val etag: String)`. Or use `Response<Unit>` and read ETag from header.

**4. PATCH body limit is 1 MB — client sends entire blob in one call**

The server enforces `maxBlobPatchBytes = 1 MB` per PATCH (`BlobRoute.kt:89`). The client does a single monolithic `appendBlobSession`. Any blob > 1 MB → 413.

**Fix:** Chunk uploads in ≤1 MB pieces. Track `Upload-Offset` from each response.

**5. `readModuleEtag()` collapses all errors into "no ETag"**

`OctiServerEndpoint.kt:379` never checks `response.isSuccessful`. A 204 (module absent) returns null ETag correctly, but so does a 500 or auth failure. Then `OctiServerConnector.kt:389` treats null as "module absent" and uses `If-None-Match: *`, which will 412 if the module actually exists.

**Fix:** Check `response.isSuccessful`. Only treat 204 as "module absent" (null ETag). Throw on 4xx/5xx.

**6. POST module blocked after PUT commit (one-way upgrade)**

Once a module has blobRefs, legacy POST returns 409. Intentional — document in capability design.

**7. Client default limits don't match server**

| Limit | Server | Client fallback |
|---|---|---|
| accountQuotaBytes | 50 MB | 25 MB |
| maxBlobBytes | 10 MB | 5 MB |

**Fix:** Update defaults to match server.

### Informational: Verified correct after server hardening

**7. Server session GC now active** — `UploadSessionRepo.startGC()` runs a reaper loop. Idle TTL (1h) and absolute TTL (24h) are both enforced. Synchronous expiry check on every operation.

**8. Quota rebuilt on startup** — `StartupRecoveryService.recover()` scans all accounts and calls `rebuildUsage()`.

**9. Orphaned blob files now deleted** — `ModuleLifecycleService.commitModule()` physically deletes orphaned blob directories post-commit.

**10. Session scope validation** — All session ops check `(accountId, moduleId)`. Commit path additionally checks `deviceId`.

**11. `storageApiVersion` is 1** — Capability detection strategy confirmed.

**12. Finalize is idempotent** — Client can retry finalize for crash recovery.

**13. ETag format** — Server sends quoted (`"<hex>"`), trims quotes on receive. Client must handle accordingly.

**14. Server checksum required** — Hash must be provided at creation OR finalize. Missing both → `400 MissingChecksum`.

---

## Revised Implementation Plan

### Phase 1: BlobMaintenance Characterization Tests

Write tests first to lock current behavior before further refactoring.

**File:** `modules-files/src/test/java/eu/darken/octi/modules/files/core/BlobMaintenanceTest.kt`

Tests:
- `retryMirrorUploads` — downloads from available, uploads to missing, patches availableOn
- `retryMirrorUploads` — skips pendingDeletes files, skips expired files
- `retryMirrorUploads` — skips on checksum mismatch after download
- `retryPendingDeletes` — removes orphaned keys, retries with configured connectors
- `retryPendingDeletes` — removes from set when all connectors succeed
- `pruneExpired` — deletes blobs and removes file entry
- `pruneExpired` — patches availableOn on partial delete
- `pruneExpired` — forgets stale entries after STALE_FORGET_DELAY

---

### Phase 2: Blob Identity Model

Fix the root cause: cross-device blob ID resolution.

**2a. Add `connectorRefs` to SharedFile**

```kotlin
// FileShareInfo.kt
data class SharedFile(
    ...
    @SerialName("availableOn") val availableOn: Set<String> = emptySet(),
    @SerialName("connectorRefs") val connectorRefs: Map<String, String> = emptyMap(),
)
```

Backward-compatible via default empty map. Old clients ignore unknown fields.

**2b. BlobStore.put returns remote ref**

```kotlin
// BlobStore.kt — change return type
suspend fun put(...): String  // returns backend-specific remote ref

// GDriveBlobStore — returns key.id (identity, no separate remote ID)
// OctiServerBlobStore — returns session.blobId
```

**2c. Thread refs through PutResult**

```kotlin
// BlobManager.kt
data class PutResult(
    val successful: Set<ConnectorId>,
    val perConnectorErrors: Map<ConnectorId, Throwable>,
    val remoteRefs: Map<ConnectorId, String> = emptyMap(),
)
```

**2d. FileShareService populates connectorRefs after upload**

In `shareFile()`, merge `putResult.remoteRefs` into `SharedFile.connectorRefs`.

**2e. Replace SyncWrite.blobRefs with BlobAttachment**

```kotlin
// SyncWrite.kt
data class BlobAttachment(
    val logicalKey: String,
    val connectorRefs: Map<String, String> = emptyMap(),
)

interface Module {
    val moduleId: ModuleId
    val payload: ByteString
    val blobs: List<BlobAttachment>? get() = null  // replaces blobRefs
}
```

**2f. OctiServerConnector resolves from BlobAttachment**

In `writeServer()`: `blob.connectorRefs[identifier.idString] ?: blob.logicalKey`

No more `blobStore?.resolveServerBlobIds()`. In-memory keyMapping in OctiServerBlobStore becomes a write-path cache only.

**2g. Serialization backward-compat tests**

- SharedFile with connectorRefs round-trips correctly
- SharedFile without connectorRefs deserializes with empty map
- BlobAttachment serialization

---

### Phase 3: OctiServer API Fixes + Capability + Legacy Compatibility

**3a. Fix commitModule API — If-Match/If-None-Match + empty response**

`OctiServerApi.kt`:
```kotlin
@retrofit2.http.PUT("module/{moduleId}")
suspend fun commitModule(
    @Path("moduleId") moduleId: String,
    @Header("X-Device-ID") callerDeviceId: String,
    @Query("device-id") targetDeviceId: String,
    @Header("If-Match") ifMatch: String?,       // null = don't send
    @Header("If-None-Match") ifNoneMatch: String?, // null = don't send
    @Body request: ModuleCommitRequest,
): Response<Unit>  // server returns empty body with ETag header
```

`OctiServerEndpoint.commitModule()`:
```kotlin
suspend fun commitModule(
    ...,
    etag: String?,  // null = module doesn't exist yet
    ...
) {
    val response = api.commitModule(
        ifMatch = etag?.let { "\"$it\"" },
        ifNoneMatch = if (etag == null) "*" else null,
        ...
    )
    // ETag from response.headers()["ETag"]?.trim('"')
}
```

**3b. Rewrite OctiServerBlobStore.put() — pre-encrypt + chunked upload**

The entire upload flow must be redesigned as one coherent change:

```
OctiServerBlobStore.put(source, metadata):
  // 1. Pre-encrypt to staging file
  val cipherFile = File(cacheDir, "blob-enc/${UUID}.tmp")
  cipher.encrypt(source, FileSystem.SYSTEM.sink(cipherFile.toPath()), aad)

  // 2. Compute ciphertext size + SHA-256
  val cipherSize = cipherFile.length()
  val cipherHash = sha256Hex(cipherFile)

  // 3. Create session with ciphertext metadata
  val session = endpoint.createBlobSession(sizeBytes = cipherSize, checksum = cipherHash)

  // 4. Upload in ≤1 MB chunks, tracking offset
  var offset = 0L
  cipherFile.inputStream().use { input ->
      while (offset < cipherSize) {
          val chunkSize = min(MAX_CHUNK_BYTES, cipherSize - offset)
          val chunk = input.readNBytes(chunkSize.toInt())
          val newOffset = endpoint.appendBlobSession(sessionId, offset, chunk)
          offset = newOffset
      }
  }

  // 5. Finalize with ciphertext hash
  endpoint.finalizeBlobSession(sessionId, checksum = cipherHash)

  // 6. Cleanup staging, record mapping
  cipherFile.delete()
  keyMapping[key.id] = session.blobId
```

Key points:
- `BlobMetadata` passed to `put()` remains **plaintext** metadata (stored in `FileShareInfo`)
- Each store computes wire-format metadata internally
- `appendBlobSession` must return the new `Upload-Offset` from the 204 response header
- `MAX_CHUNK_BYTES = 1L * 1024 * 1024` (1 MB, matching server's `maxBlobPatchBytes`)
- On failure at any step: abort session + delete cipher staging file
- `OctiServerApi.appendBlobSession` return type changes from `Unit` to `Response<Unit>` to read the `Upload-Offset` header

**3c. OctiServerCapabilities**

```kotlin
enum class BlobSupport { SUPPORTED, LEGACY, UNKNOWN }

data class OctiServerCapabilities(
    val blobSupport: BlobSupport = BlobSupport.UNKNOWN,
    val storageApiVersion: Int = 0,
    val maxBlobPatchBytes: Long = 1L * 1024 * 1024,
)
```

**3d. Resolve capabilities via account/storage**

```kotlin
suspend fun resolveCapabilities(): OctiServerCapabilities {
    return try {
        val storage = api.getAccountStorage(...)
        OctiServerCapabilities(
            blobSupport = if (storage.storageApiVersion >= 1) SUPPORTED else LEGACY,
            storageApiVersion = storage.storageApiVersion,
        )
    } catch (e: HttpException) {
        when (e.code()) {
            404, 405 -> OctiServerCapabilities(blobSupport = LEGACY)
            else -> OctiServerCapabilities(blobSupport = UNKNOWN) // transient
        }
    }
}
```

**3e. Gate blob operations on capability**

- `OctiServerBlobStoreHub`: only create BlobStore when `blobSupport == SUPPORTED`
- `OctiServerConnector.writeServer()`:
  - `SUPPORTED` + `blobs != null` → PUT commitModule
  - `LEGACY` + `blobs != null` → POST writeModule (strip blobs)
  - `UNKNOWN` + `blobs != null` → try PUT, catch 404/405 → memoize LEGACY, retry POST

**3f. Fix ETag handling in writeServer**

```kotlin
val etag = try {
    endpoint.readModuleEtag(data.deviceId, module.moduleId)
} catch (e: OctiServerHttpException) {
    if (e.httpCode == 404) null // module doesn't exist yet
    else throw e  // network error → abort, don't guess
}
// etag == null → If-None-Match: * (create)
// etag != null → If-Match: "<etag>" (update)
```

**3g. Update client defaults**

`DEFAULT_MAX_BLOB_BYTES = 10MB`, `DEFAULT_QUOTA_BYTES = 50MB` (match server).

---

### Phase 4: Upload Protocol Hardening

Abort/offset rejection are integrated into the Phase 3b upload rewrite. This phase covers remaining commit-path fixes.

**4a. Handle 412 Precondition Failed on commitModule**

In `OctiServerConnector.writeServer()`:
```kotlin
try {
    endpoint.commitModule(...)
} catch (e: OctiServerHttpException) {
    if (e.httpCode == 412) {
        log(TAG, WARN) { "writeServer(): 412 conflict on ${module.moduleId}, refreshing ETag" }
        val freshEtag = endpoint.readModuleEtag(data.deviceId, module.moduleId)
        // freshEtag == null means module deleted → use If-None-Match: *
        endpoint.commitModule(..., etag = freshEtag)
    } else throw e
}
```

**4b. Fix readModuleEtag to distinguish 404 from real failures**

In `OctiServerEndpoint.readModuleEtag()`:
```kotlin
val response = api.readModule(...)
return when {
    response.code() == 204 -> null  // module doesn't exist
    response.isSuccessful -> response.headers()["ETag"]?.trim('"')
    else -> throw OctiServerHttpException(HttpException(response))
}
```
Only 204 returns null. 500/auth/network errors throw → commit is aborted.

---

### Phase 5: Cleanup Improvements

**5a. PendingDelete tombstones**

Replace `Set<String>` with structured data:
```kotlin
@Serializable
data class PendingDelete(
    val blobKey: String,
    val remainingConnectors: Set<String>,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)
```

Update `FileShareSettings.pendingDeletes` type. Update BlobMaintenance and FileShareService.

**5b. Startup temp cleanup**

In `BlobMaintenance.runOnce()` at the start:
```kotlin
listOf("blob-maintenance", "blob-staging", "blob-download").forEach { dir ->
    File(context.cacheDir, dir).listFiles()?.forEach { it.delete() }
}
```

**5c. Update client default limits**

Match server defaults: `DEFAULT_MAX_BLOB_BYTES = 10MB`, `DEFAULT_QUOTA_BYTES = 50MB`.

---

### Phase 6: Remaining Tests

Written alongside each phase:

| Phase | Test file | Key scenarios |
|---|---|---|
| 2 | FileShareInfoSerializationTest | connectorRefs round-trip, backward compat |
| 3 | OctiServerConnectorTest | capability branching, ETag handling, 412 retry |
| 4 | OctiServerBlobStoreTest | session protocol, abort on failure, non-zero offset rejection |
| 4 | FileShareServiceTest | shareFile happy path, null inputStream, saveFile + checksum |
| 5 | BlobManagerTest | get fallback, delete partial, put remoteRefs |
| 6 | FileShareListVMTest | action handlers, quota display |
| 6 | StreamingPayloadCipherTest | wrong AAD, empty payload, multi-segment |

---

## Server-Side Changes Required

These should be deployed before or alongside the client changes:

1. **Session GC** — Add background reaper in `UploadSessionRepo.init {}` (10-min interval, delete expired sessions, release reserved quota)
2. **Quota rebuild on startup** — Call `rebuildUsage()` for all accounts during server init
3. **Orphaned blob file GC** — Delete physical blob files when dropped from `blobRefs` during commit
4. **If-Match: * wildcard support** (optional) — Add `ifMatch == "*" -> null // unconditional match` to the precondition check. Or keep the current strict behavior and fix the client instead (recommended).

---

## Implementation Order

```
Server hardening: DONE (8 fixes, 156 tests)

Client (dependency-ordered):
  Phase 1 (BlobMaintenance tests — characterization)
    → Phase 2 (blob identity model — connectorRefs, BlobAttachment, PutResult.remoteRefs)
      → Phase 3 (API fixes + capability + legacy compat — chunked uploads, If-Match/If-None-Match, commitModule response, defaults)
        → Phase 4 (upload protocol fixes — abort sessions, reject offset>0, handle 412)
          → Phase 5 (cleanup improvements — PendingDelete tombstones, startup temp cleanup, GDrive quota)
            → Phase 6 (remaining tests)
```

## Verification

- `./gradlew testDebugUnitTest` after each phase
- `./gradlew assembleDebug` compiles cleanly
- Manual test: share file between two devices via OctiServer
- Manual test: share file via GDrive
- Manual test: restart app, verify file download still works (connectorRefs resolve)
- Manual test: connect to legacy server (pre-blob API), verify graceful degradation
- Manual test: upload blob > 1 MB (verify chunked upload works)
- Manual test: first-ever file share on fresh module (verify If-None-Match: * creates module)
- Manual test: concurrent file share from two devices (verify ETag conflict → retry works)
