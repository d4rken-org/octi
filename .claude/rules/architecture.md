# Architecture

## Module Structure

### Core Application
- `app`: Main application module with Compose UI, navigation, application-level logic, and custom ViewModel hierarchy
- `app-common`: Shared utilities, base architecture components, Flow extensions, theming
- `app-common-test`: Testing utilities, base test classes, and helpers for all modules

### Sync Backends
- `sync-core`: Core sync abstractions (`SyncManager`, `SyncConnector`, `ConnectorHub`, `SyncRead`, `SyncWrite`)
- `syncs-gdrive`: Google Drive sync implementation
- `syncs-kserver`: K-Server sync implementation (end-to-end encrypted)

### Module System
- `module-core`: Module abstractions and base classes (`BaseModuleSync`, `BaseModuleRepo`, `BaseModuleCache`, `ModuleManager`)
- `modules-power`: Battery and power information
- `modules-meta`: Device metadata
- `modules-wifi`: WiFi connectivity information
- `modules-connectivity`: Connectivity/network information
- `modules-apps`: Installed apps information
- `modules-clipboard`: Clipboard synchronization

## Sync System Architecture

Data flows through: `SyncConnector` → `ConnectorHub` → `SyncManager`

- `SyncConnector`: Interface for sync backend operations (read/write/sync)
- `ConnectorHub`: Groups connectors by backend type (GDrive, KServer), injected via `@IntoSet`
- `SyncManager`: Central coordinator that combines all `ConnectorHub` instances, manages connector state and data flow

## Module System Architecture

`ModuleRepo` combines data from `ModuleInfoSource` (local), `ModuleSync` (remote), and `ModuleCache` (persisted)

- `ModuleInfoSource<T>`: Collects device data locally (e.g., battery level)
- `BaseModuleSync<T>`: Syncs data via `SyncManager`, serializes/deserializes using `ModuleSerializer<T>` with `ByteString`
- `BaseModuleRepo<T>`: Combines local + synced data using `DynamicStateFlow`, provides unified `state: Flow<State<T>>`
- `BaseModuleCache<T>`: Persists module data to disk using kotlinx.serialization + file I/O
- `ModuleManager`: Aggregates all `ModuleRepo` instances (injected via `@IntoSet`), provides `byModule` and `byDevice` views

## MVVM with Custom ViewModel Hierarchy

- `ViewModel1` → `ViewModel2` → `ViewModel4`
- `ViewModel4` adds navigation (`NavigationEventSource`) and error handling (`ErrorEventSource`)
- Uses `SingleEventFlow` for one-shot navigation and error events
- All ViewModels use `@HiltViewModel` with `@Inject constructor`
- Compose screens use `ErrorEventHandler(vm)` and `NavigationEventHandler(vm)` composables to observe events

## Dependency Injection

- Hilt/Dagger throughout the application
- `@AndroidEntryPoint` for Activities
- `@HiltViewModel` for ViewModels
- `@AppScope` for application-scoped coroutines
- `@IntoSet` for collecting `ConnectorHub`, `ModuleRepo`, and `NavigationEntry` implementations
- `@Singleton` for core services

## Navigation

- Single Activity architecture with Compose `setContent {}`
- Navigation3 runtime-based navigation (`NavDisplay`, `rememberNavBackStack`)
- Sealed interface destinations in `Nav.kt` with `@Serializable`, grouped under `Nav.Main`, `Nav.Sync`, `Nav.Settings`
- `NavigationEntry` pattern: each screen registers via `@IntoSet`
- `navTo(destination)` / `navUp()` from `ViewModel4`; `navTo` also supports `popUpTo`/`inclusive` for back-stack manipulation
- `NavigationEventHandler(vm)` composable in screen hosts
