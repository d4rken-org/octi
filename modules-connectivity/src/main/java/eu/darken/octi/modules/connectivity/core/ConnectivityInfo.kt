package eu.darken.octi.modules.connectivity.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConnectivityInfo(
    @Json(name = "connectionType") val connectionType: ConnectionType?,
    @Json(name = "publicIp") val publicIp: String?,
    @Json(name = "localAddressIpv4") val localAddressIpv4: String?,
    @Json(name = "localAddressIpv6") val localAddressIpv6: String?,
    @Json(name = "gatewayIp") val gatewayIp: String?,
    @Json(name = "dnsServers") val dnsServers: List<String>?,
) {

    @JsonClass(generateAdapter = false)
    enum class ConnectionType {
        @Json(name = "WIFI") WIFI,
        @Json(name = "CELLULAR") CELLULAR,
        @Json(name = "ETHERNET") ETHERNET,
        @Json(name = "NONE") NONE,
        ;
    }
}
