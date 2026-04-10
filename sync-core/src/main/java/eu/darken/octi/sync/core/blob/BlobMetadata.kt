package eu.darken.octi.sync.core.blob

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class BlobMetadata(
    @SerialName("size") val size: Long,
    @Serializable(with = InstantSerializer::class) @SerialName("createdAt") val createdAt: Instant,
    @SerialName("checksum") val checksum: String,
)
