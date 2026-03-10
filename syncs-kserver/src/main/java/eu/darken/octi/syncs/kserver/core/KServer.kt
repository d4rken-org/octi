@file:UseSerializers(InstantSerializer::class, ByteStringSerializer::class)

package eu.darken.octi.syncs.kserver.core

import android.os.Parcelable
import eu.darken.octi.common.serialization.serializer.ByteStringSerializer
import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID


interface KServer {

    @Serializable
    enum class Official(val address: Address) {
        @SerialName("PROD") PROD(Address("prod.kserver.octi.darken.eu")),
        @SerialName("BETA") BETA(Address("beta.kserver.octi.darken.eu")),
        @SerialName("LOCAL") LOCAL(Address("blasphemy", protocol = "http", port = 8080)),
    }

    @Serializable
    @Parcelize
    data class Address(
        @SerialName("domain") val domain: String,
        @SerialName("protocol") val protocol: String = "https",
        @SerialName("port") val port: Int = 443,
    ) : Parcelable {
        val address: String
            get() = "$protocol://$domain:$port"
    }

    @Serializable
    data class Credentials(
        @SerialName("serverAdress") val serverAdress: Address,
        @SerialName("accountId") val accountId: AccountId,
        @SerialName("devicePassword") val devicePassword: DevicePassword,
        @SerialName("encryptionKeyset") val encryptionKeyset: PayloadEncryption.KeySet,
        @SerialName("createdAt") val createdAt: Instant = Instant.now(),
    ) {

        override fun toString(): String =
            "KServer.Credentials(server=$serverAdress, account=$accountId, password=$devicePassword)"

        @Serializable
        @Parcelize
        data class AccountId(@SerialName("id") val id: String = UUID.randomUUID().toString()) : Parcelable

        @Serializable
        @Parcelize
        data class DevicePassword(@SerialName("password") val password: String) : Parcelable {
            override fun toString(): String = "DevicePassword(code=${password.take(4)}...)"
        }

        @Serializable
        @Parcelize
        data class LinkCode(@SerialName("code") val code: String) : Parcelable {
            override fun toString(): String = "ShareCode(code=${code.take(4)}...)"
        }
    }
}
