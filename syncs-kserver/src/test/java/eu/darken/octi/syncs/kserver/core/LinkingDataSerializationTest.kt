package eu.darken.octi.syncs.kserver.core

import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LinkingDataSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val testData = LinkingData(
        serverAdress = KServer.Address(domain = "prod.kserver.octi.darken.eu"),
        linkCode = KServer.Credentials.LinkCode(code = "ABCD1234"),
        encryptionKeyset = PayloadEncryption.KeySet(
            type = "AES256_SIV",
            key = "testkey".encodeUtf8(),
        ),
    )

    @Test
    fun `round-trip serialization`() {
        val encoded = json.encodeToString(testData)
        val decoded = json.decodeFromString<LinkingData>(encoded)
        decoded shouldBe testData
    }

    @Test
    fun `encoded string round-trip`() {
        val encoded = testData.toEncodedString(json)
        val decoded = LinkingData.fromEncodedString(json, encoded)
        decoded shouldBe testData
    }

    @Test
    fun `wire format uses serverAddress not serverAdress`() {
        val encoded = json.encodeToString(testData)
        // Note: LinkingData uses "serverAddress" (correct spelling), unlike KServer.Credentials
        encoded.contains("\"serverAddress\"") shouldBe true
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """
            {
                "serverAddress": {"domain": "prod.kserver.octi.darken.eu", "protocol": "https", "port": 443},
                "shareCode": {"code": "XYZ"},
                "encryptionKeySet": {"type": "AES256_SIV", "key": "dGVzdGtleQ=="}
            }
        """
        val decoded = json.decodeFromString<LinkingData>(moshiJson)
        decoded.serverAdress.domain shouldBe "prod.kserver.octi.darken.eu"
        decoded.linkCode.code shouldBe "XYZ"
    }
}
