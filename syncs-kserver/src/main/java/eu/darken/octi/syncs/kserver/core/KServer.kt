package eu.darken.octi.syncs.kserver.core

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.parcelize.Parcelize
import java.time.Instant
import java.util.UUID


interface KServer {

    @JsonClass(generateAdapter = false)
    enum class Official(val address: Address) {
        @Json(name = "PROD") PROD(Address("prod.kserver.octi.darken.eu")),
        @Json(name = "BETA") BETA(Address("beta.kserver.octi.darken.eu")),
        @Json(name = "DEV") DEV(Address("dev.kserver.octi.darken.eu")),
        @Json(name = "LOCAL") LOCAL(Address("blasphemy", protocol = "http", port = 8080)),
    }

    @JsonClass(generateAdapter = true)
    @Parcelize
    data class Address(
        @Json(name = "domain") val domain: String,
        @Json(name = "protocol") val protocol: String = "https",
        @Json(name = "port") val port: Int = 443,
    ) : Parcelable {
        val httpUrl: String
            get() = "$protocol://$domain:$port/v1/"
    }

    @JsonClass(generateAdapter = true)
    data class Credentials(
        @Json(name = "serverAdress") val serverAdress: Address,
        @Json(name = "accountId") val accountId: AccountId,
        @Json(name = "devicePassword") val devicePassword: DevicePassword,
        @Json(name = "encryptionKeyset") val encryptionKeyset: PayloadEncryption.KeySet,
        @Json(name = "createdAt") val createdAt: Instant = Instant.now(),
    ) {

        override fun toString(): String =
            "KServer.Credentials(server=$serverAdress, account=$accountId, password=$devicePassword)"

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class AccountId(@Json(name = "id") val id: String = UUID.randomUUID().toString()) : Parcelable

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class DevicePassword(@Json(name = "password") val password: String) : Parcelable {
            override fun toString(): String = "DevicePassword(code=${password.take(4)}...)"
        }

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class LinkCode(@Json(name = "code") val code: String) : Parcelable {
            override fun toString(): String = "ShareCode(code=${code.take(4)}...)"
        }
    }
}
