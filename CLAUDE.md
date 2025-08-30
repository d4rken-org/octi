# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building

- `./gradlew assembleDebug` - Build debug APK
- `./gradlew assembleFossDebug` - Build FOSS debug variant
- `./gradlew assembleGplayDebug` - Build Google Play debug variant
- `./gradlew assembleRelease` - Build release APK (all flavors)

### Testing

- `./gradlew testDebugUnitTest` - Run unit tests for debug build
- `./gradlew connectedAndroidTest` - Run instrumented tests on connected device
- `./gradlew test` - Run all unit tests across modules

### Code Quality

- `./gradlew lint` - Run lint checks
- `./gradlew lintDebug` - Run lint for debug build only

### Cleaning

- `./gradlew clean` - Clean all build outputs

## Project Architecture

### Multi-Module Structure

This is a modularized Android application with the following structure:

- **`:app`** - Main application module containing UI components and application-level logic
- **`:app-common`** - Common UI components, themes, and shared resources
- **`:app-common-test`** - Shared test utilities and helpers
- **`:module-core`** - Core module abstractions and interfaces
- **`:sync-core`** - Sync system core implementation
- **`:syncs-gdrive`** - Google Drive sync implementation
- **`:syncs-kserver`** - K-Server sync implementation (end-to-end encrypted)
- **`:modules-*`** - Feature modules for different device information types:
    - `:modules-power` - Battery and power information
    - `:modules-meta` - Device metadata
    - `:modules-wifi` - WiFi connectivity information
    - `:modules-apps` - Installed apps information
    - `:modules-clipboard` - Clipboard synchronization

### Key Technologies

- **Kotlin** - Primary language
- **Android Gradle Plugin** with Kotlin DSL
- **Dagger Hilt** - Dependency injection
- **Android Jetpack Components** - Navigation, WorkManager, etc.
- **Coroutines & Flow** - Asynchronous programming
- **ViewBinding** - View binding for UI
- **Retrofit** - Network communication
- **Moshi** - JSON serialization

### Build Variants

- **FOSS** - Free and open source variant
- **Google Play** - Google Play Store variant with additional features
- Build types: `debug`, `beta`, `release`

### Configuration Files

- **`buildSrc/`** - Contains build configuration and version management
- **`ProjectConfig.kt`** - Centralized project configuration
- **`Versions.kt`** - Dependency version management
- **`version.properties`** - Version number configuration

### Testing Strategy

- Unit tests in `src/test/` directories
- Instrumented tests in `src/androidTest/`
- Custom test runner: `eu.darken.octi.HiltTestRunner`
- Test utilities in `:app-common-test` module

### Module Dependencies

The main app depends on all feature modules and sync implementations. Each module is designed to be self-contained with
minimal cross-dependencies, following clean architecture principles.