# Plan: Configurable Sub-15-Minute Sync Intervals via Foreground Service

## Context

WorkManager enforces a 15-minute minimum for periodic work on Android. Users who want more frequent sync (1-14 min) need a foreground service to bypass this OS limitation. The feature is Pro-only — free users keep the 15-min minimum. When interval >= 15 min, the existing WorkManager approach stays unchanged.

## Design

**`SyncWorkerControl` becomes the single orchestrator** that picks between two sync mechanisms:
- **Interval >= 15 min (or not Pro)**: Existing WorkManager periodic work (unchanged)
- **Interval < 15 min + Pro**: Cancel WorkManager, start foreground service with coroutine delay loop

The foreground service uses `dataSync` foreground service type and shows a minimal low-priority sticky notification.

## New Files

### 1. `app/src/main/java/eu/darken/octi/sync/core/SyncExecutor.kt`
- `@Singleton` class with `@Inject` constructor
- Injects: `SyncManager`, `ModuleManager`, `BatteryWidgetManager`, `PowerAlertManager`
- Single `suspend fun execute()` containing the shared sync logic (currently duplicated in `SyncWorker.doDoWork()`): refresh modules → delay 3s → sync → refresh widgets → check alerts
- Holds a `Mutex` to prevent overlapping sync runs (important at sub-15 intervals where manual sync + service loop could overlap)
- Used by both `SyncWorker` and `SyncForegroundService`

### 2. `app/src/main/java/eu/darken/octi/sync/core/service/SyncServiceNotifications.kt`
- `@Singleton` class with `@Inject` constructor
- Creates notification channel `{appId}.notification.channel.sync.service` with `IMPORTANCE_LOW` in `init{}`
- `buildNotification()` returns a low-priority ongoing notification with "Sync active" text
- Tap opens the app via `PendingIntent` to launch intent
- Pattern: follows `PowerAlertNotifications` (`modules-power/.../PowerAlertNotifications.kt`)

### 3. `app/src/main/java/eu/darken/octi/sync/core/service/SyncForegroundService.kt`
- Extends `Service2`, annotated `@AndroidEntryPoint`
- Injects: `SyncExecutor`, `SyncSettings`, `SyncServiceNotifications`, `ConnectivityManager`
- `onCreate()`: calls `startForeground(NOTIFICATION_ID, notification)` immediately
- `onStartCommand()`: reads interval from intent extra; **if intent is null (sticky restart), reads interval from `SyncSettings` DataStore** as fallback; validates state (enabled, interval < 15) — stops self if invalid
- Cancels existing `syncJob`, launches new coroutine loop: `syncExecutor.execute()` → `delay(interval)` → repeat
- Before each sync, checks network constraint (`onMobile` setting via `ConnectivityManager`) — skips iteration if on metered when not allowed
- `onTimeout(id, type)` (Android 15+): gracefully stops, schedules one-shot WorkManager restart job to re-start the service
- `onDestroy()`: cancels scope
- Uses `START_STICKY` for OEM kill resilience

### 4. `app/src/main/java/eu/darken/octi/sync/core/service/SyncServiceControl.kt`
- `@Singleton` thin wrapper with `start(intervalMinutes: Int)` and `stop()`
- `start()` uses `ContextCompat.startForegroundService()` with interval as intent extra; wraps in try/catch for `SecurityException` (background start restriction) — returns success/failure boolean
- `stop()` calls `context.stopService()`

### 5. `app/src/main/java/eu/darken/octi/sync/core/service/SyncServiceBootReceiver.kt`
- `@AndroidEntryPoint` BroadcastReceiver
- On `BOOT_COMPLETED`: reads settings from DataStore, checks isPro
- If `enabled && interval < 15 && isPro`: tries to start FGS via `SyncServiceControl.start()`
- **On failure (SecurityException)**: falls back to scheduling WorkManager with `coerceAtLeast(15)` — logs warning
- Uses `goAsync()` + coroutine for suspend DataStore reads

## Modified Files

### 6. `app/src/main/AndroidManifest.xml`
- **Remove** the `FOREGROUND_SERVICE` permission removal override (lines 6-8)
- **Add** permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`
- **Declare** `SyncForegroundService` with `android:foregroundServiceType="dataSync"`, `android:exported="false"`
- **Declare** `SyncServiceBootReceiver` with `BOOT_COMPLETED` intent filter, `android:exported="false"`

### 7. `app/src/main/java/eu/darken/octi/sync/core/worker/SyncWorkerControl.kt`
- Add injected deps: `SyncServiceControl`, `UpgradeRepo`
- Add `upgradeRepo.upgradeInfo` to the `combine` flow — use `kotlinx.coroutines.flow.combine` (not the custom overload which lacks a 4-arg variant, see `FlowCombineExtensions.kt`)
- Add `.distinctUntilChanged()` on the computed scheduler mode to avoid restart churn from noisy flows
- Decision logic:
  - `useForegroundService = isEnabled && interval < 15 && upgradeInfo.isPro`
  - **Transactional switching**: start new mechanism first, then stop old. If FGS start fails, fall back to WorkManager with `coerceAtLeast(15)`
  - If `useForegroundService`: call `syncServiceControl.start(interval)` → on success, cancel WorkManager; on failure, schedule WorkManager as fallback
  - If `!useForegroundService`: schedule WorkManager with `interval.coerceAtLeast(15)` → then call `syncServiceControl.stop()`

### 8. `app/src/main/java/eu/darken/octi/sync/core/worker/SyncWorker.kt`
- Replace inline `doDoWork()` logic with `syncExecutor.execute()` call
- Inject `SyncExecutor` instead of individual managers

### 9. `app/src/main/java/eu/darken/octi/sync/ui/settings/SyncSettingsVM.kt`
- Inject `UpgradeRepo`
- Add `isPro: Boolean` to `State` data class
- Add `upgradeRepo.upgradeInfo` to the `combine` flow
- `setBackgroundSyncInterval()`: if `minutes < 15 && !isPro()`, navigate to `Nav.Main.Upgrade()` and return without saving
- Battery optimization: when saving sub-15 interval successfully, check `Permission.IGNORE_BATTERY_OPTIMIZATION.isGranted()` — if not granted, emit a nav event reusing the existing dashboard permission prompt pattern (not direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)

### 10. `app/src/main/java/eu/darken/octi/sync/ui/settings/SyncSettingsScreen.kt`
- Replace raw 15-1440 slider with **preset intervals**: `1, 2, 3, 5, 10, 15, 30, 60, 120, 240, 360, 720, 1440`
- Slider maps index position to preset value
- **Migration**: snap stored values not in preset list to nearest preset (e.g., 45 → 30, 75 → 60)
- `valueLabel` shows `"Xh"` for >= 60 min, `"X min"` otherwise
- Show subtitle hint below slider when interval < 15: "Intervals under 15 min use a foreground service (Pro)"
- **Pro gate on `onValueChangeFinished`** only (not on every drag event) — prevents spamming upgrade navigation during slider drag

### 11. `app/src/main/res/values/strings.xml`
Add:
- `sync_service_notification_channel_label` — "Sync service"
- `sync_service_notification_title` — "Sync active"
- `sync_service_notification_body` — "Syncing at frequent intervals"
- `sync_setting_interval_foreground_hint` — "Intervals under 15 min use a foreground service (Pro)"

## Control Flow

**User sets interval to 5 min:**
1. Slider drag finishes → `SyncSettingsVM.setBackgroundSyncInterval(5)`
2. VM checks `isPro()` → true → saves to DataStore → checks battery optimization
3. `SyncWorkerControl` combine re-emits → `useForegroundService = true`
4. Starts FGS → on success, cancels WorkManager
5. Service starts foreground with notification → launches sync loop via `SyncExecutor`

**User changes interval from 5 to 30 min:**
1. VM saves 30 → `SyncWorkerControl` re-emits → `useForegroundService = false`
2. Schedules WorkManager with 30 min → then stops foreground service

**Pro expires while service running:**
1. `upgradeRepo.upgradeInfo` emits `isPro = false`
2. `SyncWorkerControl` re-evaluates → `useForegroundService = false`
3. Schedules WorkManager with `interval.coerceAtLeast(15)` → stops service
4. Stored interval (5) preserved in DataStore — resubscribing restores it

**Interval changes while service running:**
1. `SyncWorkerControl` calls `syncServiceControl.start(newInterval)` again
2. Service receives new `onStartCommand` with updated interval extra
3. Cancels existing `syncJob`, relaunches loop with new interval

**Device reboot:**
1. `SyncServiceBootReceiver` receives `BOOT_COMPLETED`
2. Reads DataStore → if enabled + sub-15 + pro → tries to start FGS
3. On `SecurityException`: falls back to WorkManager with 15 min
4. WorkManager auto-restores its own periodic work for the >= 15 path

**FGS timeout (Android 15+, ~24h):**
1. OS calls `onTimeout()` on the service
2. Service gracefully stops → schedules one-shot WorkManager job to restart FGS
3. WorkManager job starts the FGS again, resetting the 24h timer

**Sticky restart (OEM kill + START_STICKY):**
1. OS restarts service with null intent
2. `onStartCommand` reads interval from `SyncSettings` DataStore
3. Validates enabled + sub-15 — stops self if no longer valid

## Edge Cases

- **Aggressive OEM kills**: `START_STICKY` + boot receiver + battery optimization prompt provides best-effort resilience
- **Network constraint**: Service checks `onMobile` setting before each sync. Skips iteration (doesn't stop service) when on metered network and mobile not allowed
- **Non-Pro user with sub-15 stored value**: `SyncWorkerControl` uses `coerceAtLeast(15)` for WorkManager. Slider shows the stored value but VM blocks saving new sub-15 values
- **FGS start failure**: Transactional switching ensures WorkManager always serves as fallback
- **Overlapping syncs**: `Mutex` in `SyncExecutor` serializes runs from service loop, manual sync, and worker
- **Legacy stored intervals (45, 75, etc.)**: Snapped to nearest preset on read

## Verification

1. Build: `./gradlew assembleDebug`
2. Install and set interval to 5 min (as Pro) → verify notification appears, sync runs every ~5 min via logcat
3. Change to 30 min → verify notification disappears, WorkManager schedules
4. Kill app → verify service restarts (START_STICKY)
5. Reboot device → verify service starts after boot (or falls back to WorkManager)
6. Test non-Pro user → verify upgrade prompt appears when releasing slider below 15
7. Test network constraint → disable mobile sync, switch to mobile data, verify sync skips
8. Test slider with existing 45-min stored value → verify snaps to 30
9. Run tests: `./gradlew testDebugUnitTest`
10. Unit tests to add: null-intent restart, non-pro with stored sub-15, migration snap, service-start failure fallback, timeout/restart behavior
