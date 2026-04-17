package eu.darken.octi.sync.core

import android.os.Parcelable
import eu.darken.octi.common.sync.ConnectorType
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class ConnectorId(
    @SerialName("type") val type: ConnectorType,
    @SerialName("subtype") val subtype: String,
    @SerialName("account") val account: String,
) : Parcelable {

    val idString: String
        get() = "${type.typeId}-$subtype-$account"

    val logLabel: String
        get() = "$type:$subtype"
}
