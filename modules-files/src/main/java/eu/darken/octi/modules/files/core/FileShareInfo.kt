package eu.darken.octi.modules.files.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.sync.core.RemoteBlobRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class FileShareInfo(
    @SerialName("files") val files: List<SharedFile> = emptyList(),
) {
    @Serializable
    data class SharedFile(
        @SerialName("name") val name: String,
        @SerialName("mimeType") val mimeType: String,
        @SerialName("size") val size: Long,
        @SerialName("blobKey") val blobKey: String,
        @SerialName("checksum") val checksum: String,
        @Serializable(with = InstantSerializer::class)
        @SerialName("sharedAt") val sharedAt: Instant,
        @Serializable(with = InstantSerializer::class)
        @SerialName("expiresAt") val expiresAt: Instant,
        @SerialName("availableOn") val availableOn: Set<String> = emptySet(),
        @SerialName("connectorRefs") val connectorRefs: Map<String, RemoteBlobRef> = emptyMap(),
    )
}
