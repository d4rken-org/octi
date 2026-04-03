package eu.darken.octi.syncs.gdrive.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleAccount(
    @SerialName("accountId") val accountId: String,
    @SerialName("email") val email: String,
) {

    val id: Id
        get() = Id(accountId)

    override fun toString(): String = "GDriveAcc($email)"

    @Serializable
    data class Id(@SerialName("id") val id: String)
}
