# Stability and change policy

What a third-party client can rely on, how much of it is verified by machine rather than by prose,
and how changes to each layer are rolled out.

## Coverage matrix

The repository publishes cross-repository wire fixtures under
[`sync-core/src/test/resources/interop/`](../../sync-core/src/test/resources/interop/README.md).
They pin exact bytes that consumers must decode. They do not cover everything this documentation
describes.

| Area | Machine-checked by the published fixtures | Where |
|---|---|---|
| Payload AEAD: keyset parsing, both modes, associated data, Tink prefix | **yes** | `tink-vectors.json` |
| Blob streaming AEAD: keys, segments, associated data, truncation rejection | **yes** | `streaming-vectors.json` |
| Linking payload encoding | no | prose in [linking.md](linking.md) |
| HTTP endpoints, status codes, headers, precondition semantics | no | prose in [http-api.md](http-api.md) |
| WebSocket frame schema and delivery behavior | no | prose in [websocket.md](websocket.md) |
| Capability tag grammar, limits, authority | no | prose in [capabilities.md](capabilities.md) |
| Module document schemas | no | not documented at this revision |

The published fixtures are crypto vectors only. Module-level fixtures published from this repository
are a planned later phase.

In the other direction, this repository already **consumes** module fixtures published by
[`octi-web`](https://github.com/d4rken-org/octi-web) and
[`octi-desktop`](https://github.com/d4rken-org/octi-desktop): per-module tests decode their committed
document vectors through the production decoders, pinned by
[`fixture-lock.json`](../../fixture-lock.json). That protects this client against those producers'
drift. It does not give a third-party client a fixture to verify against, which is what the matrix
above measures.

Anywhere the matrix says "no", the contract is prose plus the reference implementations. Prose can be
wrong. Report a mismatch instead of assuming the code is right and this document is authoritative, or
the reverse.

## Consuming the published fixtures

The verification chain has to start from something you pinned yourself. Fetching a manifest and
hashing it proves nothing: whatever commit you fetched, its manifest hashes to whatever it hashes to.
The anchor is a digest you recorded out of band.

1. Pin a **full 40-character commit SHA** of this repository **and** the expected SHA-256 of that
   commit's `manifest.json`, together, in your own lockfile. This is what
   [`fixture-lock.json`](../../fixture-lock.json) does for the sources this repository consumes:

   ```json
   {
     "schemaVersion": 2,
     "sources": {
       "d4rken-org/octi": {
         "ref": "<40 hex characters>",
         "manifest_sha256": "<64 hex characters>"
       }
     }
   }
   ```

2. Fetch `sync-core/src/test/resources/interop/` at that SHA.
3. Verify the fetched `manifest.json` against your pinned digest. Abort on mismatch.
4. Verify every file listed in `manifest.files` against the SHA-256 recorded there.
5. Decode and decrypt each vector under its committed keyset, comparing the resulting **plaintext**
   to the recorded plaintext.
6. Reject any `schemaVersion` you do not recognize, in the manifest and in each fixture file. A newer
   schema may add fields whose absence you would silently misread. Failing is correct; guessing is
   not.

Step 1 is the one that is easy to skip and worthless to skip.

## Ciphertext bytes change on every regeneration

Both AES-GCM-SIV and the streaming AEAD use a random nonce per encryption. Regenerating the fixtures
under the very same keyset produces different bytes every time.

Verification therefore **decrypts** and compares plaintext. Never byte-compare a fixture's ciphertext
against your own encryption of the same plaintext; that comparison fails even when both sides are
completely correct.

The practical consequence for producers is that a regeneration always shows up as a diff, so
regenerating without a wire-format reason is pure noise. The keyset itself is preserved across
regenerations by design, and rotating it is a deliberate delete-then-regenerate act, because rotation
breaks every consumer that already pinned the old keyset.

## Change policy by layer

Each layer has a different escape hatch, because each has a different way of reaching a peer.

### HTTP and server API

Changes are **additive**, or they ship under a new endpoint or a new API version.

Device capability tags cannot help here. They describe peers, not the server, and a client learns
about a peer's tags by calling the very API in question. The server's own feature level is discovered
through the API itself, for example the `storageApiVersion` field in
[`GET /v1/account/storage`](http-api.md#get-v1accountstorage), and through the status codes a client
must handle anyway.

Servers are also deployed independently of clients. A client cannot assume that the server it talks
to today is the one it talked to yesterday, and must degrade rather than fail when an endpoint or
field is missing.

### Module document format

There is exactly **one** stored document per (owner device, module id), and every peer on the account
reads that same document. A producer physically cannot emit one format to one peer and a different
format to another. Per-peer dual-write does not exist as an option.

Migrations therefore ship as **one shared backward-compatible representation**: the document carries
the old and the new shape together, so old readers keep reading the fields they know while new readers
prefer the new ones.

Peer capability tags still matter, but for a different question. They do not decide who receives which
shape. They decide **when it is safe to stop writing the old shape**: once every peer that matters
advertises support for the new one, the compatibility fields become dead weight and the old shape can
be dropped. Until then, both are written for everyone.

### Encryption mode

Account-wide and fixed at account creation. It cannot vary per peer and cannot be renegotiated after
the fact. A peer that cannot handle the account's mode is permanently incompatible with that account.
Capability tags exist to surface that condition, not to work around it. See
[encryption.md](encryption.md) and [capabilities.md](capabilities.md).

## What is not promised

No compatibility promise attaches to behavior this documentation does not describe. Observed
behavior that is not written down here, including undocumented fields, undocumented endpoints,
incidental orderings, timing, and the exact wording of error bodies, may change without notice and
without a fixture failing.

If your client depends on something not documented here, that dependency is your risk to carry. The
better move is to ask for it to be specified.

## Further reading

- [Interop fixtures README](../../sync-core/src/test/resources/interop/README.md): the fixture file
  formats, field by field.
- [`.claude/rules/interop-fixtures.md`](../../.claude/rules/interop-fixtures.md): the producer-side
  workflow, regeneration rules, the cross-repository CI gate, and the staged rollout sequence for a
  deliberate wire-format break.
- [`.claude/rules/device-capabilities.md`](../../.claude/rules/device-capabilities.md): the capability
  contract across all four implementations.
