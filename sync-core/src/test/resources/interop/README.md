# Interop Fixtures

This directory is the canonical cross-repo wire-format pin for the Octi clients.
[`d4rken-org/octi-web`](https://github.com/d4rken-org/octi-web) and
[`d4rken-org/octi-desktop`](https://github.com/d4rken-org/octi-desktop) consume
these files via a pinned app-main commit SHA and must successfully decode every
fixture under that pin or their CI fails.

## Schema (v1)

### `manifest.json`

Entry point. Lists every other file with its SHA-256 so consumers can verify on fetch.

```jsonc
{
  "schemaVersion": 1,
  "source": "d4rken-org/octi",
  "generator": "sync-core InteropFixtureGeneratorTest",
  "files": {
    "tink-vectors.json":      { "sha256": "<hex>" },
    "streaming-vectors.json": { "sha256": "<hex>" }
  }
}
```

### `tink-vectors.json`

Payload-layer AEAD vectors. Two keyset blocks, each with its own committed Tink keyset:

```jsonc
{
  "schemaVersion": 1,
  "note": "...",
  "gcmsiv": {
    "keysetType": "AES256_GCM_SIV",
    "keysetBase64": "<Tink keyset proto, base64>",
    "vectors": [
      {
        "name": "hello-world",
        "plaintextBase64": "<gzip-input, base64>",
        "aad": "device-a:module-x",
        "ciphertextBase64": "<Tink AEAD output, base64>"
      }
    ]
  },
  "siv": {
    "keysetType": "AES256_SIV",
    "keysetBase64": "...",
    "vectors": [
      { "name": "hello-world", "plaintextBase64": "...", "aad": "", "ciphertextBase64": "..." }
    ]
  }
}
```

**Wire layering.** `ciphertextBase64` is the output of:
`Tink AEAD(keyset).encrypt(gzip(plaintext), aad)`.
To verify: base64-decode → `Tink AEAD(keyset).decrypt(ciphertext, aad)` → gunzip → compare.

**Legacy SIV.** `AES256_SIV` is deterministic-AEAD and **ignores AAD** by construction.
Vectors emit `"aad": ""` to make that contract explicit.

**Tink prefix.** Every `ciphertextBase64` starts with byte `0x01` (Tink wire-format
version marker), followed by the 4-byte key id, then nonce + ciphertext + tag. Consumers
should pin the prefix byte to catch silent Tink upgrades.

### `streaming-vectors.json`

Blob-layer streaming-AEAD vectors (Tink's `AesGcmHkdfStreaming` over an HKDF-SHA256-derived
key — see [`StreamingPayloadCipher.kt`](../../../main/java/eu/darken/octi/sync/core/blob/StreamingPayloadCipher.kt)).

```jsonc
{
  "schemaVersion": 1,
  "note": "...",
  "keysetType": "AES256_GCM_SIV",
  "keysetBase64": "<Tink keyset proto, base64>",
  "vectors": [
    {
      "name": "short",
      "aad": "device-a:module-x:key-2",
      "plaintextBase64": "SGkgZnJvbS...",   // inline plaintext
      "plaintextSize": 18,
      "ciphertextBase64": "...",
      "ciphertextSize": 73
    },
    {
      "name": "two-segments",
      "aad": "device-b:eu.darken.octi.module.core.files:key-4",
      "plaintextPattern": { "kind": "sequential", "size": 1048577 },   // reconstructible plaintext
      "plaintextSize": 1048577,
      "ciphertextBase64": "...",
      "ciphertextSize": 1048629
    }
  ]
}
```

Each vector has **either** `plaintextBase64` **or** `plaintextPattern`, never both
(verifier enforces this). Patterns avoid committing megabytes of base64 for plaintext
that's algorithmically generated.

**Plaintext patterns.** Currently one kind:

| `kind` | Definition |
|---|---|
| `sequential` | byte `i` = `(i and 0xFF).toByte()` for `i in 0 until size` |

Add a new kind by extending `InteropFixtures.materializePattern` in app-main AND every
consumer's port of the same function. New `kind` values are wire-incompatible — older
consumers must reject `unknown plaintextPattern.kind`.

## Why ciphertext bytes change every regeneration

Tink AES-GCM-SIV and streaming-AEAD both use a random nonce per encrypt. Re-running
the generator with the same keyset produces different ciphertext bytes every time.
The verifier therefore **decrypts** committed ciphertext rather than byte-comparing
re-encrypted output.

Practical consequence: regenerating always changes `tink-vectors.json` and
`streaming-vectors.json` bytes (and their manifest SHAs), even when no wire-format
change was intended. Don't regenerate without reason.

## Consumer integration

1. Pin a full 40-character app-main commit SHA in `fixture-lock.json`.
2. Fetch this directory at that SHA (e.g. via `actions/checkout` + sparse-checkout).
3. Verify `manifest.json` SHA-256 first; reject the bundle on mismatch.
4. For each file in `manifest.files`, verify SHA-256 against `manifest.files[name].sha256`.
5. Decode + decrypt under the committed keysets. Compare plaintext (or materialized
   pattern) to the committed `plaintextBase64` / `plaintextPattern`.
6. Reject `schemaVersion` values you don't recognize.

See [`.claude/rules/interop-fixtures.md`](../../../../.claude/rules/interop-fixtures.md)
for the producer-side workflow and regeneration rules.
