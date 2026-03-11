# Code Style

## General Principles

- Package by feature, not by layer
- Prefer adding to existing files unless creating new logical components
- Write minimalistic and concise code
- Don't add code comments for obvious code
- Prefer flow-based reactive solutions
- Cancel-able operations should be implemented for good UX

## Kotlin Conventions

- Add trailing commas for multi-line parameter lists and collections
- When using `if` that is not single-line, always use brackets
- Place `@Suppress` annotations as close as possible to the affected code

## Logging

Use `logTag()` to create tags (prefixed with 🐙) and `log()` with lambda messages:

```kotlin
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.logging.Logging.Priority.*

companion object {
    private val TAG = logTag("Module", "Power", "Sync")
    // Produces: "🐙:Module:Power:Sync"
}

log(TAG) { "Processing $item" }           // DEBUG (default)
log(TAG, INFO) { "Scan complete" }         // INFO
log(TAG, WARN) { "Unexpected state" }      // WARN
log(TAG, ERROR) { "Failed: ${e.asLog()}" } // ERROR with stacktrace
```

## Flow Patterns

- `shareLatest(scope)`: Convert a Flow to a shared hot flow that emits the latest value to new collectors
- `replayingShare(scope)`: Share a flow with replay=1 via `shareIn`
- `setupCommonEventHandlers(tag) { "label" }`: Adds standard logging for flow lifecycle events
- `DynamicStateFlow`: Thread-safe stateful flow with `updateAsync` and `updateBlocking`
- Always chain `.setupCommonEventHandlers(TAG) { "flowName" }` on shared flows

## State Management

Settings use `DataStoreValue` with `createValue()`:

```kotlin
val isOnboardingDone = dataStore.createValue("core.onboarding.done", false)
```

Access with `.flow` (reactive), `value()` (suspend read), `value(newVal)` (suspend write), or `.valueBlocking` (blocking).

## Kotlinx Serialization

- `@Serializable` for data classes (replaces Moshi's `@JsonClass(generateAdapter = true)`)
- `@SerialName("fieldName")` for field mapping (replaces Moshi's `@Json(name = "fieldName")`)
- Inject the project `Json` instance from `SerializationModule` — do not create ad-hoc `Json {}` in production code
- Custom serializers (e.g., `InstantSerializer`, `ByteStringSerializer`) via `@UseSerializers`
- `ByteString` from okio for binary data serialization in sync payloads
- Each module has a `ModuleSerializer<T>` using `json.toByteString()` / `json.fromJson()`
- Navigation routes also use `@Serializable` (same framework)
- Backward-compat tests verify old Moshi wire format compatibility

## Error Handling

- `Bugs.report(exception)`: Report non-fatal errors to crash reporter
- `e.asLog()`: Format exception with stacktrace for logging
- `Throwable.hasCause(ExceptionClass::class)`: Check exception chain
- `Throwable.getRootCause()`: Get the root cause of an exception chain
- `errorEvents: SingleEventFlow<Throwable>` in `ViewModel4` for UI error display

## UI Patterns

- Jetpack Compose with Material 3 components (`Scaffold`, `TopAppBar`, etc.)
- ScreenHost/Screen split: stateful host (collects Flow, injects ViewModel) + stateless screen (pure composable)
- `collectAsState(initial = null)` for Flow → Compose State conversion in hosts
- `hiltViewModel()` for ViewModel injection in Compose
- `ErrorEventHandler(vm)` + `NavigationEventHandler(vm)` in host composables
- `@Preview2` + `PreviewWrapper` for composable previews
- Edge-to-edge display support
- Single Activity architecture with Navigation3 (see [architecture.md](architecture.md#navigation))
