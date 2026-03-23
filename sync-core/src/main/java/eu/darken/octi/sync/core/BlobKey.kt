package eu.darken.octi.sync.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BlobKey(@SerialName("key") val key: String)
