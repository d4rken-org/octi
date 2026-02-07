# Agent Instructions

## Core Principles

- Read existing code before making changes
- Follow existing patterns — don't introduce new paradigms
- Verify changes compile with `./gradlew assembleDebug`
- Run tests with `./gradlew testDebugUnitTest` after modifications
- Use the Task tool to delegate suitable tasks to sub-agents
- Keep context focused — use sub-agents for verbose gradle output

## Adding a New Feature Module

1. Create the module directory (e.g., `modules-newfeature/`)
2. Add `build.gradle.kts` based on an existing module (e.g., `modules-power`)
3. Register the module in `settings.gradle.kts`
4. Create data class (e.g., `NewFeatureInfo`) implementing the module's data contract
5. Create `NewFeatureInfoSource` implementing `ModuleInfoSource<NewFeatureInfo>`
6. Create `NewFeatureSerializer` implementing `ModuleSerializer<NewFeatureInfo>`
7. Create `NewFeatureSync` extending `BaseModuleSync<NewFeatureInfo>`
8. Create `NewFeatureRepo` extending `BaseModuleRepo<NewFeatureInfo>` — bind via `@IntoSet` as `ModuleRepo`
9. Create `NewFeatureCache` extending `BaseModuleCache<NewFeatureInfo>`

Register the module ID in `ModuleId` and provide Hilt bindings using `@IntoSet`.

## Adding a New Sync Backend

1. Create a new module (e.g., `syncs-newbackend/`)
2. Implement `SyncConnector` interface
3. Implement `ConnectorHub` interface — bind via `@IntoSet` as `ConnectorHub`
4. The `SyncManager` will automatically discover it through `@IntoSet` injection

## Common Mistakes to Avoid

- **Don't use SharedPreferences** — use `DataStoreValue` via `DataStore<Preferences>.createValue()`
- **Don't use `GlobalScope`** — inject `@AppScope CoroutineScope` instead
- **Always extend `ViewModel3`** for ViewModels (not `ViewModel` or `AndroidViewModel`)
- **Always call `setupCommonEventHandlers`** on shared flows for consistent logging
- **Don't forget trailing commas** in multi-line parameter lists
- **Use `logTag()`** for log tags — it adds the 🐙 prefix automatically

## Exploring vs Implementing

- **Exploring**: Use read-only tools, gather context, understand patterns
- **Implementing**: Make minimal, focused changes based on exploration
- Don't mix exploring and implementing in the same step
- When uncertain, explore first
