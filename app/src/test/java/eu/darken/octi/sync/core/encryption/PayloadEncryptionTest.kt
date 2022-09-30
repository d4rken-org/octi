package eu.darken.octi.sync.core.encryption

import eu.darken.octi.common.collections.toByteString
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class PayloadEncryptionTest : BaseTest() {

    @Test
    fun `encrypt and decrypt`() {
        val crypti = eu.darken.octi.sync.core.encryption.PayloadEncryption()
        val testData = "Banana Pancakes".toByteString()

        val encrypted = crypti.encrypt(testData)
        val decrypted = crypti.decrypt(encrypted)

        testData shouldBe decrypted

        decrypted shouldBe crypti.decrypt(encrypted)
        crypti.encrypt(testData) shouldBe encrypted

        crypti.exportKeyset() shouldBe crypti.exportKeyset()
    }

    @Test
    fun `pass existing encryption key`() {
        val keyset = eu.darken.octi.sync.core.encryption.PayloadEncryption.KeySet(
            type = "AES256_SIV",
            key = "CMyhkP8HEoQBCngKMHR5cGUuZ29vZ2xlYXBpcy5jb20vZ29vZ2xlLmNyeXB0by50aW5rLkFlc1NpdktleRJCEkDAEayVsnPs8JIV0wrVP3EuGuM8dEkIxw0gqyuNJGDqJAQoAJB44ZS9JayECYZ/mUv13oslpQ+Vxjj98je6InGMGAEQARjMoZD/ByAB".decodeBase64()!!
        )
        val encryptedData = "AX/kEMzK2BAPQAjQuafZ3k/kF4fiwsEYAu5h/exhpZaeYkU0".decodeBase64()!!
        val testData = "Banana Pancakes".toByteString()

        val crypti = eu.darken.octi.sync.core.encryption.PayloadEncryption(keyset)

        val decrypted = crypti.decrypt(encryptedData)

        testData shouldBe decrypted

        crypti.exportKeyset() shouldBe keyset
    }

}
