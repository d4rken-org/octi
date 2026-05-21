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

## Upstream gating (this repo's CI)

`.github/workflows/cross-repo-verify.yml` runs on every PR. On PRs that touch the
allowlisted wire-format paths (the crypto layer, this fixture dir, and the
shared serialization module — see `ALLOWLIST` in the workflow), it checks out
`octi-web` and `octi-desktop` at their default branches and runs their test
suites against the PR's HEAD using the `INTEROP_FIXTURE_OVERRIDES` env var the
consumer sync scripts already accept. A wire-incompatible change is blocked at
the producer PR, not discovered weeks later when a consumer happens to bump its
pin.

The allowlist today covers what the **committed fixtures actually exercise**:
the crypto vectors under `sync-core/src/test/resources/interop/`. Module-level
fixtures (Phase B/C) will expand it. Don't widen prematurely — over-firing
turns into "every PR pays the cross-repo CI cost for changes that can't
actually break consumers."

PRs that don't touch the allowlist still run the workflow but echo "skip: no
relevant paths changed" and exit 0 — required-check status reports green
without leaving the check pending.

## Consuming other repos' fixtures (Phase B onward)

App-main also **consumes** fixtures published by `d4rken-org/octi-web` (and, in
Phase C, `d4rken-org/octi-desktop`). The other side of the same bidirectional
contract: producer-side serializer drift breaks the gate at the producer's PR,
because every consumer's CI runs against the producer's HEAD via the
`INTEROP_FIXTURE_OVERRIDES` env var.

### Pinning

`fixture-lock.json` at repo root pins each upstream source:

```json
{
  "schemaVersion": 2,
  "sources": {
    "d4rken-org/octi-web": {
      "ref": "<40-char commit SHA>",
      "manifest_sha256": "<sha256 of that source's manifest.json>"
    }
  }
}
```

Schema is v2 — multi-source from day one so Phase C can add `d4rken-org/octi-desktop`
without a migration step.

### Test layout

- **Shared sync helper**: `app-common-test/src/main/java/testhelpers/interop/`
  (`InteropFixtureSync`, `SyncRefResolver`, schemas). Lives there because
  `modules-meta`, `modules-clipboard`, `modules-files` depend on `sync-core`
  and each module's consumer test needs to import its own `*Info` model.
- **Per-module consumer tests**: `modules-{meta,clipboard,files}/src/test/.../interop/Web*InteropTest.kt`.
  Each calls `InteropFixtureSync.ensureSynced("d4rken-org/octi-web")` in
  `@BeforeAll`, reads the relevant `octi-web-<module>.json` from the cache,
  and decodes every `payloadJson` through its production decoder with
  field-by-field assertions.

### Cache

`.cache/interop-fixtures/<owner>/<repo>/<sha>/` — gitignored. Owner+repo in
the path prevents collisions when multiple sources land. Marker file `.sha`
written last; a cache that's missing it (or whose marker mismatches `lock.ref`)
gets re-fetched on the next run.

### CI override

`INTEROP_FIXTURE_OVERRIDES` is a JSON map `{ "<owner>/<repo>": "<sha40>" }`. When
set, the matching `lock.sources[source].manifest_sha256` is **dropped** as a
trust anchor — there's no committed sha that could pin an arbitrary upstream
commit. Per-file sha256 inside the fresh manifest becomes the sole anchor for
that run. Used by the consumer-side workflow in each producer repo
(`octi-web/.github/workflows/cross-repo-verify.yml` in Phase B4) to point at a
PR HEAD SHA without rewriting `fixture-lock.json`.

The env var is declared as a Gradle task input on each consuming module's
test task so an overridden run can't be UP-TO-DATE skipped.

### Bumping the pin

```bash
# 1. Pick the new upstream commit SHA.
# 2. Fetch its manifest.json and sha256 it.
curl -sSL https://raw.githubusercontent.com/d4rken-org/octi-web/<sha>/src/__interop__/published/manifest.json \
  | sha256sum
# 3. Edit fixture-lock.json with both values.
# 4. Run the module test suites — re-fetch happens automatically.
./gradlew :modules-meta:testDebugUnitTest :modules-clipboard:testDebugUnitTest :modules-files:testDebugUnitTest
```

Cache misses force a fresh fetch; the lockfile's `manifest_sha256` is the
single-file trust anchor for that fetch.

## Breaking a wire format on purpose

The cross-repo gate makes "land producer change first, consumers later"
impossible on its own. Two ways to break wire format intentionally:

### 1. Coordinated sister PRs (default)

Open the consumer PRs first (or simultaneously). Each includes the matching
decoder change. The producer PR then merges because the consumers' default
branches already have the updated decoders when the gate fetches them. The
[pull-request-guidelines.md](pull-request-guidelines.md) sister-repo section
covers the mechanics. Right tool for renames, additions of required fields, or
new value-class shapes.

### 2. Staged via capability flag

For changes the consumers can't absorb synchronously (a serializer refactor on
one side, a Tink upgrade requiring blob re-encoding, etc.), don't break — add a
new format alongside the old and let consumers migrate on their own schedule.
Full sequence:

1. **Producer commits a new fixture vector** exercising the new shape, alongside
   the existing one. Don't remove the old vector — the gate still requires
   consumers to decode it.
2. **Each consumer adds decode support** for the new shape (their own PR;
   gate passes because the old vector still works AND the new vector is just
   "yet another decodable thing").
3. **Producer advertises a new capability tag** in `Octi-Device-Capabilities`
   (see [device-capabilities.md](device-capabilities.md)). Consumers reading
   peers' capabilities now know "peer X has the new format."
4. **Producer adds dual-write**: emit old format unconditionally, emit new
   format only for peers whose capabilities include the new tag.
5. **Support floor moves** — once every peer that matters has advertised the
   capability, the old format becomes dead code.
6. **Producer removes the old write path** and the old fixture vector. This
   step is the actual breaking change; by now, no peer in the network reads
   the old format anyway.

Steps 1–4 are the rollout; step 5 is calendar time; step 6 is a normal PR.
None of this requires bypassing the gate. The fixture vector contract is what
keeps producers honest: you can only remove the old vector once consumers have
agreed (via their decoders) they no longer need it.

### Bypass (emergency only)

Admin-merge of the required check exists in branch protection settings. Don't
use it for normal cross-repo work — it skips the safety the gate provides.
Reserve for "the gate itself is broken" emergencies (e.g. GitHub Actions
outage, a runner image issue, raw.githubusercontent.com unreachable from
ubuntu-22.04). When using it, file a follow-up to fix whatever made the gate
unavailable.
