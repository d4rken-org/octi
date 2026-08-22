# Linking

How a second device joins an existing account. Two things have to travel from the host device to
the joining device: a server-issued share code, and the account's encryption keyset. The server
only knows about the first one.

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

The code is generated as 64 random bytes, hex-encoded, so it is 128 lowercase hex characters. Treat
it as an opaque string; nothing in the protocol parses it.

### Lifetime is best effort, not a hard TTL

A share is stored with its creation time. Two mechanisms interact:

- The server's `shareExpiration` defines when a share counts as expired. It is server
  configuration with a 60-minute default, and the pinned revision parses no command-line flag for
  it, so a client cannot discover a deployment's value.
- Expired shares are deleted by a periodic sweep whose interval is `shareExpiration / 2`, so
  **30 minutes** at the default.

Redemption itself does **not** check age. `consumeShare()` looks the code up and removes it, without
comparing timestamps. A code therefore stays redeemable until a sweep happens to remove it, which at
default settings can be up to roughly 90 minutes after creation.

Clients must not treat 60 minutes as a guarantee in either direction. Do not assume a code is dead
after an hour, and do not assume one will still work.

See [`ShareRepo.kt`](https://github.com/d4rken-org/octi-server/blob/7e813e2b7d198daae30bdb3cc17e5544ab9b3c22/src/main/kotlin/eu/darken/octi/server/account/share/ShareRepo.kt)
and the `shareExpiration` default in
[`App.kt`](https://github.com/d4rken-org/octi-server/blob/7e813e2b7d198daae30bdb3cc17e5544ab9b3c22/src/main/kotlin/eu/darken/octi/server/App.kt).

### Redemption is transactional

The registration handler consumes the share before it creates the device, and restores it if the
registration cannot complete. The share is put back when:

- the account referenced by the share no longer exists (response `403`),
- the account is at its device limit (response `409`),
- device creation fails for any other reason (the error propagates as `500`).

A failed join therefore does not burn the code. See
[`AccountRoute.kt`](https://github.com/d4rken-org/octi-server/blob/7e813e2b7d198daae30bdb3cc17e5544ab9b3c22/src/main/kotlin/eu/darken/octi/server/account/AccountRoute.kt).

## 2. The host renders a link payload

The share code alone is not enough: without the account's keyset the joining device could talk to
the server but could not read or write anything. The host packages both into one payload, which it
displays as a QR code or as text.

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

- `serverAddress.protocol` defaults to `https` and `serverAddress.port` to `443`, but the Android
  encoder writes defaults explicitly, so both keys are present in payloads it produces. A decoder
  should still apply those defaults if a key is missing.
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
- It always starts with `H4sI`, which is the base64 of the gzip magic bytes `1f 8b 08`. A payload
  that does not is missing its beginning, which whitespace-tolerant base64 decoding cannot detect on
  its own.
- The Android client trims the code before decoding, and okio's base64 decoder additionally skips
  ASCII space, tab, carriage return and line feed **anywhere** in the payload, so a code that an
  email client line-wrapped still decodes. Any other stray character fails the decode outright.
  The `alphabet` check in `LinkCodeShape.kt` does not allow those whitespace characters, so a
  wrapped code that decodes successfully still reports `alphabet=false`. This is what the Android
  decoder happens to accept, not a protocol guarantee; another client may be stricter, so a
  producer should emit the code with no embedded whitespace.
- The Android decoder accepts both the standard and the URL-safe base64 alphabets.

## 3. The joining device registers

```http
POST /v1/account?share=<the 128 hex characters>
X-Device-ID: 22222222-2222-2222-2222-222222222222
```

Send no `Authorization` header; there is nothing to authenticate against yet. The server's own
check is narrower than that requirement: it answers `400` only for a header it can parse as Basic
credentials whose decoded username is a UUID. A header it cannot parse that way, a `Bearer` token
or malformed Basic value, is ignored and the registration proceeds, consuming the share code. Do
not rely on the `400` to protect a code from a stray header. See
[http-api.md](http-api.md).

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

The keyset from the link payload is the account's payload encryption keyset. The joining device
stores it and uses it for every read and write. There is no key exchange with the server and no
key negotiation between peers; the keyset in the payload is authoritative.

## Security note

The link payload carries the account's encryption keyset. It is the entire secret. Anyone who
obtains the payload before it is redeemed can join the account **and** decrypt everything stored on
it, including documents written before and after they joined. Consuming the share code does not
invalidate the keyset in a payload that leaked.

Treat the payload like a private key: show it on screen only for as long as needed, never write it
to logs, crash reports, or analytics, and never transmit it over a channel less trusted than the
one it is establishing.
