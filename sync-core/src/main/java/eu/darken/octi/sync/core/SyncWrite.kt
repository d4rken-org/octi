package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString
import java.util.*

/**
 * Data written to a connector
 */
interface SyncWrite {
    val writeId: UUID
    val deviceId: DeviceId
    val modules: Collection<Device.Module>

    @Serializable
    data class BlobAttachment(
        @SerialName("logicalKey") val logicalKey: String,
        @SerialName("connectorRefs") val connectorRefs: Map<String, RemoteBlobRef> = emptyMap(),
        /**
         * Connectors (by [ConnectorId.idString]) currently advertised as holding this blob.
         * When non-empty, [OctiServerConnector.writeServer] filters its commit to only refs
         * for connectors in this set — defense-in-depth against stale [connectorRefs] entries.
         * Empty means "no filter" (legacy attachments from pre-availableOn producers).
         */
        @SerialName("availableOn") val availableOn: Set<String> = emptySet(),
    )

    interface Device {
        interface Module {
            val moduleId: ModuleId
            val payload: ByteString
            /** Blob attachments for this module. null = legacy write, non-null = blob-aware commit. */
            val blobs: List<BlobAttachment>? get() = null
        }
    }
}