package eu.darken.octi.syncs.octiserver.core

import io.kotest.matchers.shouldBe
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pins the two OkHttp behaviours the transport bounds are reasoned about:
 *
 * 1. `callTimeout` does not terminate an established WebSocket — OkHttp exits the call timeout
 *    after a successful upgrade — so keeping the inherited timeout is safe, and clearing it (the
 *    obvious-looking "fix") would only leave the handshake unbounded.
 * 2. `callTimeout` does **not** cover the time a call spends queued in the Dispatcher.
 *    `RealCall.enqueue` only calls `callStart()`; `timeout.enter()` happens in `AsyncCall.run()`,
 *    i.e. once the call is actually dispatched. A saturated dispatcher can therefore park a call
 *    indefinitely regardless of any HTTP timeout — which is why the bound that actually guarantees
 *    progress lives in `ConnectorProcessor`, not here.
 */
class WebSocketCallTimeoutTest : BaseTest() {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `an established socket outlives the callTimeout that bounded its handshake`() {
        val serverSideOpen = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    serverSideOpen.countDown()
                    // Well past the client's call timeout.
                    Thread.sleep(1500)
                    webSocket.send("late-message")
                }
            })
        )

        val client = OkHttpClient.Builder()
            .callTimeout(500, TimeUnit.MILLISECONDS)
            .build()

        val message = CountDownLatch(1)
        val failure = CountDownLatch(1)
        client.newWebSocket(
            Request.Builder().url(server.url("/v1/ws")).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text == "late-message") message.countDown()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failure.countDown()
                }
            },
        )

        serverSideOpen.await(5, TimeUnit.SECONDS) shouldBe true
        message.await(5, TimeUnit.SECONDS) shouldBe true
        failure.count shouldBe 1L
    }

    @Test
    fun `a handshake stuck behind a saturated dispatcher is NOT bounded by the callTimeout`() {
        // One in-flight call occupies the only slot; the WebSocket handshake sits in the dispatcher
        // queue. The 500ms callTimeout does not fire while it waits there — the handshake only
        // proceeds once the blocker frees the slot, seconds later. Documenting the gap, because the
        // natural assumption ("callTimeout is the total call bound, queueing included") is wrong
        // and would leave this class of wedge unfixed.
        server.enqueue(MockResponse().setBody("blocker").setHeadersDelay(5, TimeUnit.SECONDS))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))

        // Shared dispatcher, separate timeouts: the blocker must not be the thing that times out.
        val dispatcher = Dispatcher().apply { maxRequests = 1; maxRequestsPerHost = 1 }
        val blockerClient = OkHttpClient.Builder().dispatcher(dispatcher).build()
        val client = OkHttpClient.Builder()
            .callTimeout(500, TimeUnit.MILLISECONDS)
            .dispatcher(dispatcher)
            .build()

        blockerClient.newCall(Request.Builder().url(server.url("/blocker")).build())
            .enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = Unit
                override fun onResponse(call: okhttp3.Call, response: Response) = response.close()
            })

        val queuedWhileBlocked = CountDownLatch(1)
        val settled = CountDownLatch(1)
        client.newWebSocket(
            Request.Builder().url(server.url("/v1/ws")).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = settled.countDown()
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = settled.countDown()
            },
        )

        // The handshake is queued, not running...
        Thread.sleep(300)
        if (dispatcher.queuedCallsCount() == 1) queuedWhileBlocked.countDown()
        queuedWhileBlocked.count shouldBe 0L

        // ...and it is still queued well after its own call timeout would have expired.
        Thread.sleep(1000)
        settled.count shouldBe 1L
    }
}
