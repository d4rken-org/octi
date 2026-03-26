package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class OctiServerSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `Address round-trip`() {
        val address = OctiServer.Address(domain = "prod.kserver.octi.darken.eu")
        val encoded = json.encodeToString(address)
        val decoded = json.decodeFromString<OctiServer.Address>(encoded)
        decoded shouldBe address
    }

    @Test
    fun `Address wire format with defaults`() {
        val address = OctiServer.Address(domain = "prod.kserver.octi.darken.eu")
        val encoded = json.encodeToString(address)
        encoded.toComparableJson() shouldBe """
            {
                "domain": "prod.kserver.octi.darken.eu",
                "protocol": "https",
                "port": 443
            }
        """.toComparableJson()
    }

    @Test
    fun `Credentials wire key uses serverAdress typo`() {
        val credentials = OctiServer.Credentials(
            serverAdress = OctiServer.Address(domain = "example.com"),
            accountId = OctiServer.Credentials.AccountId(id = "acc-1"),
            devicePassword = OctiServer.Credentials.DevicePassword(password = "secret"),
            encryptionKeyset = PayloadEncryption.KeySet(
                type = "AES256_SIV",
                key = "testkey".encodeUtf8(),
            ),
            createdAt = Instant.parse("2024-06-15T12:00:00Z"),
        )
        val encoded = json.encodeToString(credentials)
        encoded.contains("\"serverAdress\"") shouldBe true
        encoded.contains("\"serverAddress\"") shouldBe false
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written Credentials with serverAdress typo`() {
        val moshiJson = """
            {
                "serverAdress": {"domain": "prod.kserver.octi.darken.eu", "protocol": "https", "port": 443},
                "accountId": {"id": "test-acc"},
                "devicePassword": {"password": "test-pw"},
                "encryptionKeyset": {"type": "AES256_SIV", "key": "dGVzdGtleQ=="},
                "createdAt": "2024-01-01T00:00:00Z"
            }
        """
        val decoded = json.decodeFromString<OctiServer.Credentials>(moshiJson)
        decoded.serverAdress.domain shouldBe "prod.kserver.octi.darken.eu"
        decoded.accountId.id shouldBe "test-acc"
        decoded.devicePassword.password shouldBe "test-pw"
    }

    @Test
    fun `Official enum wire names are stable`() {
        json.encodeToString(OctiServer.Official.PROD) shouldBe "\"PROD\""
        json.encodeToString(OctiServer.Official.BETA) shouldBe "\"BETA\""
        json.encodeToString(OctiServer.Official.LOCAL) shouldBe "\"LOCAL\""
    }

    @Test
    fun `AccountId round-trip`() {
        val id = OctiServer.Credentials.AccountId(id = "test-123")
        val encoded = json.encodeToString(id)
        val decoded = json.decodeFromString<OctiServer.Credentials.AccountId>(encoded)
        decoded shouldBe id
    }

    @Test
    fun `LinkCode round-trip`() {
        val code = OctiServer.Credentials.LinkCode(code = "ABCD1234")
        val encoded = json.encodeToString(code)
        val decoded = json.decodeFromString<OctiServer.Credentials.LinkCode>(encoded)
        decoded shouldBe code
    }

    @Test
    fun `RegisterResponse backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """{"account":"acc-xyz","password":"pw-123"}"""
        val decoded = json.decodeFromString<OctiServerApi.RegisterResponse>(moshiJson)
        decoded.accountID shouldBe "acc-xyz"
        decoded.password shouldBe "pw-123"
    }

    @Test
    fun `ShareCodeResponse backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """{"code":"ABCD-1234-EFGH"}"""
        val decoded = json.decodeFromString<OctiServerApi.ShareCodeResponse>(moshiJson)
        decoded.shareCode shouldBe "ABCD-1234-EFGH"
    }

    @Test
    fun `DevicesResponse backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """
            {
                "devices": [
                    {"id": "device-1", "version": "1.0.0"},
                    {"id": "device-2", "version": null}
                ]
            }
        """
        val decoded = json.decodeFromString<OctiServerApi.DevicesResponse>(moshiJson)
        decoded.devices.size shouldBe 2
        decoded.devices[0].id shouldBe "device-1"
        decoded.devices[0].version shouldBe "1.0.0"
        decoded.devices[1].id shouldBe "device-2"
        decoded.devices[1].version shouldBe null
    }

    @Test
    fun `ResetRequest round-trip`() {
        val request = OctiServerApi.ResetRequest(
            targets = setOf(
                eu.darken.octi.sync.core.DeviceId(id = "dev-1"),
                eu.darken.octi.sync.core.DeviceId(id = "dev-2"),
            ),
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<OctiServerApi.ResetRequest>(encoded)
        decoded.targets shouldBe request.targets
    }

    @Test
    fun `forward compatibility - unknown fields in Address`() {
        val futureJson = """{"domain":"example.com","protocol":"https","port":443,"region":"us-east"}"""
        val decoded = json.decodeFromString<OctiServer.Address>(futureJson)
        decoded.domain shouldBe "example.com"
    }
}
