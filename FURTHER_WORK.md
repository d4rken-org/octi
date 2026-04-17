# Further Work — Blob-Backed File Sharing

Findings from on-device end-to-end testing of the file sharing feature (two emulators against local dev Octi Server, build `1.0.0-beta0 dev-fcc30664`). The feature works end-to-end — this document tracks the items that remain before shipping.

## Must-fix before ship

### 1. POST_NOTIFICATIONS not requested at runtime (API 33+)

`android.permission.POST_NOTIFICATIONS` is declared in `app/src/main/AndroidManifest.xml:5` but no runtime prompt is triggered when the file-share feature first needs it. On API 33+ the system silently drops `NotificationManager.notify()` calls until the user grants the permission.

Observed behavior:
- Receiver on API 33 with permission denied.
- Sender shares a file → receiver's `IncomingFileNotifier.notify(...)` logs `D/Module:Files:IncomingNotifier: notify(device=a381021d, file=test-small.txt)` — the system drops the notification with no error visible to the app or user.
- `seenByDevice` in `IncomingFileNotifier.kt:52` still gets updated. Once the user later grants the permission, the previously-dropped file is **not** re-notified — the user never sees that a share arrived.

Options:
- Request the permission in onboarding/first-launch alongside the other permissions in `app/src/main/java/eu/darken/octi/main/ui/dashboard/PermissionTool.kt`.
- Defer the `seenByDevice` update until after the `notificationManager.notify()` call has actually posted a visible notification (would require checking `notificationManager.areNotificationsEnabled()` up front and skipping the seen-set update when disabled).

### 2. Generic "Failed to share file" for oversize files

`OctiServerBlobStore` rejects files larger than the server limit (observed max: 10,484,736 bytes = 10 MiB − 1 KiB for AEAD overhead). The 1 KiB reservation happens per `OctiServerBlobStore.kt` constraints check.

Current behavior:
- A 10 MB (10,485,760 B) file exceeds the cap.
- Logcat: `I/Sync:Blob:Manager: put(...): Skipping ConnectorId(...) — file too large (10485760 > 10484736)`.
- `BlobManager` returns `AllConnectorsFailed` → UI shows `R.string.module_files_upload_failed` "Failed to share file", indistinguishable from a network failure.

Suggested fix in `FileShareService.shareFile` / `FileShareListVM.onShareFile`:
- Map the `BlobFileTooLargeException` (from `BlobStoreException.kt`) to a distinct `ShareResult` variant.
- Add a dedicated string `R.string.module_files_upload_too_large` — e.g. "File is too large (max ~10 MiB)".
- Consider pre-checking size against `BlobStoreConstraints.maxFileBytes` before staging, so the failure is immediate instead of after the SHA-256 + stage copy.

## Worth investigating

### 3. Storage quota: dedup vs stale cache

When the same 8 MB file was shared twice from the same sender (same plaintext, different blobKeys, different ciphertext keyset nonces), the dashboard storage display stayed at `8.39 MB / 52.43 MB used` instead of ~16.78 MB.

Possible causes:
- Server deduplicates blobs by ciphertext hash. Unlikely given distinct AES-GCM-SIV encryptions should produce distinct ciphertexts.
- Server returns cached quota from `GET /v1/module/.../blob-sessions` metadata, not recomputed.
- `BlobManager.quotas()` polling interval masks an intermediate update.

Worth confirming what the server is actually storing and whether the reported quota is authoritative. If dedup is intentional, document it; otherwise, track down the stale read.

### 4. Quota doesn't drop immediately after delete

Client `OctiServerBlobStore.kt:217-220` treats delete as a no-op — server GCs the blob when no module payload references it. Observed: quota actually *grew* slightly after a delete (2.55 kB → 2.74 kB) because the module payload (including tombstone entry in `FileShareSettings.pendingDeletes`) grew while the blob remained.

Consider either:
- Issuing an explicit `DELETE /blobs/{id}` call so the quota drops promptly.
- Adding a hint in the list UI that server cleanup is asynchronous (so users don't retry-delete thinking it failed).

### 5. `BaseModuleCache.set` races with cache-dir creation on first run

On the initial session after install, logcat fills with the same error across every module (Power, Wifi, Connectivity, Clipboard, Apps, Meta, Files):

```
E/🐙:Module:<X>:Cache: Failed to cache sync data: java.io.IOException: No such file or directory
    at java.io.File.createNewFile(File.java:1022)
    at eu.darken.octi.module.core.BaseModuleCache$set$suspendImpl$$inlined$guard$1.invokeSuspend(BaseModuleCache.kt:...)
```

Each failure is caught at `BaseModuleCache.kt:84-87` and reported via `Bugs.report`, so sync keeps working (data still flows from the server on every GET). `run-as` listing confirms the module-cache subdirectories exist after the burst, and the issue did not reproduce after a HOME→re-launch.

`cacheDir` is initialized with `.mkdirs()` at `BaseModuleCache.kt:37`, and `cachedDevices()` at line 115-124 recreates it via `deleteRecursively() + mkdirs()` on listing failure. One of these paths is racing with a concurrent `set()` — line 79-81 calls `createNewFile()` without first ensuring the parent exists. Fix is either `cacheFile.parentFile?.mkdirs()` before `createNewFile`, or guaranteeing cache bootstrap finishes before sync starts writing.

Non-fatal (errors are caught, UI unaffected) but noisy in Bugs reporting.

## Already verified, no action needed

- Upload encryption (AES-256-GCM-SIV streaming, 1 MB chunks, AEAD framing).
- Resumable upload sessions (`POST /blob-sessions` → `PATCH` × N → `POST /finalize`).
- WebSocket push delivery — receiver picks up new shares without manual sync.
- End-to-end integrity — md5 match across 8 MB transfer on both devices.
- `IncomingFileNotifier` seed-then-diff semantics: first per-device emission is silent seed, subsequent additions fire exactly once per new blob key.
- Module toggle (`FileShareSettings.isEnabled`) disables the tile and uploads without breaking maintenance.
- `BlobMaintenance.runOnce()` runs 60 s after app start and every 30 min, independent of module enabled state.
- Background uploads continue on `@AppScope` after the app is backgrounded.
- Dashboard nav: own-device tile → editable list (FAB); remote-device tile → read-only list (Save buttons).
- Offline-receiver → share on sender → reconnect receiver → sync + notify fires correctly.

### Regression smoke test — non-file-sharing paths (second agent, same branch)

To guard against collateral damage from the connector-UI refactor (commit `844c88f6` "Decouple sync connector UI from app module", plus the `ConnectorUiContribution`/`ConnectorCapabilities` split), an independent smoke test walked every non-file-sharing screen on octi-1 (emulator-5556). All passed:

- **Dashboard detail sheets**: `PowerDetailSheet`, `WifiDetailSheet`, `ConnectivityDetailSheet`, `ClipboardDetailSheet` open and close. Module display order intact. `ReliabilitySheet` and `IssuesSummarySheet` render (the latter correctly surfaces the "File sharing unavailable" warning on legacy-encryption Octi Server accounts — see commit `4b5eb819`).
- **`AppsListScreen`**: list renders, sort dialog (Name / Install date / Update date) functions.
- **`SyncListScreen`** (highest-risk area): all three connectors render with correct labels and icons — GDrive (App-data scope) + two Octi Server accounts. Per-connector action sheets (previously in deleted `GDriveActionsSheet.kt` / `OctiServerActionsSheet.kt`, now owned by each module's `ConnectorUiContribution`) render every expected action: Pause / Synchronize / Synced devices / Reset / Disconnect, plus `Link new device` on Octi Server — which navigates to `OctiServerLinkHost` (QR + text-code entry points both present).
- **`SyncAddScreen`**: both Google Drive and Octi Server options visible → confirms the Dagger `Map<ConnectorType, ConnectorUiContribution>` is populated (the `f09bcfeb` "Fail fast on empty sync UI contribution registry in debug" assertion did not trip).
- **`AddGDriveScreen`** and **`AddOctiServerScreen`** (server picker + legacy-encryption toggle + create/link branches) both render.
- **`SyncDevicesScreen`** + **`DeviceActionsSheet`**: peer shows correct policy-specific copy ("Deleting this device will remove its data and revoke its access" for Octi Server → `DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE`). Delete option present but not confirmed during test.
- **Settings tree**: Index → General (theme light/dark/system round-trip) / Synchronization (device-label rename dialog) / Modules (every module listed with toggle) / Support (debug log) / Contact support / Acknowledgements — every screen opens cleanly.
- **Cross-device sync**: dev server log confirms continuous 200 OK reads for all module endpoints from both device IDs (`899f18df…` + `2ca6f2b4…`); peer data visible on the octi-1 dashboard without manual intervention.
- **Lifecycle**: HOME → relaunch restores dashboard with both device tiles, no crash.

## Out of scope / not tested on this setup

- Partial-mirror retry (`BlobMaintenance.retryMirrorUploads`) — requires a second sync connector (e.g. GDrive) configured. Unit-tested in `BlobMaintenanceTest`.
- Synthetic 48 h expiry (`pruneExpired`) — not tested to avoid emulator-wide clock manipulation. Covered by unit tests.
- Checksum mismatch path — not inducible on-device without patching code. Covered by `StreamingPayloadCipherTest` and `OctiServerBlobStoreHubTest`.
- Quota exhaustion — dev server's 50 MB limit is far below the per-file cap; testing would require a low-quota server build. `BlobQuotaExceededException` mapping covered by `OctiServerBlobStoreTest`.
- App-kill-during-upload recovery — too risky on a live sync session; `BlobMaintenance.cleanupStaleTempFiles` handles stranded staging files on the next maintenance cycle.
