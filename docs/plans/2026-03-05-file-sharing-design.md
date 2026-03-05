# File Sharing Module — Design Brainstorm

**Status**: Draft / In Progress
**Date**: 2026-03-05

## Goal

Allow users to share small files (1–5 MB) between their Octi-linked devices. Files are ephemeral — available for ~24 hours, then automatically cleaned up.

**Use cases**: sharing a PDF, a config file, a photo, a small document between your own devices.

## Core Decisions

### File Picker: Storage Access Framework (SAF)

- **Upload**: `ACTION_OPEN_DOCUMENT` — user picks a file from any document provider
- **Download**: `ACTION_CREATE_DOCUMENT` — user chooses save location and filename
- No custom file browser UI needed. SAF handles all file access.

### Sync Strategy: Increase KServer Payload Limit

Rather than adding a separate file storage endpoint, increase the existing KServer payload limit from 128 KB to ~10 MB. Files flow through the normal module sync pipeline.

**Trade-offs**:
- (+) No new server API endpoints needed
- (+) Works with existing encryption, compression, and multi-connector merging
- (+) GDrive AppData can also handle files of this size (no per-file limit, just quota)
- (-) Entire file is base64-encoded in JSON (~33% overhead before gzip)
- (-) Full file re-synced on every write cycle (no delta sync)
- (-) Memory pressure — entire payload must fit in memory as ByteString

**Mitigations**:
- Gzip compression (already applied on KServer path) will help for text-based files
- Size cap (e.g., 5 MB) keeps memory usage bounded
- Files are ephemeral, so the re-sync cost is acceptable

## Architecture (Following Existing Module Pattern)

New Gradle module: `modules-files`

### Data Model — `FileShareInfo`

```kotlin
@JsonClass(generateAdapter = true)
data class FileShareInfo(
    @Json(name = "files") val files: List<SharedFile> = emptyList(),
) {
    @JsonClass(generateAdapter = true)
    data class SharedFile(
        @Json(name = "name") val name: String,
        @Json(name = "mimeType") val mimeType: String,
        @Json(name = "size") val size: Long,
        @Json(name = "data") val data: ByteString,
        @Json(name = "sharedAt") val sharedAt: Instant,
    )
}
```

### Module Components (same pattern as Clipboard)

| Component | Role |
|-----------|------|
| `FileShareInfo` | Data model — list of shared files with metadata + content |
| `FileShareHandler` | `ModuleInfoSource` — manages the local file list, handles SAF intents |
| `FileShareSerializer` | `ModuleSerializer` — Moshi JSON ↔ ByteString |
| `FileShareSettings` | `ModuleSettings` — DataStore with `isEnabled`, max file size, expiry duration |
| `FileShareCache` | `BaseModuleCache` — file-based persistence |
| `FileShareSync` | `BaseModuleSync` — network sync via SyncManager |
| `FileShareRepo` | `BaseModuleRepo` — combines local + remote state |
| `FileShareModule` | Hilt DI — `@IntoSet` bindings for sync, repo, moduleId |

### App-Level Wiring

- `DashboardVM.ModuleItem.FileShare` — new sealed interface variant
- `DashboardScreen` — new composable for file share row + detail sheet
- `ModuleSettingsVM` — toggle for file sharing
- `GeneralModuleSettings` — reference to `FileShareSettings`
- `Nav.Main.FileShareList(deviceId)` — detail screen (if multiple files per device)

### Server Changes (KServer)

- Increase `payloadLimit` from `128 * 1024` to `10 * 1024 * 1024` (10 MB)
- Consider a per-module limit rather than global, to prevent other modules from accidentally sending huge payloads

### 24-Hour Expiry

- `SharedFile.sharedAt` timestamp on each file
- Client-side cleanup: `FileShareHandler` periodically filters out files older than 24 hours
- On each sync write, expired files are excluded
- Optional: server-side cleanup for stale module data (future enhancement)

## Open Questions

These need to be resolved before implementation:

1. **Single file vs. multiple files per device?**
   Single is simpler (mirrors clipboard). Multiple is more useful. Leaning toward multiple with a total size cap.

2. **Exact size limit?**
   1 MB? 5 MB per file? 5 MB total per device? Affects KServer limit and memory usage.

3. **GDrive handling**
   GDrive AppData doesn't have the same size constraint as KServer. Should files sync to GDrive too, or KServer-only? If both, the base64-in-JSON overhead matters more for GDrive (no gzip layer).

4. **UI for the file list**
   Bottom sheet (like clipboard) or full screen (like apps list)? If multiple files, a full screen with a list makes more sense.

5. **Progress indication**
   For multi-MB files, upload/download may take seconds. Show a progress indicator? The current sync pipeline doesn't expose progress — it's all-or-nothing ByteString reads/writes.

6. **Expiry UX**
   Show remaining time on each file? Show expiry in the file list? Let user manually delete files before expiry?

## Files to Create/Modify

### New module: `modules-files/`
- `build.gradle.kts`
- `src/main/java/eu/darken/octi/modules/files/FileShareInfo.kt`
- `src/main/java/eu/darken/octi/modules/files/FileShareHandler.kt`
- `src/main/java/eu/darken/octi/modules/files/FileShareSerializer.kt`
- `src/main/java/eu/darken/octi/modules/files/FileShareSettings.kt`
- `src/main/java/eu/darken/octi/modules/files/FileShareCache.kt`
- `src/main/java/eu/darken/octi/modules/files/FileShareSync.kt`
- `src/main/java/eu/darken/octi/modules/files/FileShareRepo.kt`
- `src/main/java/eu/darken/octi/modules/files/FileShareModule.kt`
- `src/main/res/values/strings.xml`

### Modified files
- `settings.gradle` — add `:modules-files`
- `app/build.gradle.kts` — add `implementation(project(":modules-files"))`
- `app/.../DashboardVM.kt` — add `ModuleItem.FileShare`
- `app/.../DashboardScreen.kt` — add file share composable + detail sheet/screen
- `app/.../ModuleSettingsVM.kt` — add file share toggle
- `app/.../GeneralModuleSettings.kt` — add `FileShareSettings` reference
- `app/.../Nav.kt` — add navigation destination
- `sync-server/` — increase payload limit

## Next Steps

1. Resolve open questions (single vs. multiple files, exact size limit)
2. Create detailed implementation plan
3. Implement in order: data model → serializer → handler → settings → cache → sync → repo → DI → UI → server change
