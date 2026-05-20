# Interop Fixtures

App-main is the canonical source of cross-repo wire-format fixtures shared with
[`d4rken-org/octi-web`](https://github.com/d4rken-org/octi-web) and
[`d4rken-org/octi-desktop`](https://github.com/d4rken-org/octi-desktop). The
fixtures pin the exact bytes those clients must decode (or fail decoding) to
guarantee wire compatibility.

## Files

Committed under `sync-core/src/test/resources/interop/`:

| File | Contents |
|---|---|
| `manifest.json` | Schema version, source, sha256 of every other file in this dir |
| `tink-vectors.json` | Payload AEAD vectors (AES-256-GCM-SIV + legacy SIV) with committed keysets |
| `streaming-vectors.json` | Streaming AEAD vectors (blob upload) with committed keyset |

Consumers (`octi-web`, `octi-desktop`) fetch this directory at a pinned app-main
commit SHA, verify each file's sha256 against `manifest.json`, then decode/decrypt
under the committed keysets.

## Two test classes own this

### `InteropFixtureVerifyTest` (always on)

Runs on every `./gradlew :sync-core:testDebugUnitTest`. Asserts:

- `manifest.json` sha256 matches the actual bytes of every other fixture file.
- Every committed ciphertext decrypts to the recorded plaintext under the
  committed keyset + AD.
- Tampered ciphertext + wrong AD + truncated streaming bytes all reject.

If a contributor hand-edits a fixture (or forgets to regenerate the manifest),
this fails.

### `InteropFixtureGeneratorTest` (opt-in, regenerates the fixtures)

Gated by `@EnabledIfSystemProperty(named = "generateInteropFixtures", matches = "true")`,
so a normal `./gradlew test` run **does not** rewrite committed fixtures.

To regenerate:

```bash
./gradlew :sync-core:testDebugUnitTest \
  --tests "*InteropFixtureGeneratorTest*" \
  -DgenerateInteropFixtures=true \
  --rerun-tasks
```

`--rerun-tasks` is required — Gradle would otherwise cache the prior no-op result
because the `-D` flag isn't part of the task's input hashing.

`./gradlew :sync-core:generateInteropFixtures` is a documentation-only wrapper that
prints the above command.

## Why GCM-SIV makes "regen matches committed bytes" impossible

Tink AES-GCM-SIV uses a random nonce per encrypt. Re-running the generator with
the same keyset produces different ciphertext bytes every time. The verify gate
therefore **decrypts** committed ciphertext and compares plaintext — never
re-encrypts and byte-compares.

Practical consequence: regenerating the fixture file always changes the on-disk
bytes (and thus the manifest sha256), even if no wire-format change was intended.
Keep regenerations to actual wire changes or keyset rotations; don't regen
"to keep things fresh."

## Keyset stability

The generator **preserves** an existing committed keyset across regens (it parses
`keysetBase64` out of the existing JSON and reuses it). The keyset only rotates
when the file is missing — i.e. the only way to rotate is to delete the file and
re-run. This is intentional: keyset rotation is a wire-incompatible event for
any system that already pinned the old fixture, and the delete-then-regen flow
makes it deliberate.

## When to regenerate

| Reason | Action |
|---|---|
| Added a new payload or streaming vector to `InteropFixtureGeneratorTest` | Regenerate, commit the result |
| Changed the encrypt/encode wire format (intentional break) | Update encoders, regenerate, regenerate consumer pins in sister PRs |
| Just want fresh ciphertext | **Don't.** Diff noise without signal |

## Cross-repo workflow (consumer side)

See [pull-request-guidelines.md](pull-request-guidelines.md) — sister PRs must
be linked when a wire-format change crosses repos. The web/desktop side keeps a
`fixture-lock.json` that pins this directory by app-main commit SHA, fetches via
sparse-checkout, and verifies against `manifest.json` before running its tests.
