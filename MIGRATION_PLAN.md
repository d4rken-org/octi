# Migrate Java Time Types to Kotlin Multiplatform Equivalents

## Context

Octi is preparing for a desktop companion app via Kotlin Multiplatform. As a prerequisite, all `java.time` types must be replaced with KMP-compatible equivalents. This migration touches 77+ source files across all modules.

**Type mapping:**
- `java.time.Instant` -> `kotlinx.datetime.Instant`
- `java.time.Duration` -> `kotlin.time.Duration` (stdlib, no extra dep)
- `java.time.OffsetDateTime` -> `kotlinx.datetime.Instant` (only used in GithubApi)
- `java.time.ZonedDateTime` -> remove (dead code in TimeExtensions; utility for HTTP header parsing in server endpoints)
- `System.currentTimeMillis()` -> `Clock.System.now()` / `TimeSource.Monotonic` where appropriate

**Backward compatibility:** Both serialization layers (kotlinx.serialization + Moshi) use ISO 8601 string format. `kotlinx.datetime.Instant.toString()` produces the same format as `java.time.Instant.toString()`. For Duration, `kotlin.time.Duration.toIsoString()` produces the same ISO 8601 format (`PT1H30M`) as `java.time.Duration.toString()`. Wire format is fully preserved.

---

## Step 1: Add kotlinx-datetime dependency

**Files:**
- `buildSrc/src/main/java/Versions.kt` — add `Datetime` version
- `buildSrc/src/main/java/Dependencies.kt` — add `addDatetime()` function
- Module `build.gradle.kts` files that use Instant — add `addDatetime()` call

---

## Step 2: Update serialization adapters

Both kotlinx.serialization serializers AND Moshi adapters need updating (verify each exists before modifying).

### kotlinx.serialization serializers (`app-common/.../serializer/`)

**`InstantSerializer.kt`** — change `KSerializer<java.time.Instant>` to `KSerializer<kotlinx.datetime.Instant>`
- `value.toString()` / `Instant.parse()` — same API, just different import

**`DurationSerializer.kt`** — change `KSerializer<java.time.Duration>` to `KSerializer<kotlin.time.Duration>`
- Serialize: `value.toIsoString()` (NOT `toString()` which uses `1h 30m` format)
- Deserialize: `Duration.parseIsoString(decoder.decodeString())`

**`OffsetDateTimeSerializer.kt`** — delete. Only consumer is `GithubApi.kt` which will switch to `InstantSerializer`
- `kotlinx.datetime.Instant.parse()` handles ISO 8601 with offsets (e.g. `2024-06-15T12:00:00+00:00`)

### Moshi adapters (`app-common/.../adapter/`) — if they exist

**`InstantAdapter.kt`** — change to `kotlinx.datetime.Instant`

**`DurationAdapter.kt`** — change to `kotlin.time.Duration` with `toIsoString()`/`parseIsoString()`

**`OffsetDateTimeAdapter.kt`** — delete

### SerializationModule.kt

- Update `contextual(Instant::class, ...)` to use `kotlinx.datetime.Instant`
- Update `contextual(Duration::class, ...)` to use `kotlin.time.Duration`
- Remove `contextual(OffsetDateTime::class, ...)` registration
- Update Moshi builder if present (remove deleted adapters)

---

## Step 3: Migrate core data types and interfaces

### Complete API mapping table:

| Java | Kotlin |
|------|--------|
| `Instant.now()` | `Clock.System.now()` |
| `Instant.EPOCH` | `Instant.fromEpochMilliseconds(0)` |
| `Instant.ofEpochMilli(ms)` | `Instant.fromEpochMilliseconds(ms)` |
| `Instant.parse(s)` | `Instant.parse(s)` (same) |
| `instant.toEpochMilli()` | `instant.toEpochMilliseconds()` |
| `instant.epochSecond` | `instant.epochSeconds` |
| `instant.isAfter(other)` | `instant > other` |
| `instant.isBefore(other)` | `instant < other` |
| `instant.minusSeconds(n)` | `instant - n.seconds` |
| `instant.minusMillis(n)` | `instant - n.milliseconds` |
| `instant.plusMillis(n)` | `instant + n.milliseconds` |
| `instant.plus(duration)` | `instant + duration` |
| `instant.minus(duration)` | `instant - duration` |
| `Duration.ofMinutes(n)` | `n.minutes` |
| `Duration.ofSeconds(n)` | `n.seconds` |
| `Duration.ofMillis(n)` | `n.milliseconds` |
| `Duration.ofHours(n)` | `n.hours` |
| `Duration.ofDays(n)` | `n.days` |
| `Duration.between(a, b)` | `b - a` (Instant subtraction) |
| `duration.toMillis()` | `duration.inWholeMilliseconds` |
| `duration.toMinutes()` | `duration.inWholeMinutes` |
| `duration.toDays()` | `duration.inWholeDays` |
| `duration.seconds` (property) | `duration.inWholeSeconds` |
| `duration.abs()` | `duration.absoluteValue` |
| `duration.isNegative` | `duration.isNegative()` |
| `duration.coerceIn(a, b)` | `duration.coerceIn(a, b)` (same) |

### sync-core module (foundation)

- `SyncConnectorState.kt` — `ClockOffset(offset: Duration, ...)`, `Quota(updatedAt: Instant)`, `lastActionAt: Instant?`
- `SyncRead.kt` — `modifiedAt: Instant`
- `SyncEvent.kt` — `modifiedAt: Instant`
- `DeviceMetadata.kt` — `lastSeen: Instant?`, `addedAt: Instant?`
- `CommonIssue.kt` — `lastSeen: Instant`
- `CachedSyncRead.kt` — `@UseSerializers(InstantSerializer::class)`, `modifiedAt: Instant`
- `SyncSettings.kt` — `clockSkewThreshold: Duration` DataStore value
- `StalenessUtil.kt` — `Duration.between()` -> Instant subtraction, `Instant.now()` -> `Clock.System.now()`
- `ClockAnalyzer.kt` — heavy Duration/Instant usage

### module-core

- `ModuleData.kt` — `modifiedAt: Instant`
- `BaseModuleRepo.kt` — `Instant.now()`
- `BaseModuleCache.kt` — `Instant` usage

### Feature modules

- `modules-power/` — `PowerInfo.kt`, `PowerAlertRule.kt`, `PowerSettings.kt`, `PowerInfoSource.kt` (has `plusMillis`), `PowerAlertManager.kt`, `PowerAlert.kt`, `PowerEstimationFormatter.kt`
- `modules-apps/` — `AppsInfo.kt`, `AppsInfoSource.kt` (`Instant.ofEpochMilli()`)
- `modules-meta/` — `MetaInfo.kt`, `MetaInfoSource.kt` (has `minusMillis`)

### Sync backends (handle whichever modules exist on disk)

- `syncs-gdrive/` — `GDriveAppDataConnector.kt`, `GDriveModuleData.kt`
- `syncs-kserver/` — `KServerEndpoint.kt`, `KServerConnector.kt`, `KServerModuleData.kt`
- `syncs-octiserver/` — `OctiServer.kt`, `OctiServerApi.kt`, `OctiServerConnector.kt`, `OctiServerEndpoint.kt`, `OctiServerModuleData.kt`, `OctiServerWebSocket.kt`

---

## Step 4: Migrate app module

### Core
- `CurriculumVitae.kt` — `Instant` DataStore values, `Instant.ofEpochMilli()`
- `ReleaseSettings.kt` — `Instant` DataStore value
- `ReleaseManager.kt` — `Instant.now()`
- `UpgradeRepo.kt` — `Instant` field

### Build variants
- `FossUpgrade.kt` — `@UseSerializers(InstantSerializer::class)`, `Instant` field
- `UpgradeRepoFoss.kt` — `Instant.now()`
- `FossUpdateChecker.kt` — `Duration.between()`, `Duration.ofHours()`
- `FossUpdateSettings.kt` — `Instant` DataStore value (has `Instant.EPOCH`)
- `UpgradeRepoGplay.kt` — `Instant.now()`
- `GithubApi.kt` — change `publishedAt: OffsetDateTime` to `publishedAt: Instant`, change `@UseSerializers`

### Sync orchestration
- `ForegroundSyncControl.kt` — Duration constants, `Instant.now()`, `Duration.between()`
- `SyncOrchestrator.kt` — `Instant` usage
- `SyncWorkerControl.kt` — `.minutes.toJavaDuration()` at WorkManager API boundary, `Instant.ofEpochMilli()`

### UI
- `DashboardScreen.kt` — `Instant.now()`, `clampToNow()`, fully-qualified `java.time.Duration` usage
- `DashboardVM.kt` — `Instant.now()`, `Duration.between()`
- `ClockDiscrepancySheet.kt` — Duration construction/display
- `SyncDevicesScreen.kt` — `Instant.now()` in previews
- `SyncDevicesVM.kt` — `Instant` usage
- `DeviceActionsSheet.kt` — `Instant` usage, timezone formatting
- `SyncListScreen.kt` — `Instant.now().minusSeconds()`
- `OctiServerActionsSheet.kt` — `Instant` usage
- `AppsListScreen.kt` — `Instant.now()` in previews
- `AppsModuleTile.kt` — `Instant.ofEpochMilli()` in previews
- `RecorderActivityVM.kt` — `Duration.between()`, `Instant` usage
- `RecorderScreen.kt` — `Duration` construction/display
- Widget files — `Instant` in battery widget

### Debug
- `FileLogger.kt` — `Instant.ofEpochMilli(System.currentTimeMillis())` -> `Clock.System.now()`

---

## Step 5: Migrate TimeExtensions.kt

- Remove `OffsetDateTime.toSystemTimezone()` — no callers found
- Remove `Instant.toSystemTimezone()` — no callers found
- Update `Instant.clampToNow()` — change `Instant.now()` to `Clock.System.now()`, `isAfter()` to `>`

If either `toSystemTimezone()` function does have callers that grep missed, change return type to `LocalDateTime` using `instant.toLocalDateTime(TimeZone.currentSystemDefault())`.

---

## Step 6: Handle ZonedDateTime in server endpoints

Both `KServerEndpoint.kt` and `OctiServerEndpoint.kt` (whichever exist) parse HTTP headers:
```kotlin
ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
```

Replace with an extension function **inside the sync module** (not `TimeExtensions.kt`) to keep common utils platform-agnostic for future KMP split:

```kotlin
// In the sync module's own utility file
fun String.parseRfc1123ToInstant(): kotlinx.datetime.Instant? = runCatching {
    java.time.ZonedDateTime.parse(this, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        .toInstant()
        .let { kotlinx.datetime.Instant.fromEpochSeconds(it.epochSecond, it.nano) }
}.getOrNull()
```

---

## Step 7: Migrate System.currentTimeMillis()

### Performance measurement (-> TimeSource.Monotonic)
Replace `val start = System.currentTimeMillis()` / `System.currentTimeMillis() - start` with `measureTime {}` or `TimeSource.Monotonic.markNow()`:

- `SyncManager.kt`, `GDriveAppDataConnector.kt`, `KServerConnector.kt` / `OctiServerConnector.kt`, `SyncWorker.kt`, `SyncExecutor.kt`
- `UpgradeViewModel.kt` (FOSS) — elapsed time for sponsor page visit (monotonic is more correct here)

### Timestamp creation (-> Clock.System.now())
- `FileLogger.kt`, `JUnitLogger.kt`, `RecorderModule.kt`, `BatteryWidgetProvider.kt`

### Android API interop (keep as wall-clock millis)
- `GDriveEnvironment.kt` — `DateTime(Clock.System.now().toEpochMilliseconds())`
- `UpgradeRepoGplay.kt` — billing cache comparison
- `ContactSupportScreen.kt`, `DebugSessionsSheet.kt`, `BatteryWidgetContent.kt` — `DateUtils.getRelativeTimeSpanString()` requires wall-clock millis, keep `System.currentTimeMillis()` or use `Clock.System.now().toEpochMilliseconds()`

---

## Step 8: Update tests

Use `rg "java\.time\." --type kt -l` to generate definitive scope, not just the manual list. Known test files:

- All `*SerializationTest.kt` files — update `Instant.parse()` / `Instant.now()` / `Duration` references
- `ClockAnalyzerTest.kt` — update Duration/Instant references (has `epochSecond` usage)
- `DeviceInfoBuilderTest.kt` — update Instant/Duration references
- `DeviceItemTest.kt`, `SyncWorkerControlWorkerStateTest.kt`, `SyncOrchestratorTest.kt`
- `SyncEventsSharingTest.kt`, `SyncManagerSyncEventsTest.kt`, `BaseModuleSyncTest.kt`
- `OctiServer*Test.kt` / `KServer*Test.kt`, `GDriveAppDataConnectorMergeTest.kt`
- `FossUpgradeSerializationTest.kt`, `GithubApiSerializationTest.kt` (testFoss)
- `OctiServerWebSocketEventTest.kt` — has `epochSecond` property access

### Add backward-compat edge-case tests:
- Test `Instant.parse()` with non-Z offset timestamps (e.g. `2024-06-15T12:00:00+02:00`)
- Test `Duration.parseIsoString()` with edge cases (negative durations, fractional seconds)
- Verify existing DataStore payloads deserialize correctly after migration

---

## Step 9: Clean up

- Delete `OffsetDateTimeSerializer.kt`
- Delete `OffsetDateTimeAdapter.kt` (if it exists)
- Remove dead imports from all files
- Remove `OffsetDateTime` registration from `SerializationModule.kt`

---

## What could go wrong

1. **Duration wire format break** — `kotlin.time.Duration.toString()` uses `1h 30m` NOT ISO 8601. Must use `toIsoString()`/`parseIsoString()` in serializers. This is the #1 risk.
2. **WorkManager API boundary** — `PeriodicWorkRequestBuilder` requires `java.time.Duration`. Use `.toJavaDuration()`.
3. **DataStore backward compat** — Existing stored values must deserialize correctly. Wire format is preserved, but add edge-case tests to confirm.
4. **RFC 1123 parsing** — `kotlinx.datetime` has no RFC 1123 parser. Keep `java.time` bridge in sync module.
5. **kotlinx-datetime version compatibility** — Must match Kotlin 2.3.x.
6. **Missing API replacements** — `plusMillis`, `minusMillis`, `EPOCH`, `epochSecond` are easy to miss. Use the mapping table.

---

## Verification

1. `./gradlew assembleFossDebug assembleGplayDebug` — both flavor variants compile
2. `./gradlew testFossDebugUnitTest testGplayDebugUnitTest` — all tests pass including FOSS-specific
3. `rg "import java\.time\.Instant" --type kt` — should be zero (except RFC 1123 bridge in sync module)
4. `rg "import java\.time\.Duration" --type kt` — should be zero
5. `rg "System\.currentTimeMillis" --type kt` — should only remain where strictly necessary
