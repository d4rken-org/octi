package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import kotlin.time.Clock
import kotlin.time.Instant

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
    fun `Credentials wire format stability`() {
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
        // Note: uses "serverAdress" typo (legacy Moshi name), unlike LinkingData's "serverAddress".
        encoded.toComparableJson() shouldBe """
            {
                "serverAdress": {
                    "domain": "example.com",
                    "protocol": "https",
                    "port": 443
                },
                "accountId": {"id": "acc-1"},
                "devicePassword": {"password": "secret"},
                "encryptionKeyset": {
                    "type": "AES256_SIV",
                    "key": "dGVzdGtleQ=="
                },
                "createdAt": "2024-06-15T12:00:00Z"
            }
        """.toComparableJson()
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

    @Test
    fun `AccountStorageResponse v1 wire format - new fields default to null`() {
        val v1Json = """
            {
                "storageApiVersion": 1,
                "accountQuotaBytes": 52428800,
                "usedBytes": 1024,
                "reservedBytes": 0,
                "availableBytes": 52427776,
                "maxBlobBytes": 10485760,
                "maxModuleDocumentBytes": 262144,
                "maxActiveUploadSessionsPerDevice": 8
            }
        """
        val decoded = json.decodeFromString<OctiServerApi.AccountStorageResponse>(v1Json)
        decoded.storageApiVersion shouldBe 1
        decoded.accountQuotaBytes shouldBe 52428800L
        decoded.usedBytes shouldBe 1024L
        decoded.idleSessionTtlSeconds shouldBe null
        decoded.absoluteSessionTtlSeconds shouldBe null
        decoded.maxDevicesPerAccount shouldBe null
        decoded.maxModulesPerDevice shouldBe null
        decoded.maxBlobRefsPerModule shouldBe null
        decoded.maxActiveUploadSessionsPerAccount shouldBe null
        decoded.completeIdleTtlSeconds shouldBe null
        decoded.accountRateLimit shouldBe null
        decoded.accountRateLimitWindowSeconds shouldBe null
    }

    @Test
    fun `AccountStorageResponse v2 wire format - all fields decode`() {
        val v2Json = """
            {
                "storageApiVersion": 2,
                "accountQuotaBytes": 52428800,
                "usedBytes": 1024,
                "reservedBytes": 512,
                "availableBytes": 52427264,
                "maxBlobBytes": 10485760,
                "maxModuleDocumentBytes": 262144,
                "maxActiveUploadSessionsPerDevice": 8,
                "idleSessionTtlSeconds": 3600,
                "absoluteSessionTtlSeconds": 86400,
                "maxDevicesPerAccount": 64,
                "maxModulesPerDevice": 256,
                "maxBlobRefsPerModule": 64,
                "maxActiveUploadSessionsPerAccount": 32,
                "completeIdleTtlSeconds": 600,
                "accountRateLimit": 256,
                "accountRateLimitWindowSeconds": 60
            }
        """
        val decoded = json.decodeFromString<OctiServerApi.AccountStorageResponse>(v2Json)
        decoded.storageApiVersion shouldBe 2
        decoded.idleSessionTtlSeconds shouldBe 3600L
        decoded.absoluteSessionTtlSeconds shouldBe 86400L
        decoded.maxDevicesPerAccount shouldBe 64
        decoded.maxModulesPerDevice shouldBe 256
        decoded.maxBlobRefsPerModule shouldBe 64
        decoded.maxActiveUploadSessionsPerAccount shouldBe 32
        decoded.completeIdleTtlSeconds shouldBe 600L
        decoded.accountRateLimit shouldBe 256
        decoded.accountRateLimitWindowSeconds shouldBe 60L
    }
}
