package eu.darken.octi.modules.connectivity.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectivityInfo(
    @SerialName("connectionType") val connectionType: ConnectionType?,
    @SerialName("publicIp") val publicIp: String?,
    @SerialName("localAddressIpv4") val localAddressIpv4: String?,
    @SerialName("localAddressIpv6") val localAddressIpv6: String?,
    @SerialName("gatewayIp") val gatewayIp: String?,
    @SerialName("dnsServers") val dnsServers: List<String>?,
) {

    @Serializable
    enum class ConnectionType {
        @SerialName("WIFI") WIFI,
        @SerialName("CELLULAR") CELLULAR,
        @SerialName("ETHERNET") ETHERNET,
        @SerialName("NONE") NONE,
        ;
    }
}
