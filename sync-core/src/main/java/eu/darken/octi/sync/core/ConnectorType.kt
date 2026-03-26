package eu.darken.octi.sync.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConnectorType(val typeId: String) {
    @SerialName("gdrive") GDRIVE("gdrive"),
    @SerialName("kserver") OCTISERVER("kserver"),
}
