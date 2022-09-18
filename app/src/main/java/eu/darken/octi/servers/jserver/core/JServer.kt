package eu.darken.octi.servers.jserver.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant


interface JServer {

    @JsonClass(generateAdapter = false)
    enum class Official(val address: Address) {
        @Json(name = "GRYLLS") GRYLLS(Address("grylls.octi.darken.eu")),
        @Json(name = "DEV") DEV(Address("dev.octi.darken.eu")),
    }

    @JsonClass(generateAdapter = true)
    data class Address(@Json(name = "ipv4") val ipv4: String) {

        val baseUrl: String
            get() = "https://$ipv4/"

    }

    @JsonClass(generateAdapter = true)
    data class Credentials(
        @Json(name = "server") val server: Address,
        @Json(name = "accountId") val accountId: AccountId,
        @Json(name = "deviceId") val deviceId: DeviceId,
        @Json(name = "devicePassword") val devicePassword: String,
        @Json(name = "createdAt") val createdAt: Instant,
    ) {

        override fun toString(): String =
            "JServerAccount(server=$server, account=$accountId, device=$deviceId, password=${devicePassword.take(4)}...)"

        @JsonClass(generateAdapter = true)
        data class AccountId(@Json(name = "id") val id: String)

        @JsonClass(generateAdapter = true)
        data class DeviceId(@Json(name = "id") val id: String)

        data class ShareCode(val code: String) {
            override fun toString(): String = "ShareCode(code=${code.take(4)}...)"
        }
    }
}
