package eu.darken.octi.common.network

import kotlinx.coroutines.flow.Flow

interface PublicIpEndpointProvider {
    val endpointUrls: Flow<List<String>>
}
