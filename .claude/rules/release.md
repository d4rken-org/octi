# Release

Releases are cut via the **Release prepare** workflow (`.github/workflows/release-prepare.yml`). It bumps `version.properties` and `VERSION`, commits to `main`, tags `v<version>`, pushes atomically. The App-token push naturally fires `on: push:` in `release-tag.yml`, which builds, signs, and uploads.

## Dispatch

```bash
# Plan only — no commit, no tag, no push.
gh workflow run release-prepare.yml -f bump_kind=build -f dry_run=true

# Real cut.
gh workflow run release-prepare.yml -f bump_kind=build -f dry_run=false
```

After `dry_run=false`: Job 1 computes + writes the summary, then Job 2 immediately commits/tags/pushes (no env gate — cancel the run between Job 1 and Job 2 if the summary looks wrong; you have ~seconds). The tag push naturally triggers `release-tag.yml` (the App-token push fires `on: push:` workflows; only `GITHUB_TOKEN`-pushes are suppressed). `release-tag.yml` then runs `validate-tag` and the existing `release-github` (`foss-production` approval) + `release-gplay` (`gplay-production` approval) jobs — those are the two human checkpoints, matching the pre-migration UX.

Job 1 also refuses to bump if `main` has failing or pending checks. Fix any CI failures on `main` before dispatching.

**Do NOT dispatch `release-prepare.yml` while a previous tag's `release-github`/`release-gplay` env approvals are still pending.** The per-tag concurrency in `release-tag.yml` only blocks duplicate runs of the same tag, not two different tags simultaneously.

## Inputs

| Input | Default | Notes |
|---|---|---|
| `bump_kind` | `build` | `build` \| `patch` \| `minor` \| `major` |
| `version_type` | `keep-current` | Preserves current `rc`/`beta`. Set explicitly to switch. |
| `version_override` | empty | e.g. `1.0.1-rc0`. Bypasses bump_kind/version_type. |
| `expected_current` | empty | Optional: fail if `version.properties` ≠ this. Useful for tight coordination. |
| `dry_run` | `true` | Default is plan-only. |

Bump rules: `build` increments build; `patch`/`minor`/`major` zero everything to the right of the bumped field. All numeric fields bounded `0..99` (the `versionCode` formula collapses at ≥100).

**Switching from beta to rc** — example: From `1.0.0-beta0` (versionCode 10000000), dispatch with `bump_kind=build`, `version_type=rc`. New version: `1.0.0-rc1` (versionCode 10000010). Note: `1.0.0-rc0` would fail monotonicity because its versionCode equals the current `1.0.0-beta0`. Always increment the build counter when changing type.

## Local

```bash
./tools/release/bump.sh --mode=plan --bump-kind=build --version-type=keep-current
./tools/release/bump.sh --mode=check
bats tools/release/bump.bats
```

## Channel mapping

| Tag suffix | FOSS APK | GitHub release | Fastlane lane | Play track | Rollout |
|---|---|---|---|---|---|
| `-beta*` | `assembleFossBeta` | pre-release | `beta` | `beta` | 10% |
| `-rc*` (or anything else) | `assembleFossRelease` | full release | `production` | **`beta`** | 10% |

`lane :production` in `Fastfile` uploads to Play's **beta** track at 10% — manually promoted to production via Play Console. The lane is intentionally not renamed to preserve historical fastlane invocation surface.

## Rollback

| Stage reached | Steps |
|---|---|
| Bump on `main`, downstream not started | `git push origin :refs/tags/v<bad>`, `git revert <bump-sha>`, push |
| GitHub release created | Above + `gh release delete v<bad> --yes --cleanup-tag` |
| Play upload completed | Above + halt rollout in Play Console (or `bundle exec fastlane supply --track beta --rollout 0 --version-code <bad-code>`) |
| Job 2 ran but downstream rejected at env approval | Treat as first row — bump+tag are public on `main` regardless of downstream outcome |

`bump.sh` enforces strict `versionCode` monotonicity, so re-using a code is impossible without manually editing `version.properties`.

## Auth setup

`release-prepare.yml` Job 2 uses a GitHub App token (not `GITHUB_TOKEN`) to push the bump commit and tag. The App identity is in the rulesets' bypass list, which is what allows the push to bypass branch protection + tag-creation restrictions.

Required org secrets (set on the d4rken-org organization, accessible to `octi`):

- `RELEASE_APP_CLIENT_ID` — Client ID of the `d4rken-org-releaser` GitHub App (visible on the App's settings page, format `Iv1.<hex>` or similar)
- `RELEASE_APP_PRIVATE_KEY` — full `.pem` contents (including BEGIN/END lines)

The App is installed on this repo and added as a bypass actor to:
- The main-branch ruleset (PR + status check requirements)
- The tag ruleset (creation restriction on `v*`)

Other repos in the org can reuse the same App + secrets — just install the App on each repo and add it to that repo's rulesets' bypass lists.

## Defense in depth

`release-tag.yml` includes `validate-tag` which: (1) regex-checks `github.ref_name`, (2) verifies the tag commit is reachable from `origin/main`, (3) runs `bump.sh --mode=check`, (4) asserts the parsed name matches the tag. Hand-pushed tags from feature branches or with mismatched version files fail before any build.

`release-prepare.yml` Job 1 also refuses to bump if `main` has failing or pending checks, preventing orphan tags from broken state.

## Stuck-dispatch recovery

If Job 2's atomic push lands but the natural `on: push:` trigger doesn't fire `release-tag.yml` (rare — would mean GitHub dropped the event), the tag is public on `main` but no pipeline runs. `release-tag.yml` has no `workflow_dispatch` trigger, so the recovery path is:

1. Confirm the tag exists: `gh api repos/d4rken-org/octi/git/refs/tags/v<tag>`
2. If it does and the downstream didn't fire, delete the tag via App bypass and re-push: contact an org admin to temporarily re-run the atomic push step from Job 2 with the same values (or use `git push origin :refs/tags/v<tag>` + `git push origin refs/tags/v<tag>` with App credentials).
3. If that's not feasible, the `release-github` and `release-gplay` jobs can be triggered by creating a new release prepare with the same version forced via `version_override` — but this would bump to a new build counter, producing a new versionCode.

In practice, GitHub dropping `on: push:` events is extremely rare. Check the tag in the repository tags list and verify whether any `release-tag.yml` run appears before taking action.
