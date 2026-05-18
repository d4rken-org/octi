package eu.darken.octi.syncs.gdrive.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GDriveDeviceInfo(
    @SerialName("version") val version: String? = null,
    @SerialName("platform") val platform: String? = null,
    @SerialName("label") val label: String? = null,
    /**
     * Per-device capability tag set. Stored as raw [JsonElement] so a malformed value on
     * one device's manifest doesn't tank the whole `_device.json` decode. Mapped through
     * [eu.darken.octi.sync.core.CapabilitiesCodec.decode] at the boundary.
     */
    @SerialName("capabilities") val capabilities: JsonElement? = null,
)
