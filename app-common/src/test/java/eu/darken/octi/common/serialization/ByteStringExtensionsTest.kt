package eu.darken.octi.common.serialization

import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.collections.toGzip
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ByteStringExtensionsTest : BaseTest() {

    @Test
    fun `bytestring gzip`() {
        val testData = "strawberry".toByteString()
        val expected = "1f8b08000000000000002b2e294a2c4f4a2d2aaa04003f65870a0a000000".decodeHex()
        val gzipped = testData.toGzip()
        gzipped shouldBe expected
        gzipped.fromGzip() shouldBe testData
    }

}