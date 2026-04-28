package eu.darken.octi.modules.files.core

import android.net.Uri
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class IncomingShareInboxTest : BaseTest() {

    private fun fakeUri(path: String): Uri = mockk<Uri>().also {
        every { it.toString() } returns "content://test/$path"
    }

    @Test
    fun `enqueue returns unique tokens for each batch`() {
        val inbox = IncomingShareInbox()
        val batchA = listOf(fakeUri("a"))
        val batchB = listOf(fakeUri("b"))
        val tokenA = inbox.enqueue(batchA)
        val tokenB = inbox.enqueue(batchB)
        tokenA shouldNotBe tokenB
    }

    @Test
    fun `drain returns the matching batch only`() {
        val inbox = IncomingShareInbox()
        val batchA = listOf(fakeUri("a1"), fakeUri("a2"))
        val batchB = listOf(fakeUri("b1"))
        val tokenA = inbox.enqueue(batchA)
        val tokenB = inbox.enqueue(batchB)

        inbox.drain(tokenA) shouldBe batchA
        inbox.drain(tokenB) shouldBe batchB
    }

    @Test
    fun `second drain on the same token returns null - idempotent`() {
        val inbox = IncomingShareInbox()
        val batch = listOf(fakeUri("x"))
        val token = inbox.enqueue(batch)
        inbox.drain(token) shouldBe batch
        inbox.drain(token) shouldBe null
    }

    @Test
    fun `drain on unknown token returns null`() {
        val inbox = IncomingShareInbox()
        inbox.drain("never-seen") shouldBe null
    }
}
