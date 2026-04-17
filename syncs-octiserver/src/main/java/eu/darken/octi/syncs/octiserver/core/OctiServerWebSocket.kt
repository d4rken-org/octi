package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncEvent
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OctiServerWebSocket(
    private val credentials: OctiServer.Credentials,
    private val connectorId: ConnectorId,
    private val syncSettings: SyncSettings,
    private val baseHttpClient: OkHttpClient,
    private val json: Json,
    private val onConnectionChanged: (Boolean) -> Unit = {},
) {

    @Serializable
    private data class EventPayload(
        @SerialName("events") val events: List<Event>,
    ) {
        @Serializable
        data class Event(
            @SerialName("type") val type: String,
            @SerialName("deviceId") val deviceId: String,
            @SerialName("moduleId") val moduleId: String,
            @SerialName("modifiedAt") val modifiedAt: String? = null,
            @SerialName("action") val action: String = "updated",
            @SerialName("blobKey") val blobKey: String? = null,
        )
    }

    fun connect(): Flow<SyncEvent> = callbackFlow {
        val wsClient = baseHttpClient.newBuilder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        var backoff = INITIAL_BACKOFF
        var activeSocket: WebSocket? = null

        lateinit var doConnect: () -> Unit

        val scheduleReconnect: () -> Unit = {
            launch {
                log(TAG) { "Reconnecting in $backoff" }
                delay(backoff)
                backoff = minOf(backoff * 2, MAX_BACKOFF)

                if (!isActive) return@launch

                doConnect()
            }
        }

        doConnect = {
            val wsProtocol = if (credentials.serverAdress.protocol == "https") "wss" else "ws"
            val url = "$wsProtocol://${credentials.serverAdress.domain}:${credentials.serverAdress.port}/v1/ws"

            val authString = "${credentials.accountId.id}:${credentials.devicePassword.password}"
            val authBase64 = Base64.getEncoder().encodeToString(authString.toByteArray())

            val request = Request.Builder()
                .url(url)
                .header("X-Device-ID", syncSettings.deviceId.id)
                .header("Authorization", "Basic $authBase64")
                .build()

            log(TAG, INFO) { "Connecting to $url" }

            activeSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    log(TAG, INFO) { "Connected" }
                    backoff = INITIAL_BACKOFF
                    onConnectionChanged(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    log(TAG, VERBOSE) { "Received: $text" }
                    try {
                        val payload = json.decodeFromString<EventPayload>(text)
                        payload.events.forEach { event ->
                            val syncEvent = event.toSyncEvent() ?: return@forEach
                            trySend(syncEvent)
                        }
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to parse message: ${e.message}" }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    log(TAG, WARN) { "Connection failed: ${t.message}" }
                    onConnectionChanged(false)
                    scheduleReconnect()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    log(TAG, INFO) { "Connection closing: $code $reason" }
                    onConnectionChanged(false)
                    webSocket.close(1000, null)
                    scheduleReconnect()
                }
            })
        }

        doConnect()

        awaitClose {
            log(TAG, INFO) { "Closing WebSocket" }
            onConnectionChanged(false)
            activeSocket?.close(1000, "Client closing")
        }
    }

    private fun EventPayload.Event.toSyncEvent(): SyncEvent? = when (type) {
        "module_changed" -> SyncEvent.ModuleChanged(
            connectorId = connectorId,
            deviceId = DeviceId(deviceId),
            moduleId = ModuleId(moduleId),
            modifiedAt = modifiedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Clock.System.now(),
            action = when (action) {
                "deleted" -> SyncEvent.ModuleChanged.Action.DELETED
                else -> SyncEvent.ModuleChanged.Action.UPDATED
            },
        )

        else -> {
            log(TAG, WARN) { "Unknown event type: $type" }
            null
        }
    }

    companion object {
        private val INITIAL_BACKOFF = 1.seconds
        private val MAX_BACKOFF = 30.seconds
        private val TAG = logTag("Sync", "OctiServer", "WebSocket")
    }
}
