package eu.darken.octi.sync.core.encryption

import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.collections.toGzip
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.toByteString as bytesToByteString
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.random.Random

/**
 * JVM-side roundtrip regression for [PayloadEncryption] — encrypt then decrypt under
 * a freshly-generated keyset must reproduce the original plaintext for both GCM-SIV
 * and legacy SIV modes, with the gzip-wrap layering [OctiServerConnector] applies.
 *
 * Cross-language correctness pinning (octi-web, octi-desktop must decrypt what we
 * encrypt) is enforced separately by the committed interop fixtures — see
 * [eu.darken.octi.sync.core.interop.InteropFixtureGeneratorTest] (writes) and
 * [eu.darken.octi.sync.core.interop.InteropFixtureVerifyTest] (always-on decode gate).
 */
class TinkVectorsExportTest : BaseTest() {

    private data class Vector(
        val name: String,
        val plaintext: ByteString,
        val ad: String,
    )

    private val vectors = listOf(
        Vector("empty", ByteString.EMPTY, "device-a:module-x"),
        Vector("hello-world", "Hello, Octi!".toByteString(), "device-a:module-x"),
        Vector("with-special-chars", "device👋label".toByteString(), "browser-1234:eu.darken.octi.module.core.clipboard"),
        Vector(
            "approx-10kb",
            Random(12345).nextBytes(10_000).bytesToByteString(),
            "device-b:eu.darken.octi.module.files",
        ),
    )

    @Test
    fun `JVM round-trip - gcmsiv with outer gzip wrap matches OctiServerConnector flow`() {
        val crypti = PayloadEncryption()
        for (v in vectors) {
            val encrypted = crypti.encrypt(v.plaintext.toGzip(), v.ad.toByteArray(Charsets.UTF_8))
            val decrypted = crypti.decrypt(encrypted, v.ad.toByteArray(Charsets.UTF_8)).fromGzip()
            decrypted shouldBe v.plaintext
        }
    }

    @Test
    fun `JVM round-trip - legacy siv ignores AD per PayloadEncryption contract`() {
        val crypti = PayloadEncryption(useLegacyEncryption = true)
        for (v in vectors) {
            val encrypted = crypti.encrypt(v.plaintext.toGzip())
            crypti.decrypt(encrypted).fromGzip() shouldBe v.plaintext
        }
    }
}
