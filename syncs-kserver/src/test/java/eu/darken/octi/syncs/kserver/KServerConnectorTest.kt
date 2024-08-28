package eu.darken.octi.syncs.kserver

import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.collections.toGzip
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test


class KServerConnectorTest {
    @Test
    fun `test encryption`() {
        val testData = "The cake is a lie!"
        val crypti = PayloadEncryption()

        val compressed = testData.toByteString().toGzip()
        val encrypted = crypti.encrypt(compressed)
        val decrypted = crypti.decrypt(encrypted)
        val decompressed = decrypted.fromGzip()

        decrypted shouldBe compressed
        decompressed shouldBe testData.toByteString()
    }
}