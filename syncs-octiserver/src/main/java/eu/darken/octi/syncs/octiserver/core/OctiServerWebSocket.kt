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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
import kotlin.time.Duration
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

    /**
     * The [baseHttpClient]'s `callTimeout` is deliberately inherited. OkHttp calls
     * `timeoutEarlyExit()` once the upgrade succeeds, so the bound covers only the handshake and
     * the time the call spends queued in the dispatcher — it never terminates an established
     * socket. Clearing it would leave a queued reconnect handshake unbounded, which is exactly the
     * failure mode this connector must not have.
     */
    fun connect(): Flow<SyncEvent> = callbackFlow {
        val wsClient = baseHttpClient.newBuilder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val supervisor = SocketSupervisor()
        var reconnectJob: Job? = null

        lateinit var doConnect: () -> Unit

        val scheduleReconnect: (Int) -> Unit = { callbackGeneration ->
            val backoff = supervisor.requestReconnect(callbackGeneration)
            if (backoff == null) {
                log(TAG, VERBOSE) { "Reconnect from generation $callbackGeneration ignored" }
            } else {
                reconnectJob = launch {
                    log(TAG) { "Reconnecting in $backoff" }
                    delay(backoff)
                    doConnect()
                }
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

            val myGeneration = supervisor.beginConnect()
            if (myGeneration == null) {
                log(TAG, VERBOSE) { "Not connecting, supervisor is shut down" }
            } else {
                log(TAG, INFO) { "Connecting to $url" }
                val socket = wsClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (!supervisor.onOpened(myGeneration)) return
                        log(TAG, INFO) { "Connected" }
                        onConnectionChanged(true)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (!supervisor.isCurrent(myGeneration)) return
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
                        if (!supervisor.isCurrent(myGeneration)) return
                        log(TAG, WARN) { "Connection failed: ${t.message}" }
                        onConnectionChanged(false)
                        scheduleReconnect(myGeneration)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        if (!supervisor.isCurrent(myGeneration)) return
                        log(TAG, INFO) { "Connection closing: $code $reason" }
                        onConnectionChanged(false)
                        webSocket.close(1000, null)
                        scheduleReconnect(myGeneration)
                    }
                })
                supervisor.attach(myGeneration, socket)
            }
        }

        doConnect()

        awaitClose {
            log(TAG, INFO) { "Closing WebSocket" }
            // shutdown() bumps the generation before cancelling, so the onFailure that cancelling
            // fires can no longer schedule a reconnect after the flow was torn down.
            supervisor.shutdown()
            reconnectJob?.cancel()
            onConnectionChanged(false)
        }
    }

    /**
     * Thread-safe bookkeeping for the socket lifecycle. `onFailure` and `onClosing` both want to
     * reconnect, and cancelling a socket fires `onFailure` itself, so without generation tagging a
     * single drop fans out into several live sockets. Every socket carries the generation it was
     * opened with; callbacks from any other generation are dropped, at most one socket is active,
     * and at most one reconnect is in flight.
     *
     * Callbacks arrive on OkHttp's reader threads while [beginConnect]/[shutdown] run on the flow's
     * coroutine, hence the lock rather than plain fields.
     */
    internal class SocketSupervisor {

        private val lock = Any()
        private var generation = 0
        private var socket: WebSocket? = null
        private var backoff = INITIAL_BACKOFF
        private var reconnectPending = false
        private var closed = false

        /** Retires the current socket and reserves the next generation, or null once shut down. */
        fun beginConnect(): Int? = synchronized(lock) {
            if (closed) return null
            reconnectPending = false
            // cancel(), not close(): the dispatcher slot must be released immediately instead of
            // after a graceful close handshake.
            socket?.cancel()
            socket = null
            ++generation
        }

        fun attach(generation: Int, socket: WebSocket) = synchronized(lock) {
            if (closed || generation != this.generation) {
                socket.cancel()
            } else {
                this.socket = socket
            }
        }

        fun isCurrent(generation: Int): Boolean = synchronized(lock) {
            !closed && generation == this.generation
        }

        /** @return false if the callback is stale and must be ignored. */
        fun onOpened(generation: Int): Boolean = synchronized(lock) {
            if (closed || generation != this.generation) return false
            backoff = INITIAL_BACKOFF
            true
        }

        /** @return how long to wait before reconnecting, or null if this request must be ignored. */
        fun requestReconnect(generation: Int): Duration? = synchronized(lock) {
            if (closed || generation != this.generation || reconnectPending) return null
            reconnectPending = true
            val current = backoff
            backoff = minOf(backoff * 2, MAX_BACKOFF)
            current
        }

        fun shutdown() = synchronized(lock) {
            closed = true
            generation++
            socket?.cancel()
            socket = null
        }

        /** Testing seam — the socket currently owned by the supervisor, if any. */
        internal val activeSocket: WebSocket?
            get() = synchronized(lock) { socket }
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
