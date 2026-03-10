package eu.darken.octi.modules.connectivity.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.network.PublicIpEndpointProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicIpProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val networkStateProvider: NetworkStateProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val publicIpEndpointProvider: PublicIpEndpointProvider,
    private val json: Json,
) {

    @Serializable
    data class IpResponse(
        @SerialName("ip") val ip: String,
    )

    val publicIp: Flow<String?> = combine(
        networkStateProvider.networkState,
        publicIpEndpointProvider.endpointUrls,
    ) { networkState, endpointUrls ->
        networkState to endpointUrls
    }.mapLatest { (networkState, endpointUrls) ->
        if (!networkState.isInternetAvailable) {
            log(TAG, WARN) { "No internet available, skipping public IP fetch" }
            return@mapLatest null
        }

        delay(SETTLE_DELAY_MS)

        for (url in endpointUrls) {
            val result = fetchPublicIp(url)
            if (result != null) return@mapLatest result
        }

        null
    }

    private suspend fun fetchPublicIp(endpointUrl: String): String? = withContext(dispatcherProvider.IO) {
        try {
            val request = Request.Builder()
                .url(endpointUrl)
                .get()
                .build()

            val call = httpClient.newCall(request).also {
                it.timeout().timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    log(TAG, WARN) { "Public IP fetch failed: ${response.code}" }
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                json.decodeFromString<IpResponse>(body).ip
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to fetch public IP: ${e.asLog()}" }
            null
        }
    }

    companion object {
        private const val SETTLE_DELAY_MS = 500L
        private const val TIMEOUT_MS = 5_000L
        private val TAG = logTag("Module", "Connectivity", "PublicIpProvider")
    }
}
