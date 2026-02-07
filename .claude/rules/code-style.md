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

## Moshi Serialization

- Use `@JsonClass(generateAdapter = true)` for data classes
- Use `@Json(name = "fieldName")` for field mapping
- `ByteString` from okio for binary data serialization in sync payloads
- Each module has a `ModuleSerializer<T>` for encoding/decoding module data

## Error Handling

- `Bugs.report(exception)`: Report non-fatal errors to crash reporter
- `e.asLog()`: Format exception with stacktrace for logging
- `Throwable.hasCause(ExceptionClass::class)`: Check exception chain
- `Throwable.getRootCause()`: Get the root cause of an exception chain
- `errorEvents: SingleLiveEvent<Throwable>` in `ViewModel3` for UI error display

## UI Patterns

- XML layouts with ViewBinding for UI components
- Material 3 theming and design system
- Edge-to-edge display support
- Single Activity architecture with Fragment-based navigation
- RecyclerView with modular adapter pattern: each list item has its own `VH` (ViewHolder) class with nested `Item` data class
