# Instant Data Sync Design

## Problem

Octi currently syncs data between devices via periodic polling (default: 60 minutes via WorkManager).
This makes time-sensitive features like clipboard sync nearly unusable and creates an unnecessarily sluggish experience for all other modules.

## Goals

- **KServer:** Sub-second delivery of module updates via WebSocket notifications
- **Google Drive:** Faster, more efficient sync via incremental change detection
- **Android:** Persistent connection via foreground service for always-on instant sync

---

## Current Architecture

```
Device A                    Server                    Device B
   |                          |                          |
   |-- POST /module/clip ---->|                          |
   |                          |   (data sits on server)  |
   |                          |                          |
   |                          |<-- GET /module/clip -----|  (60 min later)
   |                          |--- 200 + payload ------->|
```

- **Sync trigger:** `SyncWorker` runs every N minutes (default 60) via WorkManager
- **Data flow:** Module data collected locally -> serialized -> encrypted (KServer) -> uploaded via REST
- **Read:** Full directory scan on every sync (both KServer and GDrive)
- **No push mechanism exists** — all updates are pull-only

---

## Design: KServer WebSocket Notifications

### Concept

WebSocket serves as a **notification channel only**. Actual data transfer stays on the existing REST API.

When Device A writes a module, the server broadcasts a lightweight notification to all other connected devices in the same account. Those devices then fetch the updated module via `GET /module/{moduleId}`.

```
Device A                    Server                    Device B
   |                          |                          |
   |  WS connected            |            WS connected  |
   |-- POST /module/clip ---->|                          |
   |                          |-- WS: {changed: clip} -->|
   |                          |<-- GET /module/clip -----|  (immediate)
   |                          |--- 200 + payload ------->|
```

### Why notification-only (not full-duplex data)?

- Avoids reimplementing reliable delivery, ordering, backpressure over WebSocket
- Existing REST endpoints already handle encryption, auth, error cases
- Easier to debug (HTTP requests are inspectable)
- WebSocket failure degrades gracefully to existing polling

### Server Changes

#### New dependency

```kotlin
implementation("io.ktor:ktor-server-websockets:3.4.0")
```

#### WebSocket route (`/v1/ws`)

```
GET /v1/ws
Headers:
  X-Device-ID: <uuid>
  Authorization: Basic <base64(accountId:devicePassword)>
```

Authentication happens during the HTTP upgrade handshake using the existing credential verification.

#### Connection registry

In-memory map tracking active WebSocket sessions per account:

```kotlin
class ConnectionRegistry {
    // accountId -> set of (deviceId, WebSocketSession)
    private val connections: ConcurrentHashMap<UUID, MutableSet<DeviceSession>>

    fun register(accountId: UUID, deviceId: UUID, session: WebSocketSession)
    fun unregister(deviceId: UUID)
    fun getAccountPeers(accountId: UUID, excludeDevice: UUID): Set<DeviceSession>
}
```

#### Notification broadcast

When `ModuleRoute` handles a `POST /module/{moduleId}`, after writing to disk, broadcast to peers:

```kotlin
// Notification payload (JSON)
{
  "type": "module_changed",
  "deviceId": "<source-device-uuid>",
  "moduleId": "<module-id>",
  "modifiedAt": "<iso-8601-timestamp>"
}
```

Only metadata — no module payload over WebSocket.

#### Heartbeat / keepalive

- Ktor WebSocket plugin supports automatic ping/pong
- Configure: `pingPeriod = 30.seconds`, `timeout = 60.seconds`
- Server-side cleanup of stale sessions on disconnect

### Android App Changes

#### Foreground service: `SyncConnectionService`

- Started on app launch (if KServer connector is active)
- Shows persistent notification ("Octi sync active")
- Manages WebSocket lifecycle: connect, authenticate, listen, reconnect
- On `module_changed` notification: triggers `SyncManager.sync()` for the specific module

#### WebSocket client

```kotlin
// OkHttp WebSocket (already in dependency tree via Retrofit)
val client = OkHttpClient.Builder()
    .pingInterval(30, TimeUnit.SECONDS)
    .build()

val request = Request.Builder()
    .url("wss://server/v1/ws")
    .addHeader("X-Device-ID", deviceId)
    .addHeader("Authorization", "Basic $credentials")
    .build()
```

#### Reconnection strategy

- Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (cap)
- Reset backoff on successful connection
- Respect network state (don't reconnect when offline)
- On reconnect: do a full sync to catch missed notifications

#### Integration with existing sync

- WebSocket notification triggers targeted module sync (not full sync)
- WorkManager periodic sync remains as fallback (reduced interval, e.g. 15 min)
- Manual sync button continues to work as before
- If WebSocket is connected, skip the periodic sync (or extend interval significantly)

### Connector interface changes

Add optional real-time support to `SyncConnector`:

```kotlin
interface SyncConnector {
    // existing...
    fun sync(options: SyncOptions): Unit
    fun write(toWrite: SyncWrite): Unit

    // new (default no-op for backends without push)
    val supportsRealtime: Boolean get() = false
    fun connectRealtime(): Flow<RealtimeEvent> = emptyFlow()
}
```

---

## Design: Google Drive Incremental Sync

### Problem

Current GDrive sync does a full `files.list()` + download every file on every sync cycle. This is expensive and slow.

### Solution: `changes.list()` with `startPageToken`

Google Drive API provides an incremental changes endpoint that returns only files modified since a given token. This works with `appDataFolder` scope.

### How it works

1. **First sync:** Call `changes.getStartPageToken()` to get baseline, then do a full sync as today
2. **Subsequent syncs:** Call `changes.list(pageToken, spaces=appDataFolder)` to get only changed files
3. **Fetch only changed modules** instead of re-downloading everything
4. **Store `newStartPageToken`** in DataStore for next sync

### Implementation

```kotlin
// In GDriveAppDataConnector

// New DataStore value
val changeToken: DataStoreValue<String?> = dataStore.createValue("gdrive_change_token", null)

suspend fun readDriveIncremental(): GDriveData {
    val token = changeToken.value()

    if (token == null) {
        // First run: get baseline token, do full read
        val startToken = driveService.changes().getStartPageToken().execute().startPageToken
        changeToken.value(startToken)
        return readDriveFull() // existing logic
    }

    // Incremental: only fetch changes
    val changes = driveService.changes().list(token)
        .setSpaces("appDataFolder")
        .execute()

    // Process only changed files
    for (change in changes.changes) {
        // Update local cache for changed module
    }

    // Save new token
    changeToken.value(changes.newStartPageToken)
}
```

### Sync interval

With incremental changes, polling becomes much cheaper. Reduce GDrive sync interval to **5 minutes** without significant API quota impact.

### Limitations

- **No true push for GDrive appDataFolder** — `changes.watch()` requires a webhook URL and is not documented as working with `appDataFolder` scope
- Still polling-based, but dramatically more efficient
- If token becomes invalid (rare), fall back to full sync with new token

---

## Architecture Overview

```
┌─────────────────────────────────────────────────┐
│                  Android App                     │
├─────────────────────────────────────────────────┤
│                                                  │
│  SyncConnectionService (foreground)              │
│  ├── KServer WebSocket Client                    │
│  │   ├── Connects to wss://server/v1/ws          │
│  │   ├── Receives module_changed notifications   │
│  │   └── Triggers targeted SyncManager.sync()    │
│  │                                               │
│  └── GDrive Change Poller (5 min)                │
│      ├── changes.list(startPageToken)            │
│      └── Fetches only changed modules            │
│                                                  │
│  SyncWorker (WorkManager, 15 min fallback)       │
│  └── Full sync if WebSocket was disconnected     │
│                                                  │
├─────────────────────────────────────────────────┤
│  SyncManager                                     │
│  ├── KServerConnector (REST + WebSocket)         │
│  └── GDriveConnector (REST + changes.list)       │
└─────────────────────────────────────────────────┘
          │                          │
          │ REST + WebSocket         │ Google Drive API
          ▼                          ▼
┌──────────────────┐     ┌─────────────────────┐
│   KServer        │     │   Google Drive       │
│  ┌────────────┐  │     │   (appDataFolder)    │
│  │ REST API   │  │     │                      │
│  │ /v1/module │  │     │  files.list()        │
│  └────────────┘  │     │  changes.list()      │
│  ┌────────────┐  │     │  changes.getStart    │
│  │ WebSocket  │  │     │    PageToken()       │
│  │ /v1/ws     │  │     │                      │
│  └────────────┘  │     └─────────────────────┘
│  ┌────────────┐  │
│  │ Connection │  │
│  │ Registry   │  │
│  └────────────┘  │
└──────────────────┘
```

---

## Open Questions

1. **Notification granularity:** Should the server notify per-module or batch notifications when multiple modules change at once?
2. **Foreground service UX:** What should the persistent notification show? Sync status? Last sync time? Make it actionable?
3. **Battery impact:** Need to measure actual battery impact of persistent WebSocket + foreground service. May need a user toggle.
4. **Server scaling:** ConnectionRegistry is in-memory. If the server ever needs horizontal scaling, this needs a pub/sub backend (Redis, etc.). Not a concern now with single-instance deployment.
5. **TLS for WebSocket:** Server currently runs plain HTTP behind a reverse proxy. WebSocket upgrade (`wss://`) needs to work through the proxy.
6. **GDrive token invalidation:** How often do `startPageToken`s become invalid? Need error handling + fallback to full sync.

## Trade-offs

| Aspect | KServer (WebSocket) | GDrive (changes.list) |
|--------|--------------------|-----------------------|
| Latency | Sub-second | 5 minutes (polling) |
| Complexity | Medium (new server + client code) | Low (API change only) |
| Battery | Moderate (persistent connection) | Low (periodic poll) |
| Reliability | Needs reconnect logic + fallback | Simple token-based |
| Offline | Degrades to polling on reconnect | No change |

## Non-Goals (for now)

- Full-duplex data transfer over WebSocket
- FCM push notifications as a bridge
- Google Drive `changes.watch()` webhooks
- Horizontal server scaling / distributed connection registry
