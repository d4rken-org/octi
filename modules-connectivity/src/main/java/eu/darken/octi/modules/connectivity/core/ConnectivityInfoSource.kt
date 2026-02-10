package eu.darken.octi.modules.connectivity.core

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.module.core.ModuleInfoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.net.Inet4Address
import java.net.Inet6Address
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityInfoSource @Inject constructor(
    private val networkStateProvider: NetworkStateProvider,
    private val publicIpProvider: PublicIpProvider,
    private val connectivityManager: ConnectivityManager,
) : ModuleInfoSource<ConnectivityInfo> {

    override val info: Flow<ConnectivityInfo> = combine(
        networkStateProvider.networkState,
        publicIpProvider.publicIp,
    ) { networkState, publicIp ->
        val connectionType = determineConnectionType()
        val linkProperties = try {
            connectivityManager.activeNetwork?.let { connectivityManager.getLinkProperties(it) }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to get link properties: ${e.asLog()}" }
            null
        }

        val localIpv4 = linkProperties?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address?.hostAddress

        val localIpv6 = linkProperties?.linkAddresses
            ?.firstOrNull { it.address is Inet6Address && !it.address.isLinkLocalAddress }
            ?.address?.hostAddress

        val gateway = linkProperties?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway?.hostAddress

        val dnsServers = linkProperties?.dnsServers
            ?.mapNotNull { it.hostAddress }

        ConnectivityInfo(
            connectionType = connectionType,
            publicIp = publicIp,
            localAddressIpv4 = localIpv4,
            localAddressIpv6 = localIpv6,
            gatewayIp = gateway,
            dnsServers = dnsServers,
        )
    }

    private fun determineConnectionType(): ConnectivityInfo.ConnectionType {
        val network = connectivityManager.activeNetwork ?: return ConnectivityInfo.ConnectionType.NONE
        val capabilities = try {
            connectivityManager.getNetworkCapabilities(network)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to get capabilities: ${e.asLog()}" }
            null
        } ?: return ConnectivityInfo.ConnectionType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityInfo.ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityInfo.ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectivityInfo.ConnectionType.ETHERNET
            else -> ConnectivityInfo.ConnectionType.NONE
        }
    }

    companion object {
        private val TAG = logTag("Module", "Connectivity", "InfoSource")
    }
}
