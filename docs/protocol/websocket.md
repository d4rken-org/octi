# WebSocket notifications

A change notification channel. It tells a client that something changed so the client can fetch it
over HTTP sooner than its next poll. It never carries payload data, and it is not a reliable stream.

A client that ignores this channel entirely still works. It just reacts more slowly.

## Connecting

```
wss://<domain>:<port>/v1/ws
```

Use `ws://` when the configured server protocol is plain `http`, `wss://` otherwise.

The upgrade request carries the same authentication headers as any HTTP call:

```http
GET /v1/ws HTTP/1.1
X-Device-ID: 11111111-1111-1111-1111-111111111111
Authorization: Basic <base64 of accountId:devicePassword>
```

The device metadata headers described in [http-api.md](http-api.md) are also read on the upgrade and
update the stored device record exactly as they do on an HTTP call.

Authentication runs the same code path as the HTTP routes, and the per-account rate limit applies to
connection establishment.

### Close codes

A refused connection fails differently depending on whether the upgrade already completed.

**Before the upgrade** the request is still an ordinary HTTP call, so the HTTP plugins can reject
it and the client sees an HTTP error response instead of a handshake. The global per-IP request
limiter runs ahead of routing and treats the upgrade request like any other call; over the limit it
answers `429` with `Retry-After` in delta-seconds. When CORS is enabled, an `Origin` that is not on
the allowlist is answered with `403` before any upgrade happens; the CORS note in
[http-api.md](http-api.md#limits-and-rate-limiting) describes the allowlist and who it applies to.

**After the upgrade** the route-level failures below, authentication, the per-account rate limit and
the connection caps, arrive as a WebSocket close:

| Code | Reason text | Cause |
|---|---|---|
| `1008` | `Authentication failed` | Credentials missing, malformed, or wrong |
| `1013` | `Account rate limit exceeded` | Per-account rate limit hit on the upgrade |
| `1013` | `Server connection limit reached` | Global session cap reached |
| `1013` | `Too many connections from this IP` | Per-IP session cap reached |
| `1008` | `Frame rate limit exceeded` | The client sent too many frames |
| `1001` | `Session replaced` | A newer session for the same device took over, or this session was evicted by the per-account session cap |

`1008` on authentication is permanent until credentials change; reconnecting in a loop will not fix
it. `1013` is explicitly retryable after a delay.

### Session model

Sessions are keyed by (account id, device id). Opening a second connection for the same key
**replaces** the first: the old session's outbox is closed and its socket is closed with `1001`.
A client must not hold two sockets for one device id.

Connection caps at the pinned revision, all server-side constants rather than configurable options:

| Cap | Value |
|---|---|
| Sessions per account | 64, oldest evicted when exceeded |
| Sessions per client IP | 32, connection rejected |
| Sessions server-wide | 10000, connection rejected |

The server sweeps sessions whose outbox is already closed every 2 minutes.

### Timing

Server-side WebSocket configuration: **ping period 30 seconds**, **pong timeout 60 seconds**,
**maximum frame size 4096 bytes**.

The 60 seconds is the pong deadline, not an inactivity timeout on application traffic. The server
pings every 30 seconds and terminates the session if no pong comes back within 60 seconds; a
connection that carries no notifications for hours stays open as long as pongs keep arriving. Any
conforming WebSocket client answers pings automatically.

The 4096-byte frame limit is Ktor's `maxFrameSize`, and it applies to frames the server **reads**,
counting the accumulated size of a fragmented message rather than each fragment on its own. It
does not cap the frames the server sends. Do not refuse inbound frames larger than 4096 bytes: as
the batching section below explains, one notification frame can describe every module of every
peer, which is well past 4096 bytes on any account of a realistic size.

The Android client independently sets its own OkHttp ping interval to 30 seconds and reconnects with
exponential backoff from 1 second up to a 30 second ceiling. Those are client policy, not protocol.
Reconnect strategy is entirely up to the implementer, within the retry semantics of the close codes
above.

### Client-to-server frames

There are none. The server reads incoming frames only to enforce a rate limit of **120 frames per
60 second window**, and closes the connection with `1008` when a client exceeds it. Nothing a client
sends is interpreted. Do not send application frames.

## Server frames

Every server frame is a text frame containing one JSON object:

```json
{
  "events": [
    {
      "type": "module_changed",
      "deviceId": "11111111-1111-1111-1111-111111111111",
      "moduleId": "eu.darken.octi.module.core.power",
      "modifiedAt": "2026-01-02T08:30:00.123456789Z",
      "action": "updated",
      "sourceDeviceId": "22222222-2222-2222-2222-222222222222"
    }
  ]
}
```

Server contract, taken from the server's notifier rather than from any client decoder:

| Field | Contract |
|---|---|
| `type` | Always `module_changed` at this revision. It is a discriminator; more types may appear. |
| `deviceId` | The **target**: the device whose module changed, that is the owner of the slot. |
| `moduleId` | The module id of the changed slot. |
| `modifiedAt` | ISO-8601 instant, always present. Generated when the event is queued, so it is a notification timestamp and not necessarily byte-identical to the `X-Modified-At` of the document. |
| `action` | `updated` or `deleted`. |
| `sourceDeviceId` | The **actor**: the device that performed the write. Always present. |

`action` values observed at the pinned revision: `updated` for a legacy `POST` write, for a `PUT`
commit, and for a blob delete; `deleted` for a module delete. Treat an unrecognized action as
`updated`, meaning "re-read this slot", rather than dropping the event.

### Two device ids, one event

`deviceId` and `sourceDeviceId` differ whenever a device writes into another device's slot. The
server broadcasts to every connected session on the account and filters per recipient on
`sourceDeviceId`: a peer never receives events it caused itself. A peer that received an event
therefore knows it did not originate it, and should fetch `(deviceId, moduleId)`.

Do not self-filter on `deviceId`. Under the [single-writer invariant](README.md#single-writer-invariant)
the two are usually equal, but filtering on the target rather than the actor would suppress
legitimate events.

### Batching

Events are batched per account with a 500 millisecond debounce, and further writes inside the window
extend it. One frame can therefore carry several events, and a burst of writes collapses into one
delivery. Clients must handle an `events` array of any length, including one long enough to describe
every module of every peer.

### What the Android decoder tolerates, and why you must not rely on it

The Android client's decoder is deliberately lenient. It is not the contract:

- It accepts `modifiedAt` as absent or null, substituting the current time. The server always sends
  it.
- It defaults `action` to `updated` when absent. The server always sends it.
- It accepts a `blobKey` field. The server does not emit one.
- It has no field for `sourceDeviceId` and ignores it. The server always sends it, and it is what
  self-suppression is based on.
- It logs and skips events whose `type` it does not recognize.

A third-party client should encode the server contract, tolerate unknown fields for
forward-compatibility, and not assume other implementations are as forgiving as this one.

## Delivery is best effort

This is the single most important property of the channel. Notifications are a latency optimization
layered on top of HTTP polling, never a source of truth.

What the server does **not** do:

- **No persistence.** Events go only to sessions that are connected at broadcast time. If no session
  for the account is connected, the batch is discarded.
- **No replay.** Reconnecting does not deliver anything missed while disconnected. There is no
  cursor, sequence number, or resume token in the protocol.
- **No acknowledgement.** The server never learns whether a frame was processed, and never retries.
- **No backpressure.** Each session has a bounded outbox buffer. When it is full, the broadcast drops
  the notification for that peer and moves on. The socket stays open and the client sees no gap.

Consequences for an implementation:

1. Perform a **full HTTP reconciliation** on first connect and again after **every** reconnect. Read
   the device list, then read every module slot you care about. Do not assume the socket picked up
   where it left off.
2. Keep a **periodic reconciliation** running regardless of socket state if the client needs eventual
   consistency. A dropped notification is otherwise invisible until the next unrelated event.
3. Treat every event as a hint to re-read, not as data. Frames carry no payload and no ciphertext.
4. Never gate correctness on receiving an event. A feature that only works when a notification
   arrives is a feature that intermittently does not work.
