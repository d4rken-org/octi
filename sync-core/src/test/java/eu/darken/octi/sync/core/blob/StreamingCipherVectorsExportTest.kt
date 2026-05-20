package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.random.Random

/**
 * JVM-side roundtrip regression for [StreamingPayloadCipher] — encrypt then decrypt
 * under a freshly-derived streaming key (HKDF-SHA256 from a sync keyset) must reproduce
 * the original bytes, including the 1 MB segment-boundary case that exercises Tink's
 * multi-segment encode path.
 *
 * Cross-language correctness pinning is enforced separately by the committed interop
 * fixtures — see [eu.darken.octi.sync.core.interop.InteropFixtureGeneratorTest] (writes)
 * and [eu.darken.octi.sync.core.interop.InteropFixtureVerifyTest] (always-on decode gate).
 */
class StreamingCipherVectorsExportTest : BaseTest() {

    private data class Vector(
        val name: String,
        val aad: String,
        val plaintext: ByteArray,
    )

    private val vectors = listOf(
        Vector("empty", "device-a:module-x:key-1", ByteArray(0)),
        Vector("short", "device-a:module-x:key-2", "Hi from Octi blobs".toByteArray(Charsets.UTF_8)),
        Vector("one-segment", "device-b:eu.darken.octi.module.core.files:key-3", Random(42).nextBytes(800_000)),
        // Crosses Tink's 1 MB segment boundary — exercises the multi-segment encode path.
        Vector("two-segments", "device-b:eu.darken.octi.module.core.files:key-4", Random(7).nextBytes(2_500_000)),
    )

    @Test
    fun `JVM round-trip - streaming cipher encrypts and decrypts`() {
        val keyset = PayloadEncryption().exportKeyset()
        val cipher = StreamingPayloadCipher(keyset)
        for (v in vectors) {
            val ct = Buffer()
            cipher.encrypt(Buffer().write(v.plaintext), ct, v.aad.toByteArray(Charsets.UTF_8))
            val pt = Buffer()
            cipher.decrypt(Buffer().write(ct.readByteArray()), pt, v.aad.toByteArray(Charsets.UTF_8))
            pt.readByteArray().toByteString() shouldBe v.plaintext.toByteString()
        }
    }
}
