package eu.darken.octi.sync.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class DeviceMetadata(
    @SerialName("deviceId")
    val deviceId: DeviceId,
    @SerialName("version")
    val version: String? = null,
    @SerialName("platform")
    val platform: String? = null,
    @SerialName("label")
    val label: String? = null,
    @Serializable(with = InstantSerializer::class)
    @SerialName("lastSeen")
    val lastSeen: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    @SerialName("addedAt")
    val addedAt: Instant? = null,
    /**
     * Set of feature tags this peer reports supporting. Format: `<namespace>:<value>`
     * (e.g. `encryption:AES256_GCM_SIV`). Use the [Capability] helpers to construct/check
     * tags rather than hand-rolling strings.
     *
     * Tri-state at the wrapper level:
     *   - null      → peer doesn't report capabilities (legacy / not yet rolled out).
     *                 Consumers fall back to other heuristics.
     *   - non-null  → peer participates. Within each namespace, support is determined
     *                 by the `<namespace>:_reported` marker; see [Capability].
     *
     * Advisory only — not signed. A hostile substrate can rewrite; real incompatibilities
     * surface at decrypt/use time regardless. See [CapabilitiesCodec] for validation limits.
     */
    @SerialName("capabilities")
    val capabilities: Set<String>? = null,
)

val SyncConnectorState.knownDevices: Set<DeviceId>
    get() = deviceMetadata.map { it.deviceId }.toSet()

/**
 * True if the peer's [DeviceMetadata.version] is comparable to Octi Android's release numbers.
 *
 * Non-Android clients (web, desktop) have independent release trains, so comparing their version
 * strings against Android-specific gates like MIN_COMPATIBLE_VERSION or MIN_GCM_SIV_CLIENT_VERSION
 * is meaningless. `null` is treated as Android for backward compatibility with pre-`platform`
 * Android clients (see CrossVersionLegacyServerTest for the v0.8.1 case).
 */
val DeviceMetadata.usesAndroidReleaseVersioning: Boolean
    get() = platform == null || platform.equals("android", ignoreCase = true)
