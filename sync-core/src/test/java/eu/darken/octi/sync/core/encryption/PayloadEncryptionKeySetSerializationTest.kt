package eu.darken.octi.sync.core.encryption

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class PayloadEncryptionKeySetSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `round-trip serialization`() {
        val keyset = PayloadEncryption.KeySet(
            type = "AES256_SIV",
            key = "testkey".encodeUtf8(),
        )
        val encoded = json.encodeToString(keyset)
        val decoded = json.decodeFromString<PayloadEncryption.KeySet>(encoded)
        decoded shouldBe keyset
    }

    @Test
    fun `wire format stability`() {
        val keyset = PayloadEncryption.KeySet(
            type = "AES256_SIV",
            key = "testkey".encodeUtf8(),
        )
        val encoded = json.encodeToString(keyset)
        encoded.toComparableJson() shouldBe """
            {
                "type": "AES256_SIV",
                "key": "dGVzdGtleQ=="
            }
        """.toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """{"type":"AES256_SIV","key":"dGVzdGtleQ=="}"""
        val decoded = json.decodeFromString<PayloadEncryption.KeySet>(moshiJson)
        decoded.type shouldBe "AES256_SIV"
        decoded.key shouldBe "testkey".encodeUtf8()
    }
}
