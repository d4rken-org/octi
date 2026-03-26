# Octi Desktop Companion App — Evaluation & Plan

## Context

GitHub issue [#126](https://github.com/d4rken-org/octi/issues/126) requests a desktop version of Octi. Two use cases:
- **guguss-31**: Sync data between phone and laptop (full companion)
- **1s-byte**: Monitor battery of 20 tablets from a laptop (read-only dashboard)

The current codebase is Android-only (`app-main/`) with a Kotlin/JVM sync server (`sync-server/`). Data is E2E encrypted (Google Tink AES256_SIV) and synced via Octi Server or Google Drive.

**Decision**: Build a Compose Multiplatform desktop app (`app-desktop/`) with full bidirectional sync, targeting Windows/macOS/Linux.

---

## WASM: Dropped from MVP

Tink has no WASM/JS target. AES-SIV is not in the Web Crypto API standard. Reimplementing Tink's exact ciphertext framing in JS/WASM is high-risk for a security-critical component. WASM also can't collect device info (no battery/wifi/apps APIs in browsers).

**Decision**: Drop WASM. Revisit if Tink ships a Kotlin/WASM target or if a server-rendered dashboard is added separately.

---

## Phase 0: Shared Protocol Module (`octi-protocol`)

**Before any desktop work**, extract a shared KMP module from `app-main` containing the wire-format contract. This prevents drift between Android and desktop implementations and is the foundation both apps build on.

### What goes into `octi-protocol/`:
- **Data models**: `PowerInfo`, `MetaInfo`, `WifiInfo`, `ConnectivityInfo`, `AppsInfo`, `ClipboardInfo` (strip `Parcelable`)
- **Core sync types**: `DeviceId`, `ModuleId`, `ConnectorId`, `SyncRead`, `SyncWrite` (strip `Parcelable`)
- **Serializers**: `InstantSerializer`, `ByteStringSerializer`, `UUIDSerializer`, `DurationSerializer`
- **Serialization config**: `Json` instance matching current `SerializationModule.kt` config
- **Gzip utilities**: `ByteStringExtensions.kt` (okio, already KMP)
- **Encryption**: `PayloadEncryption` + `PayloadEncryption.KeySet` (strip `Parcelable`, use `expect/actual` for Tink JVM)
- **Octi Server types**: `OctiServer.Address`, `OctiServer.Credentials`, `LinkingData` (strip `Parcelable`)
- **Module interfaces**: `ModuleId`, `ModuleSerializer<T>`, `ModuleInfoSource<T>` (interfaces only)

### What this requires in `app-main`:
- Remove `Parcelable` from core types (they use `@Parcelize` but are only parceled for navigation args, which now uses `@Serializable` anyway)
- Replace `java.time.Instant` with `kotlinx.datetime.Instant` in shared models
- Depend on `octi-protocol` instead of defining these types locally
- `app-main` retains all Android-specific code (Hilt, DataStore, info sources, UI, Retrofit client)

### Wire compatibility safeguards:
- **Golden fixture tests in CI**: Serialize all 6 module data models + `LinkingData` + credentials on both Android and desktop, compare byte-for-byte
- **Cross-platform encryption test**: Android Tink encrypts → Desktop Tink Java decrypts (and vice versa)
- **Preserved typos in `@SerialName`**: `serverAdress`, `currenAvg`, `connectorId` — enforced by tests

**Estimate**: 2-3 person-weeks.

---

## Architecture

### Project Structure

```
octi/
  octi-protocol/                  # NEW: Shared KMP module
    build.gradle.kts              # KMP (jvm target)
    src/
      commonMain/kotlin/eu/darken/octi/protocol/
        sync/                     # DeviceId, ConnectorId, SyncRead, SyncWrite
        encryption/               # PayloadEncryption (expect)
        module/                   # ModuleId, ModuleSerializer, ModuleInfoSource
        modules/{power,meta,wifi,connectivity,apps,clipboard}/  # Data models
        serialization/            # Json config, custom serializers
        collections/              # Okio gzip extensions
        server/                   # OctiServer.Address, OctiServer.Credentials, LinkingData
      jvmMain/                    # PayloadEncryption actual (Tink Java)
      jvmTest/                    # Wire compat tests, encryption compat tests

  app-desktop/                    # NEW: CMP desktop app
    build.gradle.kts
    src/
      commonMain/kotlin/eu/darken/octi/desktop/
        common/
          coroutine/              # DispatcherProvider, AppScope
          debug/logging/          # log(), logTag() (println-based, with redaction policy)
          flow/                   # DynamicStateFlow, shareLatest, replayingShare
        sync/
          core/                   # SyncConnector, SyncManager, ConnectorHub
          cache/                  # SyncCache (atomic file writes)
        module/
          core/                   # BaseModuleSync, BaseModuleRepo, BaseModuleCache, ModuleManager
        syncs/octiserver/core/    # OctiServerHttpClient (Ktor), OctiServerEndpoint, OctiServerConnector, OctiServerHub, OctiServerAccountRepo
        di/                       # AppGraph (manual DI)
        ui/
          navigation/             # Screen sealed class, Navigator
          theme/                  # Material 3 theme
          dashboard/              # DashboardScreen
          device/                 # DeviceDetailScreen
          linking/                # LinkDeviceScreen (with validation + expiry handling)
          settings/               # SettingsScreen
      jvmMain/kotlin/eu/darken/octi/desktop/
        modules/
          power/                  # PowerInfoSource (Linux/macOS/Windows)
          meta/                   # MetaInfoSource
          wifi/                   # WifiInfoSource
          connectivity/           # ConnectivityInfoSource
          apps/                   # AppsInfoSource
          clipboard/              # ClipboardInfoSource (AWT)
        platform/                 # PlatformDetector, ProcessRunner (with timeouts)
        security/                 # OS keystore integration (Keychain/DPAPI/libsecret)
        main.kt
      jvmTest/

  app-main/                       # EXISTING: Android app (depends on octi-protocol)
  sync-server/                    # EXISTING: Ktor sync server
```

### Key Technology Choices

| Concern | Android (`app-main`) | Desktop (`app-desktop`) |
|---------|---------------------|------------------------|
| UI | Jetpack Compose | Compose Multiplatform |
| Navigation | Navigation3 | State-machine (sealed class + MutableStateFlow) |
| DI | Hilt | Manual (`AppGraph` factory class) |
| HTTP | OkHttp + Retrofit | Ktor Client (JVM engine) |
| Encryption | Tink Android | Tink Java (`tink:1.16.0`) |
| Settings | AndroidX DataStore | File-backed JSON (platform-canonical paths) |
| Secrets | Android Keystore | OS keystore (Keychain / DPAPI / libsecret) |
| Shared models | `octi-protocol` | `octi-protocol` |

### Octi Server Client (Ktor)

Rewrite `OctiServerApi` (14 Retrofit endpoints) as an `OctiServerHttpClient` using Ktor:
- `ContentNegotiation` plugin with `kotlinx.serialization.json`
- `Auth` plugin with `BasicAuthProvider`
- `WebSockets` plugin for push notifications
- Same REST endpoints: register, createShareCode, getDeviceList, readModule, writeModule, deleteDevice, resetDevices

**Golden response testing**: Record Retrofit responses from the Android client as test fixtures. Verify Ktor client produces identical requests and parses identical responses.

### Encryption

Google Tink `tink-java` (not `tink-android`) on JVM desktop. Same `DeterministicAead` / `AES256_SIV`. Defined in `octi-protocol` as `expect/actual`.

### Secret Storage

Credentials (account ID, device password) and encryption keysets stored in OS keystore:
- **macOS**: Keychain Services via `security` CLI or JNA
- **Linux**: libsecret / Secret Service D-Bus API via `secret-tool` CLI
- **Windows**: DPAPI via JNA (`CryptProtectData`)

Non-secret settings (sync interval, device label, UI preferences) stored in platform-canonical config directories with atomic writes (temp file + fsync + rename) and file locking:
- **Linux**: `$XDG_CONFIG_HOME/octi/settings.json` (defaults to `~/.config/octi/`)
- **macOS**: `~/Library/Application Support/octi/settings.json`
- **Windows**: `%APPDATA%\octi\settings.json`

Data/cache files follow the same convention (`$XDG_DATA_HOME/octi/`, etc.).

### Device Linking

Desktop can't scan QR codes. Flow:
1. Android app generates `LinkingData` encoded string (base64-gzipped JSON)
2. User copies text and pastes into desktop app's "Link Device" screen
3. **Staged validation**: invalid base64 → invalid gzip → invalid JSON → expired/consumed share code, each with a clear error message
4. Desktop decodes → calls `linkToExistingAccount(linkCode)` → stores credentials in OS keystore
5. **Share code expiry**: Show explicit "code expired (60min limit)" error with instruction to regenerate on Android

### WebSocket Push Notifications

Persistent WebSocket connection to Octi Server at `/v1/ws`:
- Parse `SyncNotifier.EventPayload` frames
- On `module_changed` → targeted sync for changed module/device
- **Reconnection state machine**: jittered exponential backoff (1s → 2s → 4s → ... → 60s cap)
- Handle `401` (re-auth), `404` (device deleted), network switch (reconnect)
- **Fallback polling**: If WS fails 3 consecutive times, fall back to periodic polling (configurable interval, default 60s)
- Observable connection state in UI (connected / reconnecting / polling fallback)

### Conflict Semantics

Bidirectional writes use **last-write-wins** (same as current Android-to-Android sync):
- Power, Meta, WiFi, Connectivity, Apps: each device writes its own data only — no conflict possible
- **Clipboard**: Both devices can write. Last write wins. UI shows source device + timestamp so user knows which clipboard is active

### Scale Handling (20+ Devices)

For the manufacturing tablet monitoring use case:
- Octi Server rate limit: 512 req / 60s. With 20 devices x 6 modules, a full sync = 120 reads. Budget: ~4 full syncs per minute.
- **Sync cadence**: On WS push notification → targeted single-module read (1 request, not full sync). Periodic full sync every 5 minutes as fallback.
- Handle `429 Too Many Requests`: exponential backoff, prioritize power module reads
- Handle `413 Payload Too Large`: Apps module on tablets with 200+ apps could exceed 128KB. Truncate app list if needed.

### Platform Info Sources (JVM)

| Module | Linux | macOS | Windows | Cross-platform |
|--------|-------|-------|---------|---------------|
| **Power** | `/sys/class/power_supply/BAT*/` | `pmset -g batt` | WMI `Win32_Battery` / JNA | — |
| **WiFi** | `nmcli -t -f SSID,SIGNAL dev wifi` | `airport -I` | `netsh wlan show interfaces` | — |
| **Meta** | — | — | — | `System.getProperty()`, hostname |
| **Connectivity** | — | — | — | `NetworkInterface.getNetworkInterfaces()` |
| **Apps** | `dpkg -l` / `rpm -qa` / `flatpak list` | `/Applications` + `Info.plist` | Registry `Uninstall` key | — |
| **Clipboard** | — | — | — | `java.awt.Toolkit.systemClipboard` |

**Hardening:**
- Use machine-readable CLI outputs where available (e.g., `nmcli -t` terse mode, `pmset -g batt` structured output)
- Set `LC_ALL=C` when invoking CLI tools to avoid locale-dependent output
- Command timeout: 5s per invocation, fail gracefully with partial/empty data
- Each platform collector isolated — failure on one module doesn't affect others
- Desktop PCs without battery/WiFi report "not available" (not an error)

### Security & Logging Policy

- **Never log**: encryption keysets, decrypted payloads, device passwords, link codes, full `LinkingData`
- **Redact in logs**: truncate device IDs to first 8 chars, mask account IDs
- `log(TAG)` pattern ported from Android but with `println`-based backend + redaction filter

### Local Persistence

- **Atomic writes**: All file writes use temp file → fsync → atomic rename
- **File locking**: `FileLock` on settings and cache files to prevent corruption from multiple instances
- **Schema versioning**: JSON files include a `schemaVersion` field; migration logic on read

---

## Effort Estimate

| Phase | Scope | Estimate |
|-------|-------|----------|
| **Phase 0**: Shared protocol | Extract `octi-protocol` KMP module, remove Parcelable from `app-main` core types, golden fixture CI tests | 2-3 person-weeks |
| **Phase 1**: Read-only viewer | Project setup, Ktor Octi Server client, link flow, Dashboard UI (view all devices + modules), OS keystore integration | 8-10 person-weeks |
| **Phase 2**: Basic bidirectional sync | Meta + Clipboard + Connectivity info sources, write path, WebSocket push with reconnection state machine | 3-4 person-weeks |
| **Phase 3**: Full info collection + packaging | Power + WiFi + Apps info sources (3 OS each), Settings screen, system tray, .msi/.dmg/.deb packaging, CI pipeline | 5-7 person-weeks |
| **Total (Phases 0-3)** | | **18-24 person-weeks** |
| *Phase 4 (deferred)*: GDrive support | OAuth browser redirect flow, Drive REST API client (Ktor), token management + secure storage, `appDataFolder` access | ~2-3 person-weeks |

Phase 0 + Phase 1 deliver the highest value: seeing all phone/tablet data on desktop.

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Wire format drift | High | Eliminated by `octi-protocol` shared module + golden fixture CI tests |
| Tink keyset incompatibility | High | Cross-platform encrypt/decrypt integration tests in `octi-protocol` |
| Removing Parcelable breaks app-main | Medium | Parcelable on core types is vestigial (navigation now uses `@Serializable`). Verify with full Android test suite. |
| Platform info source fragility | Medium | `LC_ALL=C`, command timeouts, graceful fallback to "unsupported" state |
| Secret storage cross-platform | Medium | Keychain/DPAPI/libsecret all have different failure modes. Fallback to encrypted file if keystore unavailable. |
| 20+ device scale | Medium | WS-driven targeted sync, rate limit backoff, Apps module payload truncation |
| CMP desktop maturity | Low | Production-ready for JVM. JetBrains ships Fleet on it. |
| Maintenance burden | Medium | Shared protocol module eliminates model drift. ~2-4 dev-days/month ongoing. |

---

## Verification

- **Wire compatibility**: Golden fixture tests in `octi-protocol` CI — serialize/deserialize all models cross-platform
- **Encryption compat**: Cross-platform encrypt/decrypt tests (Tink Android <-> Tink Java)
- **Ktor vs Retrofit parity**: Record Android Retrofit responses as fixtures, verify Ktor client matches
- **E2E sync test**: Run Octi Server locally, link Android emulator + desktop app, verify bidirectional data flow
- **Error matrix**: invalid link text, expired share code, `401/404/413/429` HTTP responses, malformed `X-Modified-At` header
- **Scale test**: 20 simulated devices against local Octi Server, measure p95 refresh latency
- **Platform info sources**: Manual testing on Linux VM, macOS, Windows VM
- **Packaging**: Install .msi/.dmg/.deb on clean systems

---

## Phase 4 (Deferred): GDrive Support

**Status**: Deferred — not implemented directly, but kept in mind during architecture and design.

Google Drive's `appDataFolder` is scoped per GCP project, not per platform. A desktop app using an OAuth client from the same GCP project can access the same data as the Android app.

**What's needed when the time comes:**
- Register "Desktop application" OAuth client ID in existing GCP console project
- OAuth 2.0 authorization code flow with `http://localhost:PORT` redirect URI
- `drive.appdata` scope (same as Android)
- Drive REST API via Ktor client (no Google Play Services SDK needed)
- Token refresh + secure storage in OS keystore
- Only applicable for `gplay` flavor users

**Architectural consideration**: The `octi-protocol` module and `SyncConnector` interface should be designed generically enough that adding a `GDriveConnector` alongside `OctiServerConnector` is additive, not a refactor. The `ConnectorHub` pattern from Android (injected via `@IntoSet`) translates to an explicit list in the desktop app's `AppGraph`.
