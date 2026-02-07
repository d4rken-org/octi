# Octi

Multi-device companion app for Android. Syncs device information (battery, WiFi, apps, clipboard, metadata) across devices via Google Drive or K-Server (end-to-end encrypted).

- **Package**: `eu.darken.octi`
- **Architecture**: Modularized Android app with Kotlin, Hilt DI, Coroutines/Flow, ViewBinding
- **Build flavors**: `foss` (open source) / `gplay` (Google Play) × `debug` / `beta` / `release`

## Rules

- [Architecture](rules/architecture.md) — Module structure, sync system, module system, MVVM hierarchy, DI, navigation
- [Code Style](rules/code-style.md) — Kotlin conventions, logging, Flow patterns, state management, serialization, error handling
- [Testing](rules/testing.md) — JUnit 5, BaseTest, MockK, Kotest matchers
- [Build Commands](rules/build-commands.md) — Gradle commands, variants, version management, dependencies
- [Localization](rules/localization.md) — String resources, naming conventions, Crowdin
- [Commit Guidelines](rules/commit-guidelines.md) — Commit message format and examples
- [Agent Instructions](rules/agent-instructions.md) — How to add modules/sync backends, common mistakes
