# HTTP API

All endpoints live under `/v1/`. A server address is a protocol, a domain and a port, so the base
URL is:

```
<protocol>://<domain>:<port>/v1/
```

for example `https://octi.example.invalid:443/v1/`.

Error bodies are human-readable plain text, not JSON. Branch on the status code and the
`X-Octi-Reason` header, never on the body text.

## Authentication

Every request except registration carries two headers:

| Header | Value |
|---|---|
| `Authorization` | `Basic ` + base64 of `<accountId>:<devicePassword>` |
| `X-Device-ID` | The caller's device UUID |

The server splits the decoded credential on the first `:`, parses the left half as the account
UUID, and looks up the device by (account id, device id). The password is compared in constant
time. Both headers are required: the device id identifies which device inside the account is
calling, and the account id comes only from the credential.

Authentication failures:

| Status | Cause |
|---|---|
| `400` | `X-Device-ID` missing or not a UUID |
| `400` | `Authorization` missing, not `Basic`, not decodable, or without a `:` |
| `404` | No device with that (account id, device id) pair |
| `401` | Device exists but the password does not match |

The Android client treats `401`, `404` and `410` on any call as "no longer registered" and pauses
the connector. The pinned server never returns `410`; the client accepts it defensively.

## Device metadata headers

Optional on any request. This is how a device advertises itself to peers.

| Header | Meaning |
|---|---|
| `Octi-Device-Version` | Client version string |
| `Octi-Device-Platform` | Platform identifier, `android` for the Android client |
| `Octi-Device-Label` | Human-readable device name |
| `Octi-Device-Capabilities` | JSON array of capability tags, see [capabilities.md](capabilities.md) |

How the server treats them:

- Values are stored on the device record and echoed back to peers through `GET /v1/devices`.
- An absent header leaves the stored value untouched; a header with a new value overwrites it.
  Omitting a header never clears a value.
- `Octi-Device-Label` is trimmed and truncated to 128 characters. Blank after trimming normalizes
  to null, which means different things on the two paths. At **registration** the device is
  created with no label. On an **existing** device it looks like an absent header and is treated as
  no update, so the stored label survives. A label cannot be cleared over HTTP at this revision.
- `Octi-Device-Version` and `Octi-Device-Platform` are stored verbatim, with no length or charset
  constraint at this revision.
- `Octi-Device-Capabilities` is validated as a whole set. One bad tag discards the entire header
  value; the request itself still succeeds. See [capabilities.md](capabilities.md).
- During registration only, `Octi-Device-Version` falls back to the `User-Agent` header when
  absent.
- Metadata is recorded only for requests that pass authentication and the per-account rate limit,
  so a rejected request updates neither `lastSeen` nor any metadata field.

Producer policy is separate. The Android client strips non-printable ASCII from the device model
and truncates to 128 characters. The server does not require that, and other clients need not copy
it.

## Identifiers and validation

| Identifier | Rule |
|---|---|
| Account id | UUID. Issued by the server at registration. |
| Device id | UUID. Chosen by the client. Registrable **once globally**: a second `POST /v1/account` with a device id that already exists anywhere on the server returns `400`. |
| Module id | Semantically opaque to the server, syntactically constrained: at most **1024** characters and matching `^[a-z]+(\.[a-z0-9_]+)*$`. Anything else returns `400`. |
| `device-id` query parameter | UUID of a device on the caller's account. Not a UUID gives `400`; not a device on this account gives `404`. |

## Endpoint index

Routes served at the pinned server revision:

| Method and path | Purpose | Called by shipping clients |
|---|---|---|
| `POST /v1/account` | Register a device, creating or joining an account | yes |
| `DELETE /v1/account` | Delete the whole account | yes |
| `POST /v1/account/share` | Create a share code | yes |
| `GET /v1/account/storage` | Quota and server limits | yes |
| `GET /v1/devices` | List the account's devices | yes |
| `DELETE /v1/devices/{deviceId}` | Remove one device | yes |
| `POST /v1/devices/reset` | Wipe module data for devices | yes |
| `GET /v1/module/{moduleId}` | Read a document | yes |
| `POST /v1/module/{moduleId}` | Unconditional legacy write | yes |
| `PUT /v1/module/{moduleId}` | Conditional write with blob references | yes |
| `DELETE /v1/module/{moduleId}` | Delete a document and everything it references | **no** |
| `GET /v1/module/{moduleId}/blobs` and the other blob routes | Blob transfer, see the scope note below | partially |
| `GET /v1/status` | Liveness probe, returns `{"status":"ok"}`, unauthenticated | no |
| `GET /v1/metrics` | Aggregate server counters, unauthenticated | no |
| `GET /v1/myip` | Returns `{"ip":"<caller ip>"}`, unauthenticated | no |
| `GET /v1/ws` | WebSocket upgrade, see [websocket.md](websocket.md) | yes |

`DELETE /v1/module/{moduleId}` is listed so the endpoint set is not silently partial. It takes the
same `device-id` parameter as the other module routes, deletes the document and every blob it
references, and emits a `deleted` notification. No shipping client calls it.

## Account endpoints

### `POST /v1/account`

Registers the calling device. With `?share=<code>` it joins that code's account; without it, a new
account is created. See [linking.md](linking.md) for the full flow.

Request: `X-Device-ID` required, `Authorization` must **not** be present, device metadata headers
optional.

Response `200`:

```json
{ "account": "<account uuid>", "password": "<device password>" }
```

| Status | Cause |
|---|---|
| `400` | `X-Device-ID` missing or not a UUID |
| `400` | Device id already registered anywhere on this server |
| `400` | `Authorization` parsed as Basic credentials |
| `403` | `?share=` did not match a stored share, or its account no longer exists |
| `409` | Account is at its device limit (default 64, deployment-configurable) |

On `403` because the account vanished, and on `409`, the share code is restored and can be retried.

The check is narrower than "no `Authorization` header". The server rejects only a header it can
parse as Basic credentials: it starts with `Basic `, the rest base64-decodes, the decoded text
contains a `:`, and the part before that `:` parses as a UUID. Anything else counts as no header
and the
registration proceeds. A `Bearer` token, a `Basic ` value that is not valid base64, and Basic
credentials with a non-UUID username all fall into that gap.

The gap matters with `?share=`: the check runs before the share is consumed, so a rejected header
leaves the code usable while an ignored one burns it. Send no `Authorization` header at all.

### `DELETE /v1/account`

Authenticated. Aborts upload sessions, releases quota, and deletes every device, every share and
all stored data. Responds `200` with no body. Not reversible, not confirmable, and it destroys the
caller's own registration too.

### `POST /v1/account/share`

Authenticated. Returns `{"code": "<share code>"}`. See [linking.md](linking.md).

### `GET /v1/account/storage`

Authenticated. Reports the account's usage plus the server's configured limits. Use it to discover
a deployment's values instead of hard-coding them. It does not report every server setting: the
per-IP rate limit, the CORS allowlist and the retention thresholds are all absent.

```json
{
  "storageApiVersion": 2,
  "accountQuotaBytes": 52428800,
  "usedBytes": 0,
  "reservedBytes": 0,
  "availableBytes": 52428800,
  "maxBlobBytes": 10485760,
  "maxModuleDocumentBytes": 262144,
  "maxActiveUploadSessionsPerDevice": 8,
  "idleSessionTtlSeconds": 3600,
  "absoluteSessionTtlSeconds": 86400,
  "maxDevicesPerAccount": 64,
  "maxModulesPerDevice": 256,
  "maxBlobRefsPerModule": 64,
  "maxActiveUploadSessionsPerAccount": 32,
  "completeIdleTtlSeconds": 600,
  "accountRateLimit": 256,
  "accountRateLimitWindowSeconds": 60
}
```

All seventeen fields are always present. `availableBytes` is
`max(0, accountQuotaBytes - usedBytes - reservedBytes)`. `reservedBytes` covers blob upload sessions
that reserved space but have not committed.

`storageApiVersion` is the feature probe for the blob layer. The Android client treats `1` or
higher as "blob-backed modules supported", and a `404` or `405` here as "legacy server, no blob
support". The value at this revision is `2`. A client that never uses blobs need not call this
endpoint.

The Android client's DTO decodes only five of these fields. That is a client limitation; the server
sends the response above.

## Device endpoints

### `GET /v1/devices`

Authenticated. Lists every device on the caller's account, including the caller.

```json
{
  "devices": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "version": "1.2.3",
      "platform": "android",
      "label": "Example Phone",
      "capabilities": ["encryption:AES256_GCM_SIV", "encryption:AES256_SIV", "encryption:_reported"],
      "addedAt": "2026-01-01T12:00:00Z",
      "lastSeen": "2026-01-02T08:30:00Z"
    }
  ]
}
```

Field notes:

- `version`, `platform` and `label` are always present, and `null` when the device has never
  reported them.
- `capabilities` is **omitted entirely** when the device has never reported a valid capability set.
  When present it is a real JSON array of strings, not a stringified one. See
  [capabilities.md](capabilities.md).
- `addedAt` and `lastSeen` are ISO-8601 instants in UTC.

This is also how a client discovers its peers. There is no separate peer list.

### `DELETE /v1/devices/{deviceId}`

Authenticated. Removes another device (or the caller), aborting its upload sessions and deleting
all of its stored modules. `400` if the path segment is not a UUID, `404` if no such device is on
this account, `200` on success.

The removed device's credentials stop working at once. Its next request gets `404`, which is how it
learns it was revoked.

### `POST /v1/devices/reset`

Authenticated. Deletes stored module data without removing the device registrations.

```json
{ "targets": ["11111111-1111-1111-1111-111111111111"] }
```

An empty `targets` array means every device on the account. `404` if any listed device is not on
this account, and nothing is reset in that case. `200` on success.

Client divergence: the server expects `targets` to be an array of UUID **strings**. The Android
client's DTO would encode each entry as `{"id": "<uuid>"}`, which the server rejects with `400`. It
never fires today because the Android client only ever sends an empty list.

## Module endpoints

All three take the same target selector:

```
/v1/module/{moduleId}?device-id=<target device uuid>
```

`moduleId` is the same for every owner; `device-id` selects whose copy is addressed. Reading a
peer's document and writing your own use the same route with a different `device-id`.

### `GET /v1/module/{moduleId}?device-id=`

Authenticated read.

| Status | Meaning |
|---|---|
| `204` | No document metadata: the slot has never been written, or it was deleted. No headers, no body. |
| `200` | Document present. |
| `400` | Missing or malformed `moduleId`, missing or malformed `device-id` |
| `404` | Target device is not on this account |

A `200` response carries:

| Header | Value |
|---|---|
| `X-Modified-At` | Server-side modification time as an HTTP-date (RFC 1123) |
| `ETag` | Current strong entity tag, quoted, 32 lowercase hex characters |
| `Content-Type` | `application/octet-stream` |

The body is the raw stored bytes, the ciphertext from [encryption.md](encryption.md). It may be
zero length. `ETag` is present whenever metadata exists.

Two Android quirks. It reads the standard `Date` response header to estimate its clock offset
against the server, ordinary HTTP rather than an Octi extension. It treats a body of the four bytes
`null` as empty; the pinned server never emits that.

### Write semantics and concurrency

The two write verbs are not interchangeable. Confusing them is the easiest way to destroy a peer's
data.

`POST` is the **unconditional legacy write**. The body is raw ciphertext, there is no precondition,
and it always overwrites. Once a module has external blob references it returns `409`, since a raw
overwrite cannot say what happens to the blobs.

`PUT` is the **conditional commit**. It requires exactly one applicable precondition:

| Situation | Header to send |
|---|---|
| Updating a document you have read | `If-Match: "<the ETag from your read>"` |
| Creating a document that must not exist yet | `If-None-Match: *` |

Rules the server enforces:

- Sending both headers is `400`.
- Sending neither is `412` with the message `PUT requires If-Match or If-None-Match: *`.
- `If-None-Match: *` when the module already exists is `412`.
- `If-Match` when the module does not exist is `412`.
- `If-Match` whose value is not the current tag is `412`.
- A malformed entity tag is `400`. Weak tags (`W/"..."`) are rejected outright, since `If-Match`
  requires strong comparison. Both `"quoted"` and bare unquoted forms are accepted.

**On `412`, re-read before retrying.** The slot changed since the tag you hold was issued.
Repeating the same body with a refreshed tag silently discards what the other writer committed.
Read, reconcile, then write. The Android client refreshes its cached tag and retries once, safe only
because the single-writer invariant means the concurrent writer was itself.

Entity tags are **random 16-byte values, hex-encoded**, regenerated on every successful write. They
are not content-derived, so writing an identical document twice gives two different tags. Compare
them for equality only.

### `POST /v1/module/{moduleId}?device-id=`

Body: raw ciphertext bytes. No content type is required.

| Status | Meaning |
|---|---|
| `200` | Written. Response carries the new `ETag` header. |
| `409` | The module has external blob references; use `PUT` |
| `409` | Module count limit for this device reached (default 256) |
| `413` | Body exceeds the payload limit (default 128 KiB) |
| `507` | Account quota exceeded, with `X-Octi-Reason: account_quota_exceeded` |

### `PUT /v1/module/{moduleId}?device-id=`

Body: JSON.

```json
{
  "documentBase64": "<base64 of the ciphertext>",
  "blobRefs": [ { "blobId": "<blob id>" } ]
}
```

`blobRefs` may be omitted or empty; a client without blobs always sends it empty. Each listed blob
must already be uploaded through the blob session routes, which this revision does not specify.

| Status | Meaning |
|---|---|
| `200` | Committed. Response carries the `ETag` header and the body `{"etag":"<hex>"}` |
| `400` | Both preconditions sent, malformed entity tag, invalid base64, duplicate `blobId` values, too many blob refs, or a `blobId` that cannot be resolved |
| `412` | Precondition missing, stale, or not applicable |
| `409` | Module count limit for this device reached |
| `413` | Decoded document exceeds `maxModuleDocumentBytes` (default 256 KiB) |
| `507` | Account quota exceeded, with `X-Octi-Reason: account_quota_exceeded` |

The route's raw body limit is twice `maxModuleDocumentBytes`, leaving room for base64 expansion of
a document at the maximum size.

The Android client falls back from `PUT` to `POST` on `404` or `405`, treating those as "this server
predates blob support".

## Errors

| Status | Emitted by | Meaning |
|---|---|---|
| `400` | any | Malformed header, identifier, precondition, or body |
| `401` | any authenticated route | Wrong device password |
| `403` | `POST /v1/account` | Invalid share code, or its account is gone |
| `404` | any authenticated route | Caller device unknown for this account |
| `404` | module and device routes | Target device not on this account |
| `409` | `POST /v1/account` | Device limit reached |
| `409` | module writes | Blob-backed module written with `POST`, or module count limit |
| `412` | `PUT /v1/module/...` | Precondition failed |
| `413` | any | Request body or decoded document too large |
| `429` | any | Rate limited, with `Retry-After` in delta-seconds |
| `500` | any | Unhandled server error |
| `507` | module writes, blob routes | Storage refusal, qualified by `X-Octi-Reason` |

### `X-Octi-Reason`

Only ever sent alongside `507`, with exactly two defined values:

| Value | Emitted by | Meaning |
|---|---|---|
| `account_quota_exceeded` | module `POST`, module `PUT`, and blob session routes | The account is at its storage quota |
| `server_disk_low` | blob routes only | The server host is below its free-disk floor |

A module write therefore never produces `server_disk_low`. A client that only writes documents
handles `account_quota_exceeded`, and should treat a `507` with a missing or unknown reason as a
generic storage refusal.

`Retry-After` is emitted only in delta-seconds form, never as an HTTP-date.

## Limits and rate limiting

Every value below is a **deployment-configurable default** from the server's configuration, not a
protocol constant. `GET /v1/account/storage` reports the live values for most of them.

| Limit | Default | Notes |
|---|---|---|
| Request body | 128 KiB | Global; the `PUT` module route raises it to 512 KiB |
| Module document | 256 KiB | Checked after base64 decoding |
| Devices per account | 64 | `409` at registration |
| Modules per device | 256 | `409` on write |
| Blob refs per module | 64 | `400` on commit |
| Account storage quota | 50 MB | `507` with `account_quota_exceeded` |
| Per-IP rate limit | 512 requests per 60 s | `429` with `Retry-After` |
| Per-account rate limit | 256 requests per 60 s | `429` with `Retry-After` |

Rate limiting is layered. The per-IP limiter runs before authentication and counts every request
except CORS preflight `OPTIONS`. The per-account limiter runs after credentials validate, so one
account on a shared NAT address cannot exhaust another's budget. An operator can disable both with
one switch.

Browser clients face one more gate: the server's CORS allowlist. It ships with the official
octi-web origins; an operator can replace or empty it. Non-browser clients are unaffected.

### Retention and garbage collection

The server deletes idle data on its own. Both sweeps are destructive and neither is announced, so a
client that assumes the server keeps what it wrote indefinitely will lose data.

| Sweep | Deletes | Clock it reads | Threshold | Interval |
|---|---|---|---|---|
| Device GC | The device registration and every module that device owns | `lastSeen` on the device record | 90 days | 10 minutes |
| Module GC | One module slot, with its document and its blobs | The module's effective last-access time | 90 days | 10 minutes |

Both loops run every 10 minutes, with the first pass one minute after server start, so deletion
happens up to one interval after the threshold is crossed.

`lastSeen` is refreshed by any request **from that device** that passes authentication and the
per-account rate limit, including the WebSocket upgrade. It tracks the caller only: a peer reading
or writing your slots does not refresh your `lastSeen`. A device that goes quiet for 90 days is
deleted with all
of its data, even while its peers still read that data. Its next request gets `404`, as if it had
been revoked, and it must register again. The value lives in memory and is written to disk at most
once every 30 seconds.

A module's last-access time is refreshed by reading the slot, from any device on the account, by
writing it with `POST` or `PUT`, and by the blob routes: listing blobs, downloading a blob, and
creating, appending to or finalizing an upload session. It lives in memory and is persisted at most
once every 30 seconds. With no persisted value the server falls back to the modification time of
the slot's metadata file, then of its payload file.

Module GC skips a slot whose upload session is active, or finalized but not yet committed, and has
not itself expired, so an in-flight upload cannot be reaped underneath itself. Device GC has no
such exemption: it aborts the device's upload sessions and deletes it.

Both thresholds are server configuration with a 90-day default. `GET /v1/account/storage` does not
report them and the pinned revision parses no command-line flag for either one, so a client cannot
discover a deployment's values. Do not hard-code 90 days. Treat "stored data disappears after long
inactivity" as the contract: keep talking to the server, and be able to re-upload.

## Blob layer, out of scope

The following routes exist at the pinned revision and are **not specified** here:

```
GET    /v1/module/{moduleId}/blobs
GET    /v1/module/{moduleId}/blobs/{blobId}
DELETE /v1/module/{moduleId}/blobs/{blobId}
POST   /v1/module/{moduleId}/blob-sessions
GET    /v1/module/{moduleId}/blob-sessions/{sessionId}
PATCH  /v1/module/{moduleId}/blob-sessions/{sessionId}
POST   /v1/module/{moduleId}/blob-sessions/{sessionId}/finalize
DELETE /v1/module/{moduleId}/blob-sessions/{sessionId}
```

They implement resumable chunked upload, download and lifecycle for file attachments. A client that
never attaches files never touches them, and can always send `PUT` with an empty `blobRefs` list.

For the shapes, read the client's Retrofit declarations in
[`OctiServerApi.kt`](../../syncs-octiserver/src/main/java/eu/darken/octi/syncs/octiserver/core/OctiServerApi.kt),
the server's
[`BlobRoute.kt`](https://github.com/d4rken-org/octi-server/blob/7e813e2b7d198daae30bdb3cc17e5544ab9b3c22/src/main/kotlin/eu/darken/octi/server/module/BlobRoute.kt),
and for the encryption of blob content the committed vectors in
[`streaming-vectors.json`](../../sync-core/src/test/resources/interop/streaming-vectors.json).

## Known module ids

| Module id | Content |
|---|---|
| `eu.darken.octi.module.core.meta` | Device metadata |
| `eu.darken.octi.module.core.power` | Battery and charging state |
| `eu.darken.octi.module.core.wifi` | Wi-Fi connection information |
| `eu.darken.octi.module.core.connectivity` | Network connectivity information |
| `eu.darken.octi.module.core.apps` | Installed applications |
| `eu.darken.octi.module.core.clipboard` | Shared clipboard |
| `eu.darken.octi.module.core.files` | Shared files |

Module ids are opaque to the protocol. This table is what the Android client currently produces and
consumes, not a closed set. Ignore an unknown module id and carry on; never reject the peer or the
response. The document schemas behind these ids are out of scope for this revision.
