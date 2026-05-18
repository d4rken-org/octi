# Device Capabilities

Per-peer feature-capability negotiation. Each Octi client (Android, desktop, web) publishes a
**set of capability tags** describing what features it supports; consumers read this set instead
of inferring capability from version strings.

Shipped in [octi#309](https://github.com/d4rken-org/octi/pull/309) (Android side) and
[octi-server#23](https://github.com/d4rken-org/octi-server/pull/23) (server side).

## Wire contract (cross-platform)

The contract is shared by **all four** implementations. Drift here breaks interop.

### Tag format

- `<namespace>:<value>` — ASCII, lowercase namespace.
- Regex: `[a-z][a-z0-9]*:[A-Za-z0-9._\-]+`
- Marker convention: `<namespace>:_reported` — emitted by any producer that participates in a
  namespace. Distinguishes "I don't speak this namespace" (no marker) from "I speak it and
  explicitly don't support this value" (marker present, value absent).

### Limits

| Limit | Value |
|---|---|
| Max tags per device | 64 |
| Max length per tag | 128 chars |
| Max header byte length | 4096 |

Validators in every implementation enforce these. **On any bad tag the whole set is rejected**
— no partial acceptance. This keeps the "either valid or absent" contract consistent across
producers and consumers.

### Wire transport

| Connector | Outbound | Inbound |
|---|---|---|
| OctiServer | `Octi-Device-Capabilities` HTTP header on any authenticated state-updating request | JSON array on each device in `GET /v1/devices` (proper array, not stringified) |
| GDrive | `capabilities` field on `_device.json` manifest | Same field, read on sync |

Both transports use the same `<namespace>:<value>` tag shape. The header is JSON-stringified
`Array<string>`; the GDrive manifest field is a JSON array element.

### Authority semantics

For a peer's `capabilities` field, with namespace `X` and value tag `<X>:V`:

| State | Verdict |
|---|---|
| `capabilities == null` | Unknown — caller falls back to version heuristic (Android only) or skips |
| `capabilities` non-null, `<X>:_reported` absent | Namespace `X` unknown for this peer — caller falls back |
| `capabilities` non-null, `<X>:_reported` present, `<X>:V` absent | Peer explicitly does NOT support `V` (known-unsupported) |
| `capabilities` non-null, `<X>:_reported` present, `<X>:V` present | Peer explicitly DOES support `V` (known-supported) |

### Server stale-state policy

The sync-server preserves the previously-stored capability set when an authenticated request
arrives without the `Octi-Device-Capabilities` header — same pattern as `Octi-Device-Version` /
`Octi-Device-Platform` / `Octi-Device-Label`. There is **no downgrade clearing** today; if it
becomes a real problem, [octi-server#23](https://github.com/d4rken-org/octi-server/pull/23)'s
follow-up note discusses options.

### Trust model

Capabilities live on `DeviceMetadata` (server-visible / GDrive-manifest-visible), **not** on
the E2E-encrypted `MetaInfo`. This was a deliberate choice — the encryption-compat check
needs to predict decryptability *before* trying to decrypt, so the capability signal must be
available pre-decryption. The trade-off is that capabilities are advisory: a hostile substrate
could rewrite the tag set to suppress warnings, but real incompatibilities still surface at
decrypt time. See the KDoc on `DeviceMetadata.capabilities` for the documented trust model.

## Currently defined namespaces

### `encryption`

Values are `EncryptionMode.typeString`:

- `encryption:AES256_GCM_SIV` — peer can read/write AES-256-GCM-SIV (the modern mode).
- `encryption:AES256_SIV` — peer can read/write AES-256-SIV (the legacy mode).

Marker: `encryption:_reported`.

**Android-specific note**: when a peer reports `capabilities == null`, the encryption-compat
check falls back to the Android-version heuristic
(`OctiServerEncryptionCompat.MIN_GCM_SIV_CLIENT_VERSION`) for `platform == "android"` peers
only. Non-Android peers without capabilities are skipped (see
[octi#308](https://github.com/d4rken-org/octi/pull/308)).

## Where the code lives (this repo)

| File | Role |
|---|---|
| `sync-core/.../Capability.kt` | Tag registry + per-namespace `supports<X>()` semantic helpers |
| `sync-core/.../CapabilitiesCodec.kt` | Shared encode/decode + validation. Single source of truth for tag-format hygiene. |
| `sync-core/.../DeviceCapabilitiesProvider.kt` | Computes the **local** Android device's tag set from injected sources (`CryptoCapabilities` today). |
| `sync-core/.../DeviceMetadata.kt` | `capabilities: Set<String>?` field on the per-peer metadata type |
| `syncs-octiserver/.../DeviceHeaderInterceptor.kt` + `DeviceHeaderValuesProvider.kt` | Sends the header on every authenticated OctiServer request |
| `syncs-octiserver/.../OctiServerApi.kt` | DTO stores response capabilities as `JsonElement?` (per-device decode resilience) |
| `syncs-octiserver/.../OctiServerEndpoint.kt` | Maps DTO → `DeviceMetadata.capabilities` via the codec |
| `syncs-octiserver/.../OctiServerEncryptionIssues.kt` | The one consumer: gates `EncryptionCompatibilityIncompatible` |
| `syncs-gdrive/.../GDriveDeviceInfo.kt` + `GDriveAppDataConnector.kt` | GDrive manifest read/write |

Tests: `CapabilityTest`, `CapabilitiesCodecTest`, `OctiServerEncryptionIssuesTest`,
`DeviceHeaderInterceptorTest`, `GDriveAppDataConnectorSyncTest`,
`CrossVersionLegacyServerTest`.

## How to add a new capability namespace

Use `encryption:*` as the worked example. To add namespace `X` with value type `T`:

1. **`Capability.kt`**:
   - Add `const val X_NAMESPACE_REPORTED = "x:_reported"`.
   - Add `fun x(value: T): String = "x:${value.toWireString()}"`.
   - Add `fun supportsX(caps: Set<String>?, value: T): Boolean?` following the encryption
     pattern: returns `null` for unknown (caps null OR marker absent), `true`/`false` otherwise.

2. **`DeviceCapabilitiesProvider.kt`**:
   - In the `buildSet { ... }` block, `add(Capability.X_NAMESPACE_REPORTED)` plus the value
     tags this Android device actually supports.

3. **Consumer site(s)**:
   - Replace any version-string heuristic with `Capability.supportsX(peer.capabilities, value)`.
   - For Android-version fallback (if relevant), follow the `PeerSupport` sealed-result pattern
     in `OctiServerEncryptionIssues.peerSupports`.

4. **Tests**: extend `CapabilityTest` with the new authority cases. Add an integration test on
   the consumer side that proves capability path wins over fallback.

5. **Cross-repo coordination**: bump the desktop port (`app-desktop/.../Capability.kt`) and the
   web declaration (`octi-web/src/protocol/octi-api.ts`) so they emit the new marker + tags.
   The server is dumb pipe — no server change needed.

## Cross-references

- **Server** (parse + echo): see `sync-server/.claude/rules/device-capabilities.md` and the
  shipped `parseCapabilitiesHeader` in `HttpExtensions.kt`.
- **Desktop** (Kotlin port): see `app-desktop/.claude/rules/device-capabilities.md` and the
  shipped `Capability.kt` / `CapabilitiesCodec.kt` under `desktop/protocol/sync/`.
- **Web** (TypeScript declaration): see `octi-web/.claude/rules/device-capabilities.md` and
  `OCTI_WEB_CAPABILITIES` in `src/protocol/octi-api.ts`.
