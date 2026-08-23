# Linking

How a second device joins an existing account. Two things travel from the host device to the
joining device: a server-issued share code, and the account's encryption keyset. The server only
knows about the first one.

## 1. The host creates a share code

```http
POST /v1/account/share
X-Device-ID: 11111111-1111-1111-1111-111111111111
Authorization: Basic <base64 of accountId:devicePassword>
```

Response:

```json
{ "code": "0000example000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000example" }
```

The code is 64 random bytes, hex-encoded, so 128 lowercase hex characters. Treat it as an opaque
string; nothing in the protocol parses it.

### Lifetime is best effort, not a hard TTL

A share is stored with its creation time. Two mechanisms interact:

- The server's `shareExpiration` says when a share counts as expired. It is server configuration
  with a 60-minute default, and the pinned revision parses no command-line flag for it, so a client
  cannot discover a deployment's value.
- Expired shares are deleted by a periodic sweep whose interval is `shareExpiration / 2`, so
  **30 minutes** at the default.

Redemption does not check age. `consumeShare()` looks the code up and removes it, without comparing
timestamps. A code stays usable until a sweep removes it, which at the defaults can be up to
roughly 90 minutes after creation.

60 minutes is not a guarantee in either direction: do not assume a code is dead after an hour, or
that one will still work.

See [`ShareRepo.kt`](https://github.com/d4rken-org/octi-server/blob/7e813e2b7d198daae30bdb3cc17e5544ab9b3c22/src/main/kotlin/eu/darken/octi/server/account/share/ShareRepo.kt)
and the `shareExpiration` default in
[`App.kt`](https://github.com/d4rken-org/octi-server/blob/7e813e2b7d198daae30bdb3cc17e5544ab9b3c22/src/main/kotlin/eu/darken/octi/server/App.kt).

### Redemption is transactional

The registration handler consumes the share before creating the device, and puts it back when
registration cannot complete:

- the account referenced by the share no longer exists (response `403`),
- the account is at its device limit (response `409`),
- device creation fails for any other reason (the error propagates as `500`).

A failed join does not burn the code. See
[`AccountRoute.kt`](https://github.com/d4rken-org/octi-server/blob/7e813e2b7d198daae30bdb3cc17e5544ab9b3c22/src/main/kotlin/eu/darken/octi/server/account/AccountRoute.kt).

## 2. The host renders a link payload

Without the keyset the joining device could talk to the server but not read or write anything. The
host packages code and keyset into one payload, shown as a QR code or as text.

The plaintext is JSON, defined by
[`LinkingData.kt`](../../syncs-octiserver/src/main/java/eu/darken/octi/syncs/octiserver/core/LinkingData.kt),
with the sub-shapes from
[`OctiServer.kt`](../../syncs-octiserver/src/main/java/eu/darken/octi/syncs/octiserver/core/OctiServer.kt)
and [`PayloadEncryption.kt`](../../sync-core/src/main/java/eu/darken/octi/sync/core/encryption/PayloadEncryption.kt):

```json
{
  "serverAddress": {
    "domain": "octi.example.invalid",
    "protocol": "https",
    "port": 443
  },
  "shareCode": {
    "code": "<the 128 hex characters from step 1>"
  },
  "encryptionKeySet": {
    "type": "AES256_GCM_SIV",
    "key": "<base64 of the Tink binary keyset proto>"
  }
}
```

Field notes:

- `serverAddress.protocol` defaults to `https` and `serverAddress.port` to `443`. The Android
  encoder writes both explicitly, so both keys are present in payloads it produces. A decoder
  should still apply the defaults if a key is missing.
- `encryptionKeySet.type` is an [encryption mode](encryption.md) identifier, either
  `AES256_GCM_SIV` or `AES256_SIV`.
- `encryptionKeySet.key` is the base64 of the serialized Tink keyset, not a raw key. See
  [encryption.md](encryption.md).

The transported form is:

```
base64( gzip( utf8( json ) ) )
```

Decoding reverses that: base64 decode, gunzip, parse JSON.

### Validity hints

From [`LinkCodeShape.kt`](../../syncs-octiserver/src/main/java/eu/darken/octi/syncs/octiserver/core/LinkCodeShape.kt),
useful for diagnosing a bad paste without logging the payload itself:

- A complete payload is roughly **496 characters**. A much shorter one was truncated.
- It always starts with `H4sI`, the base64 of the gzip magic bytes `1f 8b 08`. A payload that does
  not is missing its beginning, which whitespace-tolerant base64 decoding cannot detect on its own.
- The Android client trims the code before decoding, and okio's base64 decoder also skips ASCII
  space, tab, carriage return and line feed **anywhere** in the payload, so a line-wrapped code
  still decodes. Any other stray character fails the decode. The `alphabet` check in
  `LinkCodeShape.kt` does not allow those whitespace characters, so a wrapped code that decodes
  still reports `alphabet=false`. That is Android decoder tolerance, not a protocol guarantee.
  Another client may be stricter, so emit the code with no embedded whitespace.
- The Android decoder accepts both the standard and the URL-safe base64 alphabets.

## 3. The joining device registers

```http
POST /v1/account?share=<the 128 hex characters>
X-Device-ID: 22222222-2222-2222-2222-222222222222
```

Send no `Authorization` header; there is nothing to authenticate against yet. The server's own
check is narrower: it answers `400` only for a header it can parse as Basic credentials whose
decoded username is a UUID. Anything else, a `Bearer` token or a malformed Basic value, is ignored
and the registration proceeds, consuming the share code. The `400` will not protect a code from a
stray header. See [http-api.md](http-api.md) for the full rule.

Response:

```json
{
  "account": "33333333-3333-3333-3333-333333333333",
  "password": "0000example000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000example"
}
```

`account` is the account UUID, `password` is this device's password (64 random bytes hex-encoded,
128 characters). From here on, every request carries
`Authorization: Basic base64("<account>:<password>")` plus the same `X-Device-ID`. See
[http-api.md](http-api.md).

Registration without `?share=` creates a **new** account instead of joining one, and the client
generates its own encryption keyset at that point.

Failure modes are listed under `POST /v1/account` in [http-api.md](http-api.md).

## 4. The joining device adopts the keyset

The keyset in the link payload is the account's payload encryption keyset. The joining device
stores it and uses it for every read and write. There is no key exchange with the server and no
negotiation between peers.

## Security note

The link payload carries the account's encryption keyset, so it is the entire secret. Anyone who
gets it before it is redeemed can join the account **and** decrypt everything stored on it,
including documents written before and after they joined. Consuming the share code does not
invalidate a keyset that leaked.

Treat the payload like a private key: show it on screen only as long as needed, never write it to
logs, crash reports or analytics, and never send it over a channel less trusted than the one it is
establishing.
