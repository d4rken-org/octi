package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import okio.Sink
import okio.Timeout
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StreamingPayloadCipherTest : BaseTest() {

    private val aad = "device:module:blob".encodeToByteArray()

    @Test
    fun `encrypt and decrypt round-trip with source and sink`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val plain = Buffer().writeUtf8("hello blob world")
        val encrypted = Buffer()
        val decrypted = Buffer()

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
            associatedData = aad,
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
            associatedData = aad,
        )

        val decryptSink = TrackingSink()
        cipher.decrypt(
            source = encrypted,
            sink = decryptSink,
            associatedData = aad,
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
        shouldThrow<Exception> {
            cipher.decrypt(encrypted, decrypted, "wrong-aad".encodeToByteArray())
        }
    }

    @Test
    fun `constructor rejects legacy AES256_SIV keyset`() {
        val legacyKeyset = PayloadEncryption.KeySet(
            type = "AES256_SIV",
            key = ByteString.EMPTY,
        )
        val ex = shouldThrow<IllegalArgumentException> {
            StreamingPayloadCipher(legacyKeyset)
        }
        val msg = ex.message ?: ""
        (msg.contains("AES256_GCM_SIV") || msg.contains("AES256_SIV")) shouldBe true
    }

    @Test
    fun `constructor rejects unknown keyset type`() {
        val unknownKeyset = PayloadEncryption.KeySet(
            type = "AES256_GCM",
            key = ByteString.EMPTY,
        )
        shouldThrow<IllegalArgumentException> {
            StreamingPayloadCipher(unknownKeyset)
        }
    }

    @Test
    fun `two ciphers from same keyset interop`() {
        val keyset = PayloadEncryption().exportKeyset()
        val cipherA = StreamingPayloadCipher(keyset)
        val cipherB = StreamingPayloadCipher(keyset)

        val plaintext = "shared blob content".encodeToByteArray()
        val encrypted = Buffer()
        cipherA.encrypt(
            source = Buffer().write(plaintext),
            sink = encrypted,
            associatedData = aad,
        )

        val decrypted = Buffer()
        cipherB.decrypt(
            source = encrypted,
            sink = decrypted,
            associatedData = aad,
        )

        decrypted.readByteString() shouldBe plaintext.toByteString()
    }

    @Test
    fun `hkdfSha256 matches RFC 5869 test case 1 truncated to 32 bytes`() {
        // RFC 5869 §A.1 — T(1) for SHA-256 equals the first 32 bytes of the full OKM.
        // The production helper only supports a single expand step (length ≤ 32), so T(1)
        // truncation is exactly what it should return.
        val ikm = ByteArray(22) { 0x0b.toByte() }
        val salt = byteArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b, 0x0c,
        )
        val info = byteArrayOf(
            0xf0.toByte(), 0xf1.toByte(), 0xf2.toByte(), 0xf3.toByte(),
            0xf4.toByte(), 0xf5.toByte(), 0xf6.toByte(), 0xf7.toByte(),
            0xf8.toByte(), 0xf9.toByte(),
        )
        val expected = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf".decodeHex()

        val actual = StreamingPayloadCipher.hkdfSha256(ikm, salt, info, 32)

        actual.toByteString() shouldBe expected
    }

    @Test
    fun `multi-segment payload round-trips`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val plain = ByteArray(2_500_000) { (it and 0xFF).toByte() }
        val encrypted = Buffer()
        val decrypted = Buffer()

        cipher.encrypt(Buffer().write(plain), encrypted, aad)
        cipher.decrypt(encrypted, decrypted, aad)

        decrypted.readByteString() shouldBe plain.toByteString()
    }

    @Test
    fun `round-trips at segment boundaries`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val segmentSize = 1 * 1024 * 1024

        for (size in listOf(segmentSize - 1, segmentSize, segmentSize + 1)) {
            val plain = ByteArray(size) { (it and 0xFF).toByte() }
            val encrypted = Buffer()
            val decrypted = Buffer()

            cipher.encrypt(Buffer().write(plain), encrypted, aad)
            cipher.decrypt(encrypted, decrypted, aad)

            decrypted.readByteString() shouldBe plain.toByteString()
        }
    }

    @Test
    fun `single-segment tamper writes no plaintext`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val plain = ByteArray(1024) { (it and 0xFF).toByte() }

        val encryptedBuf = Buffer()
        cipher.encrypt(Buffer().write(plain), encryptedBuf, aad)
        val encBytes = encryptedBuf.readByteArray()
        encBytes[encBytes.size / 2] = (encBytes[encBytes.size / 2].toInt() xor 0x01).toByte()

        val decryptedBuf = Buffer()
        shouldThrow<Exception> {
            cipher.decrypt(Buffer().write(encBytes), decryptedBuf, aad)
        }
        decryptedBuf.size shouldBe 0L
    }

    @Test
    fun `multi-segment tamper leaks prefix before throwing`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val plain = ByteArray(2_500_000) { (it and 0xFF).toByte() }

        val encryptedBuf = Buffer()
        cipher.encrypt(Buffer().write(plain), encryptedBuf, aad)
        val encBytes = encryptedBuf.readByteArray()
        // Flip a byte well past segment 0 so the first segment's tag verifies and its
        // plaintext lands in the sink before the tampered segment fails.
        val tamperOffset = encBytes.size - 200_000
        encBytes[tamperOffset] = (encBytes[tamperOffset].toInt() xor 0x01).toByte()

        val decryptedBuf = Buffer()
        shouldThrow<Exception> {
            cipher.decrypt(Buffer().write(encBytes), decryptedBuf, aad)
        }
        decryptedBuf.size shouldBeGreaterThan 0L
        decryptedBuf.size shouldBeLessThan 2_500_000L
    }

    @Test
    fun `truncated ciphertext is detected`() {
        val cipher = StreamingPayloadCipher(PayloadEncryption().exportKeyset())
        val plain = ByteArray(2_500_000) { (it and 0xFF).toByte() }

        val encryptedBuf = Buffer()
        cipher.encrypt(Buffer().write(plain), encryptedBuf, aad)
        val encBytes = encryptedBuf.readByteArray()
        val truncated = encBytes.copyOfRange(0, encBytes.size - 16)

        val decryptedBuf = Buffer()
        shouldThrow<Exception> {
            cipher.decrypt(Buffer().write(truncated), decryptedBuf, aad)
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
