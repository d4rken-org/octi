package eu.darken.octi.syncs.jserver.core

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.octi.sync.core.SyncDeviceId
import kotlinx.parcelize.Parcelize
import java.time.Instant


interface JServer {

    @JsonClass(generateAdapter = false)
    enum class Official(val address: Address) {
        @Json(name = "GRYLLS") GRYLLS(Address("grylls.octi.darken.eu")),
        @Json(name = "DEV") DEV(Address("dev.octi.darken.eu")),
    }

    @JsonClass(generateAdapter = true)
    @Parcelize
    data class Address(@Json(name = "domain") val domain: String) : Parcelable {
        val httpUrl: String
            get() = "https://$domain/v1/"
    }

    @JsonClass(generateAdapter = true)
    data class Credentials(
        @Json(name = "serverAdress") val serverAdress: Address,
        @Json(name = "accountId") val accountId: AccountId,
        @Json(name = "deviceId") val deviceId: SyncDeviceId,
        @Json(name = "devicePassword") val devicePassword: DevicePassword,
        @Json(name = "createdAt") val createdAt: Instant,
    ) {

        override fun toString(): String =
            "JServerAccount(server=$serverAdress, account=$accountId, device=$deviceId, password=$devicePassword)"

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class AccountId(@Json(name = "id") val id: String) : Parcelable

        @JsonClass(generateAdapter = true)
        data class DevicePassword(@Json(name = "password") val password: String) {
            override fun toString(): String = "DevicePassword(code=${password.take(4)}...)"
        }

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class LinkCode(@Json(name = "code") val code: String) : Parcelable {
            override fun toString(): String = "ShareCode(code=${code.take(4)}...)"
        }
    }
}
