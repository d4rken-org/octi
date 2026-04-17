package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.Sink
import okio.Timeout
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StreamingPayloadCipherTest : BaseTest() {

    @Test
    fun `encrypt and decrypt round-trip with source and sink`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val plain = Buffer().writeUtf8("hello blob world")
        val encrypted = Buffer()
        val decrypted = Buffer()
        val aad = "device:module:blob".encodeToByteArray()

        cipher.encrypt(plain, encrypted, aad)
        cipher.decrypt(encrypted, decrypted, aad)

        decrypted.readUtf8() shouldBe "hello blob world"
    }

    @Test
    fun `encrypt does not close the provided sink`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val sink = TrackingSink()

        cipher.encrypt(
            source = Buffer().writeUtf8("hello blob world"),
            sink = sink,
            associatedData = "device:module:blob".encodeToByteArray(),
        )

        sink.closeCalls shouldBe 0
        (sink.target.size > 0) shouldBe true
    }

    @Test
    fun `decrypt does not close the provided sink`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val encrypted = Buffer()
        cipher.encrypt(
            source = Buffer().writeUtf8("hello blob world"),
            sink = encrypted,
            associatedData = "device:module:blob".encodeToByteArray(),
        )

        val decryptSink = TrackingSink()
        cipher.decrypt(
            source = encrypted,
            sink = decryptSink,
            associatedData = "device:module:blob".encodeToByteArray(),
        )

        decryptSink.closeCalls shouldBe 0
        decryptSink.target.readUtf8() shouldBe "hello blob world"
    }

    @Test
    fun `empty payload round-trips`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val plain = Buffer()
        val encrypted = Buffer()
        val decrypted = Buffer()
        val aad = "device:module:blob".encodeToByteArray()

        cipher.encrypt(plain, encrypted, aad)
        cipher.decrypt(encrypted, decrypted, aad)

        decrypted.readUtf8() shouldBe ""
    }

    @Test
    fun `decrypt with wrong AAD throws`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val encrypted = Buffer()
        cipher.encrypt(
            source = Buffer().writeUtf8("secret"),
            sink = encrypted,
            associatedData = "correct-aad".encodeToByteArray(),
        )

        val decrypted = Buffer()
        try {
            cipher.decrypt(encrypted, decrypted, "wrong-aad".encodeToByteArray())
            throw AssertionError("Expected decrypt to throw with wrong AAD")
        } catch (e: Exception) {
            // Expected — tag verification fails
        }
    }

    private class TrackingSink : Sink {
        val target = Buffer()
        var closeCalls: Int = 0

        override fun write(source: Buffer, byteCount: Long) {
            target.write(source, byteCount)
        }

        override fun flush() = Unit

        override fun timeout() = Timeout.NONE

        override fun close() {
            closeCalls += 1
        }
    }
}
