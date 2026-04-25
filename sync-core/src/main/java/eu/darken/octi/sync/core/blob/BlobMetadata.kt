package eu.darken.octi.sync.core.blob

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Note: [size] is connector-specific. GDrive reports plaintext bytes; OctiServer reports the
 * ciphertext byte count it stores (the server can't know plaintext in an E2EE setting). Callers
 * that need plaintext size must use the synced module metadata.
 */
@Serializable
data class BlobMetadata(
    @SerialName("size") val size: Long,
    @Serializable(with = InstantSerializer::class) @SerialName("createdAt") val createdAt: Instant,
    @SerialName("checksum") val checksum: String,
)
