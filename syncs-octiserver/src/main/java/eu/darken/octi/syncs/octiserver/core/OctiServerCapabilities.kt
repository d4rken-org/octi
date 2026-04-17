package eu.darken.octi.syncs.octiserver.core

data class OctiServerCapabilities(
    val blobSupport: BlobSupport = BlobSupport.UNKNOWN,
    val storageApiVersion: Int = 0,
) {
    enum class BlobSupport { SUPPORTED, LEGACY, UNKNOWN }
}
