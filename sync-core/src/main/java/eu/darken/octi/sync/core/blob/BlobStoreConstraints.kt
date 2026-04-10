package eu.darken.octi.sync.core.blob

data class BlobStoreConstraints(
    val maxFileBytes: Long? = null,
    val maxTotalBytes: Long? = null,
)
