package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.collections.toGzip
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import testhelpers.BaseTest

/**
 * Regression cover for the failure the user hits when a link code arrives incomplete.
 *
 * Reported as: `Error - IOException / ID1ID2: actual 0xffffab6d != expected 0x00001f8b`, which is
 * okio's internal gzip header wording leaking into the error dialog.
 */
class InvalidLinkCodeExceptionTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val testData = LinkingData(
        serverAdress = OctiServer.Address(domain = "prod.kserver.octi.darken.eu"),
        linkCode = OctiServer.Credentials.LinkCode(code = "ABCD1234"),
        encryptionKeyset = PayloadEncryption.KeySet(
            type = "AES256_GCM_SIV",
            key = "testkey".encodeUtf8(),
        ),
    )

    private val validCode = testData.toEncodedString(json)

    @Nested
    inner class `decode failures all surface as InvalidLinkCodeException` {

        @Test
        fun `truncated at the start`() {
            // The reported case. Every character is still base64-alphabet, so okio's decoder
            // accepts it and the failure only shows up at the gzip magic check.
            val error = assertThrows<InvalidLinkCodeException> {
                LinkingData.fromEncodedString(json, validCode.drop(8))
            }
            error.stage shouldBe InvalidLinkCodeException.Stage.GZIP
        }

        @Test
        fun `truncated at the end`() {
            assertThrows<InvalidLinkCodeException> {
                LinkingData.fromEncodedString(json, validCode.dropLast(20))
            }
        }

        @Test
        fun `not base64 at all`() {
            val error = assertThrows<InvalidLinkCodeException> {
                LinkingData.fromEncodedString(json, "not a link code!")
            }
            error.stage shouldBe InvalidLinkCodeException.Stage.BASE64
        }

        @Test
        fun `empty`() {
            assertThrows<InvalidLinkCodeException> {
                LinkingData.fromEncodedString(json, "")
            }
        }

        @Test
        fun `valid gzip but not LinkingData json`() {
            val notOurJson = """{"hello":"world"}""".encodeUtf8().toGzip().base64()
            val error = assertThrows<InvalidLinkCodeException> {
                LinkingData.fromEncodedString(json, notOurJson)
            }
            error.stage shouldBe InvalidLinkCodeException.Stage.JSON
        }

        @Test
        fun `the decoder cause is never chained`() {
            // ViewModel4 logs the whole cause chain with asLog(), and a kotlinx-serialization
            // message embeds a "JSON input:" excerpt of the plaintext, which holds the keyset.
            val plaintextWithSecret = """{"serverAddress":{"domain":"x"},"key":"SUPERSECRETKEY"""
                .encodeUtf8()
                .toGzip()
                .base64()
            val error = assertThrows<InvalidLinkCodeException> {
                LinkingData.fromEncodedString(json, plaintextWithSecret)
            }
            error.cause shouldBe null
            error.stackTraceToString() shouldNotContain "SUPERSECRETKEY"
        }
    }

    @Nested
    inner class `valid codes still decode` {

        @Test
        fun `round trip`() {
            LinkingData.fromEncodedString(json, validCode) shouldBe testData
        }

        @Test
        fun `surrounding whitespace is tolerated`() {
            // Share targets and clipboards routinely add a trailing newline.
            LinkingData.fromEncodedString(json, "\n  $validCode \n") shouldBe testData
        }
    }

    @Nested
    inner class `link code shape is safe to log` {

        @Test
        fun `reports length prefix and alphabet cleanliness`() {
            val shape = validCode.linkCodeShape()
            shape shouldContain "len=${validCode.length}"
            shape shouldContain "gzipPrefix=true"
            shape shouldContain "alphabet=true"
        }

        @Test
        fun `a stray character is called out`() {
            "H4sIabc!".linkCodeShape() shouldContain "alphabet=false"
        }

        @Test
        fun `a lost head is visible as a missing gzip prefix`() {
            val shape = validCode.drop(8).linkCodeShape()
            shape shouldContain "gzipPrefix=false"
            shape shouldContain "alphabet=true"
        }

        @Test
        fun `no run of code characters ever reaches the log line`() {
            // The previous version echoed the first four characters. That is the harmless constant
            // "H4sI" only while the code is intact; on a truncated code those are payload bytes.
            val truncated = validCode.drop(8)
            val shape = truncated.linkCodeShape()
            (4..12).forEach { window ->
                truncated.windowed(window).any { shape.contains(it) } shouldBe false
            }
        }
    }
}
