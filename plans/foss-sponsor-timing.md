# Plan: FOSS sponsor unlock — require 10s on sponsors page

## Context

Currently the FOSS upgrade flow unlocks pro immediately on button press (honor system), then opens the browser. Users can tap the button, immediately switch back, and get pro without ever looking at the sponsors page.

The change: when the user returns from the browser in under 10 seconds, don't unlock — instead show a snackbar asking them to take a moment to look at the sponsors page. If they stay 10+ seconds, unlock as before.

## Files to modify

1. **`app/src/foss/java/eu/darken/octi/common/upgrade/core/UpgradeRepoFoss.kt`**
2. **`app/src/foss/java/eu/darken/octi/common/upgrade/ui/UpgradeViewModel.kt`**
3. **`app/src/foss/java/eu/darken/octi/common/upgrade/ui/UpgradeScreen.kt`**
4. **`app/src/foss/res/values/strings.xml`**

## Changes

### 1. `UpgradeRepoFoss.kt` — split launch from unlock

Split `launchGithubSponsorsUpgrade()` into two methods:
- `openSponsorsPage()` — only calls `webpageTool.open(mainWebsite)`
- `unlockUpgrade()` — only writes the `FossUpgrade` to `FossCache`

### 2. `UpgradeViewModel.kt` — add timing logic

- Persist `browserOpenedAt` via `SavedStateHandle` (survives process death)
  - Use `handle.get<Long>("browserOpenedAt")` / `handle.set("browserOpenedAt", millis)`
- `goGithubSponsors()` — saves timestamp to SavedStateHandle, calls `upgradeRepo.openSponsorsPage()`, does NOT call `navUp()`
- `onResumed()` — called by the screen on `ON_RESUME`:
  - Read `browserOpenedAt` from SavedStateHandle; if null → no-op
  - Clear the saved timestamp
  - If elapsed < 10s → emit "too fast" event via `SingleEventFlow`
  - If elapsed >= 10s → call `upgradeRepo.unlockUpgrade()`, call `navUp()`
- Expose `val snackbarEvents: SingleEventFlow<Unit>` for the "too fast" event

### 3. `UpgradeScreen.kt` — add snackbar + lifecycle detection

In `UpgradeScreenHost`:
- Add `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResumed() }`
- Add `LaunchedEffect` collecting `vm.snackbarEvents` → shows snackbar with string resource
- Remove the immediate toast from `onSponsor` — move it to `onResumed()` success path (show toast before navUp)
- Pass `snackbarHostState` to `UpgradeScreen`

In `UpgradeScreen`:
- Add `snackbarHostState` parameter
- Add `snackbarHost = { SnackbarHost(snackbarHostState) }` to `Scaffold`

### 4. `app/src/foss/res/values/strings.xml` — add snackbar string

Add: `upgrade_screen_sponsor_too_fast_msg` — "Back already? Your support keeps Octi alive!"

## Edge cases

- **First ON_RESUME** (screen opens): `browserOpenedAt` is null → no-op, correct
- **Process death**: `browserOpenedAt` survives via `SavedStateHandle`
- **Multiple taps**: Each resets `browserOpenedAt`, user must stay 10s from last tap
- **User returns via back without opening browser**: Unlikely race condition; `browserOpenedAt` guards against it

## Verification

1. Build: `./gradlew assembleFossDebug`
2. Manual test:
   - Open upgrade screen → tap sponsor → quickly switch back → snackbar should appear, NOT unlocked
   - Open upgrade screen → tap sponsor → wait 10s → return → should unlock with toast
   - Verify existing gplay build still compiles: `./gradlew assembleGplayDebug`
