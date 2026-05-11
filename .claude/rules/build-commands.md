# Build Commands

## Building the Project

```bash
# Build debug APK (all flavors)
./gradlew assembleDebug

# Build specific flavor
./gradlew assembleFossDebug
./gradlew assembleGplayDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean
```

## Testing

```bash
# Run unit tests for debug build
./gradlew testDebugUnitTest

# Run all unit tests
./gradlew test

# Run instrumented tests on connected device
./gradlew connectedAndroidTest
```

## Screenshots

```bash
# Default — smoke set (6 locales × 6 screens). Use this for local iteration
# and PRs that touch screenshot content.
./fastlane/generate_screenshots.sh --smoke --clean
./fastlane/copy_screenshots.sh --clean

# Full run — all 30 locales. Use only when intending to upload to Play Store
# (non-smoke output is .gitignored and should not be committed).
./fastlane/generate_screenshots.sh --clean
./fastlane/copy_screenshots.sh --clean

# Upload screenshots only
bundle exec fastlane screenshots_only
```

### Commit policy

Only the 6 smoke locales (en-US, de-DE, ja-JP, ar, zh-CN, pt-BR) have `phoneScreenshots/*.png` checked into the repo. Non-smoke locales are excluded by `.gitignore`. Play Store's `supply` retains previously-uploaded screenshots for locales not pushed, so full localization is maintained by an **occasional manual** full regen + `screenshots_only` upload — not by every PR.

## Code Quality

```bash
# Run lint checks
./gradlew lint
./gradlew lintDebug
```

## Build Variants

- **Flavors**: `foss` (open source), `gplay` (Google Play with additional features)
- **Build types**: `debug`, `beta`, `release`

## Version Management

- `version.properties`: Source of truth for version numbers (major.minor.patch.build)
- `buildSrc/src/main/java/ProjectConfig.kt`: Reads `version.properties`, defines `packageName`, SDK versions
- Version format: `{major}.{minor}.{patch}-{type}{build}` where `type ∈ {rc, beta}`
- Bumping versions: dispatch `.github/workflows/release-prepare.yml` (do NOT run any local script)
- SDK versions (`minSdk`, `compileSdk`, `targetSdk`) are defined in `buildSrc/src/main/java/ProjectConfig.kt`

## Dependency Management

- `buildSrc/src/main/java/Versions.kt`: Centralized dependency version management
- `buildSrc/build.gradle.kts`: Build plugin versions
- When updating Kotlin or core dependencies, update both `Versions.kt` and `buildSrc/build.gradle.kts`

## Context Management

When running gradle builds or tests, use the Task tool with a sub-agent to keep verbose output isolated from the main conversation context. The sub-agent should report back only:
- Success or failure
- Compilation errors with file paths and line numbers
- Warning counts

Run gradle directly in the main context only when the user explicitly requests full output.
