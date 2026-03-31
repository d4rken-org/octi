package eu.darken.octi.sync.core.encryption

import eu.darken.octi.common.collections.toByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okio.ByteString.Companion.decodeBase64
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.security.GeneralSecurityException

class PayloadEncryptionTest : BaseTest() {

    @Test
    fun `encrypt and decrypt`() {
        val crypti = PayloadEncryption()
        val testData = "Banana Pancakes".toByteString()

        val encrypted = crypti.encrypt(testData)
        val decrypted = crypti.decrypt(encrypted)

        testData shouldBe decrypted

        decrypted shouldBe crypti.decrypt(encrypted)
        // GCM-SIV is non-deterministic, so re-encrypting gives different ciphertext
        crypti.encrypt(testData) shouldNotBe encrypted

        crypti.exportKeyset() shouldBe crypti.exportKeyset()
    }

    @Test
    fun `pass existing encryption key`() {
        val keyset = PayloadEncryption.KeySet(
            type = "AES256_SIV",
            key = "CMyhkP8HEoQBCngKMHR5cGUuZ29vZ2xlYXBpcy5jb20vZ29vZ2xlLmNyeXB0by50aW5rLkFlc1NpdktleRJCEkDAEayVsnPs8JIV0wrVP3EuGuM8dEkIxw0gqyuNJGDqJAQoAJB44ZS9JayECYZ/mUv13oslpQ+Vxjj98je6InGMGAEQARjMoZD/ByAB".decodeBase64()!!
        )
        val encryptedData = "AX/kEMzK2BAPQAjQuafZ3k/kF4fiwsEYAu5h/exhpZaeYkU0".decodeBase64()!!
        val testData = "Banana Pancakes".toByteString()

        val crypti = PayloadEncryption(keyset)

        val decrypted = crypti.decrypt(encryptedData)

        testData shouldBe decrypted

        crypti.exportKeyset() shouldBe keyset
    }

    @Test
    fun `old keyset round-trips through export and re-import`() {
        val keyset = PayloadEncryption.KeySet(
            type = "AES256_SIV",
            key = "CMyhkP8HEoQBCngKMHR5cGUuZ29vZ2xlYXBpcy5jb20vZ29vZ2xlLmNyeXB0by50aW5rLkFlc1NpdktleRJCEkDAEayVsnPs8JIV0wrVP3EuGuM8dEkIxw0gqyuNJGDqJAQoAJB44ZS9JayECYZ/mUv13oslpQ+Vxjj98je6InGMGAEQARjMoZD/ByAB".decodeBase64()!!
        )
        val testData = "Banana Pancakes".toByteString()

        val crypti1 = PayloadEncryption(keyset)
        val exported = crypti1.exportKeyset()
        exported shouldBe keyset

        val crypti2 = PayloadEncryption(exported)
        val encrypted = crypti1.encrypt(testData)
        crypti2.decrypt(encrypted) shouldBe testData
    }

    @Test
    fun `GCM-SIV encrypt and decrypt with associated data`() {
        val crypti = PayloadEncryption()
        val testData = "Banana Pancakes".toByteString()
        val ad = "device1:module1".toByteArray()

        val encrypted = crypti.encrypt(testData, ad)
        val decrypted = crypti.decrypt(encrypted, ad)

        decrypted shouldBe testData
    }

    @Test
    fun `GCM-SIV decrypt fails with wrong associated data`() {
        val crypti = PayloadEncryption()
        val testData = "Banana Pancakes".toByteString()
        val ad = "device1:module1".toByteArray()
        val wrongAd = "device2:module2".toByteArray()

        val encrypted = crypti.encrypt(testData, ad)

        shouldThrow<GeneralSecurityException> {
            crypti.decrypt(encrypted, wrongAd)
        }
    }

    @Test
    fun `GCM-SIV is non-deterministic`() {
        val crypti = PayloadEncryption()
        val testData = "Banana Pancakes".toByteString()

        val encrypted1 = crypti.encrypt(testData)
        val encrypted2 = crypti.encrypt(testData)

        encrypted1 shouldNotBe encrypted2
    }

    @Test
    fun `GCM-SIV keyset round-trips through export and re-import`() {
        val crypti1 = PayloadEncryption()
        val exported = crypti1.exportKeyset()

        exported.type shouldBe "AES256_GCM_SIV"

        val crypti2 = PayloadEncryption(exported)
        val testData = "Banana Pancakes".toByteString()
        val ad = "device1:module1".toByteArray()

        val encrypted = crypti1.encrypt(testData, ad)
        crypti2.decrypt(encrypted, ad) shouldBe testData
    }

    @Test
    fun `SIV ignores associated data parameter`() {
        val crypti = PayloadEncryption(useLegacyEncryption = true)
        val testData = "Banana Pancakes".toByteString()
        val ad = "device1:module1".toByteArray()

        val encrypted = crypti.encrypt(testData, ad)

        // SIV ignores AD, so decrypting without AD should work
        crypti.decrypt(encrypted) shouldBe testData
        // And with different AD should also work
        crypti.decrypt(encrypted, "other".toByteArray()) shouldBe testData
    }

    @Test
    fun `legacy mode generates SIV keyset`() {
        val crypti = PayloadEncryption(useLegacyEncryption = true)
        crypti.exportKeyset().type shouldBe "AES256_SIV"
    }
}
