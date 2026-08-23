# Encryption

Every module document is encrypted on the writing device and decrypted on the reading device. The
server stores ciphertext, never sees a key, and cannot read or forge a document. It can still
delete one, and it sees the metadata in [http-api.md](http-api.md).

The encryption mode is a property of the **account**, fixed at creation and shared by every device
through the linking payload. It is not negotiated per peer or per module.

## Keyset on the wire

The keyset travels inside the [link payload](linking.md) as:

```json
{
  "type": "AES256_GCM_SIV",
  "key": "<base64 of the Tink binary keyset proto>"
}
```

- `type` is the mode identifier, either `AES256_GCM_SIV` or `AES256_SIV`.
- `key` is base64 of a serialized Tink keyset proto, not a raw symmetric key. Parse it with Tink's
  `TinkProtoKeysetFormat` (the Android client uses `parseKeyset` with `InsecureSecretKeyAccess`: the
  keyset is stored unwrapped by design, since there is no server-side key management).

Without Tink you have to reproduce both the keyset proto parsing and the ciphertext framing
described below.

## `AES256_GCM_SIV`, the default

Default for accounts created by current clients.

- Tink `Aead` primitive over an AES-256-GCM-SIV key.
- Nonce-misuse resistant, but Tink still picks a **random nonce per encryption**, so encrypting the
  same document twice produces different bytes. Never byte-compare ciphertext to detect change; use
  the `ETag`.
- **Associated data is used**: the UTF-8 bytes of

  ```
  <target device id>:<module id>
  ```

  for example `11111111-1111-1111-1111-111111111111:eu.darken.octi.module.core.power`. The device
  id is the slot's owner, in the lowercase UUID form used in the API. The module id is the full
  dotted identifier, not a short label.

The associated data binds a document to its slot: ciphertext moved to a different device id or
module id fails to decrypt, so a malicious server cannot shuffle documents between slots.

## `AES256_SIV`, legacy

Used by accounts created before app version 1.0.0, and by new accounts on devices where AES-GCM-SIV
is unavailable.

- Tink `DeterministicAead` primitive over an AES-256-SIV key.
- Deterministic: the same plaintext under the same key always yields the same ciphertext.
- **Associated data is not used.** The encrypt and decrypt calls pass an empty byte array
  unconditionally, and the caller-supplied associated data is discarded.

Passing the `<deviceId>:<moduleId>` string as associated data to a legacy SIV keyset produces
ciphertext no existing Octi client can read, and fails to decrypt everything those clients wrote.
The mode decides:

| Mode | Primitive | Associated data |
|---|---|---|
| `AES256_GCM_SIV` | `Aead` | UTF-8 of `<deviceId>:<moduleId>` |
| `AES256_SIV` | `DeterministicAead` | empty, always |

## Layering

The transport carries bytes. The known modules encode their documents as JSON today, but the
envelope does not require it; do not assume the plaintext is text.

Writing:

```
module document bytes
  -> gzip
  -> AEAD encrypt (associated data per the table above)
  -> base64            for PUT, as documentBase64
     or raw bytes      for the legacy POST body
```

Reading reverses it exactly:

```
response body (or base64-decoded documentBase64)
  -> AEAD decrypt (same associated data)
  -> gunzip
  -> module document bytes
```

The gzip layer sits inside the encryption, so the server only ever sees ciphertext. Every write
compresses first, without exception; there is no "uncompressed" flag on the wire.

A zero-length response body means the slot exists but holds no document. Do not try to decrypt it.

## Tink ciphertext framing

Both modes start with the same 5-byte prefix: the version byte `0x01` followed by the key id as
4 bytes big-endian. What follows the prefix differs by mode.

| Mode | After the prefix | Overhead over the encrypted input |
|---|---|---|
| `AES256_GCM_SIV` | 12-byte random nonce, then ciphertext, then a 16-byte tag | 33 bytes |
| `AES256_SIV` | 16-byte synthetic IV, which doubles as the authentication tag, then ciphertext | 21 bytes |

Legacy SIV carries no random nonce. Its synthetic IV comes from the key and the plaintext, which
makes the mode deterministic. The 12-byte difference between the overheads is exactly that nonce.
The committed vectors show it: every GCM-SIV ciphertext is 33 bytes longer than the gzipped document
it wraps, every SIV ciphertext 21 bytes longer.

Pin the leading `0x01`. It catches a silent Tink wire-format upgrade on the producing side, and it
is the check the committed interop fixtures perform. See [stability.md](stability.md).

## Mode availability

AES-GCM-SIV is not usable on every Android device: some platform providers accept the
transformation name but return plain AES-GCM. The Android client checks a known-answer test vector
at startup and creates a **legacy SIV account** when the check fails. A legacy-SIV account is
therefore not necessarily an old account.

Because the mode is account-wide, a device that cannot do AES-GCM-SIV cannot usefully join a
GCM-SIV account; it will fail to decrypt what its peers write. Capability tags let a client spot
that mismatch before it tries to decrypt. See [capabilities.md](capabilities.md).

## Blob encryption, out of scope

Blob content uses a separate scheme that this revision does not specify: Tink's streaming AEAD
(`AesGcmHkdfStreaming`, 1 MB segments) under a key derived from the account keyset with HKDF-SHA256,
salt `octi-blob` and info `octi-blob-stream-v1`, with associated data
`<deviceId>:<moduleId>:<blobKey>`; legacy SIV keysets are rejected outright for blob encryption.
Read [`StreamingPayloadCipher.kt`](../../sync-core/src/main/java/eu/darken/octi/sync/core/blob/StreamingPayloadCipher.kt)
and the committed vectors in
[`streaming-vectors.json`](../../sync-core/src/test/resources/interop/streaming-vectors.json) if you
need it.

## Source

- [`PayloadEncryption.kt`](../../sync-core/src/main/java/eu/darken/octi/sync/core/encryption/PayloadEncryption.kt),
  the keyset facade and both primitives.
- [`EncryptionMode.kt`](../../sync-core/src/main/java/eu/darken/octi/sync/core/encryption/EncryptionMode.kt),
  the two mode identifiers.
- [`CryptoBootstrap.kt`](../../sync-core/src/main/java/eu/darken/octi/sync/core/encryption/CryptoBootstrap.kt),
  the availability check.
- [`tink-vectors.json`](../../sync-core/src/test/resources/interop/tink-vectors.json), committed
  ciphertext with the keysets needed to decrypt it, and
  [the fixtures README](../../sync-core/src/test/resources/interop/README.md) explaining the format.
