package eu.darken.octi.syncs.octiserver.core

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import okhttp3.WebSocket
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.seconds

/**
 * Reconnect hygiene of [OctiServerWebSocket.SocketSupervisor].
 *
 * `onFailure` and `onClosing` both want to reconnect and cancelling a socket fires `onFailure`
 * itself, so a single drop could otherwise fan out into several live sockets — each holding one of
 * OkHttp's five per-host dispatcher slots.
 */
class OctiServerWebSocketSupervisorTest : BaseTest() {

    private fun supervisor() = OctiServerWebSocket.SocketSupervisor()

    @Nested
    inner class `one socket at a time` {

        @Test
        fun `opening a new socket retires the previous one`() {
            val supervisor = supervisor()
            val first = mockk<WebSocket>(relaxed = true)
            val second = mockk<WebSocket>(relaxed = true)

            val gen1 = supervisor.beginConnect()!!
            supervisor.attach(gen1, first)
            val gen2 = supervisor.beginConnect()!!
            supervisor.attach(gen2, second)

            // cancel(), not close(): the dispatcher slot has to come back immediately.
            verify { first.cancel() }
            supervisor.activeSocket shouldBe second
        }

        @Test
        fun `a socket that arrives for a retired generation is cancelled, not adopted`() {
            val supervisor = supervisor()
            val late = mockk<WebSocket>(relaxed = true)

            val gen1 = supervisor.beginConnect()!!
            supervisor.beginConnect()
            supervisor.attach(gen1, late)

            verify { late.cancel() }
            supervisor.activeSocket shouldBe null
        }

        @Test
        fun `callbacks from a retired generation are ignored`() {
            val supervisor = supervisor()
            val gen1 = supervisor.beginConnect()!!
            val gen2 = supervisor.beginConnect()!!

            supervisor.isCurrent(gen1) shouldBe false
            supervisor.isCurrent(gen2) shouldBe true
            supervisor.onOpened(gen1) shouldBe false
            supervisor.onOpened(gen2) shouldBe true
            supervisor.requestReconnect(gen1) shouldBe null
        }
    }

    @Nested
    inner class `reconnects` {

        @Test
        fun `simultaneous failure and closing callbacks schedule exactly one reconnect`() {
            val supervisor = supervisor()
            val gen = supervisor.beginConnect()!!

            supervisor.requestReconnect(gen) shouldBe 1.seconds
            // The second callback (onClosing right after onFailure) must not stack another one.
            supervisor.requestReconnect(gen) shouldBe null
        }

        @Test
        fun `backoff grows per attempt and resets once connected`() {
            val supervisor = supervisor()

            val gen1 = supervisor.beginConnect()!!
            supervisor.requestReconnect(gen1) shouldBe 1.seconds
            val gen2 = supervisor.beginConnect()!!
            supervisor.requestReconnect(gen2) shouldBe 2.seconds
            val gen3 = supervisor.beginConnect()!!
            supervisor.requestReconnect(gen3) shouldBe 4.seconds

            val gen4 = supervisor.beginConnect()!!
            supervisor.onOpened(gen4) shouldBe true
            supervisor.requestReconnect(gen4) shouldBe 1.seconds
        }

        @Test
        fun `nothing reconnects after the flow was torn down`() {
            val supervisor = supervisor()
            val socket = mockk<WebSocket>(relaxed = true)
            val gen = supervisor.beginConnect()!!
            supervisor.attach(gen, socket)

            supervisor.shutdown()

            // shutdown() bumps the generation before cancelling, so the onFailure that cancelling
            // fires cannot schedule a reconnect.
            verify { socket.cancel() }
            supervisor.requestReconnect(gen) shouldBe null
            supervisor.isCurrent(gen) shouldBe false
            supervisor.beginConnect() shouldBe null
            supervisor.activeSocket shouldBe null
        }
    }
}
