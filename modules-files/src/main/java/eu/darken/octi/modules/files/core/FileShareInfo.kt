package eu.darken.octi.modules.files.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.sync.core.RemoteBlobRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class FileShareInfo(
    @SerialName("files") val files: List<SharedFile> = emptyList(),
    @SerialName("deleteRequests") val deleteRequests: List<DeleteRequest> = emptyList(),
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
    ) {
        init {
            // Pre-release contract: every shared file carries a non-blank SHA-256 plaintext
            // digest so the receive path can verify integrity on download and on cache hit.
            // No legacy data exists with empty checksum; fail-fast rather than silently skip.
            require(checksum.isNotBlank()) { "SharedFile.checksum must not be blank (blobKey=$blobKey)" }
        }
    }

    /**
     * A non-owner's request to have the owner delete a specific blob.
     * Trust model: all paired devices on the same account are trusted — any peer's DeleteRequest
     * is honored fleet-wide. [BlobMaintenance] on the owning device consumes this.
     */
    @Serializable
    data class DeleteRequest(
        @SerialName("targetDeviceId") val targetDeviceId: String,
        @SerialName("blobKey") val blobKey: String,
        @Serializable(with = InstantSerializer::class)
        @SerialName("requestedAt") val requestedAt: Instant,
        @Serializable(with = InstantSerializer::class)
        @SerialName("retainUntil") val retainUntil: Instant,
    )
}
