package eu.darken.octi.modules.connectivity

import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.network.PublicIpEndpointProvider
import eu.darken.octi.syncs.octiserver.core.OctiServer
import eu.darken.octi.syncs.octiserver.core.OctiServerAccountRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicIpEndpointProviderImpl @Inject constructor(
    private val kServerAccountRepo: OctiServerAccountRepo,
) : PublicIpEndpointProvider {

    override val endpointUrls: Flow<List<String>> = kServerAccountRepo.accounts.map { accounts ->
        val urls = buildList {
            accounts.forEach { credentials ->
                add("${credentials.serverAdress.address}/v1/myip")
            }
            // Always include PROD as final fallback
            val prodUrl = "${OctiServer.Official.PROD.address.address}/v1/myip"
            if (none { it == prodUrl }) add(prodUrl)
        }
        log(TAG) { "Public IP endpoints: $urls" }
        urls
    }

    companion object {
        private val TAG = logTag("Module", "Connectivity", "PublicIpEndpointProvider")
    }
}
