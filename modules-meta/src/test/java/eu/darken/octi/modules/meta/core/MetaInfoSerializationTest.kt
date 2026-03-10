package eu.darken.octi.modules.meta.core

import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class MetaInfoSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val fullInfo = MetaInfo(
        deviceLabel = "My Phone",
        deviceId = DeviceId(id = "device-123"),
        octiVersionName = "1.0.0",
        octiGitSha = "abc1234",
        deviceManufacturer = "Google",
        deviceName = "Pixel 8",
        deviceType = MetaInfo.DeviceType.PHONE,
        deviceBootedAt = Instant.parse("2024-01-15T08:00:00Z"),
        androidVersionName = "14",
        androidApiLevel = 34,
        androidSecurityPatch = "2024-01-05",
    )

    @Test
    fun `round-trip serialization`() {
        val encoded = json.encodeToString(fullInfo)
        val decoded = json.decodeFromString<MetaInfo>(encoded)
        decoded shouldBe fullInfo
    }

    @Test
    fun `wire format stability`() {
        val encoded = json.encodeToString(fullInfo)
        encoded.toComparableJson() shouldBe """
            {
                "deviceLabel": "My Phone",
                "deviceId": {"id": "device-123"},
                "octiVersionName": "1.0.0",
                "octiGitSha": "abc1234",
                "deviceManufacturer": "Google",
                "deviceName": "Pixel 8",
                "deviceType": "PHONE",
                "deviceBootedAt": "2024-01-15T08:00:00Z",
                "androidVersionName": "14",
                "androidApiLevel": 34,
                "androidSecurityPatch": "2024-01-05"
            }
        """.toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """
            {
                "deviceLabel": null,
                "deviceId": {"id": "old-device"},
                "octiVersionName": "0.9.0",
                "octiGitSha": "def5678",
                "deviceManufacturer": "Samsung",
                "deviceName": "Galaxy S23",
                "deviceType": "PHONE",
                "deviceBootedAt": "2024-03-01T12:00:00Z",
                "androidVersionName": "13",
                "androidApiLevel": 33,
                "androidSecurityPatch": null
            }
        """
        val decoded = json.decodeFromString<MetaInfo>(moshiJson)
        decoded.deviceLabel shouldBe null
        decoded.deviceId shouldBe DeviceId(id = "old-device")
        decoded.deviceType shouldBe MetaInfo.DeviceType.PHONE
        decoded.androidSecurityPatch shouldBe null
    }

    @Test
    fun `null deviceLabel is omitted from JSON`() {
        val info = fullInfo.copy(deviceLabel = null)
        val encoded = json.encodeToString(info)
        encoded.contains("deviceLabel") shouldBe false
    }

    @Test
    fun `DeviceType enum wire names are stable`() {
        json.encodeToString(MetaInfo.DeviceType.PHONE) shouldBe "\"PHONE\""
        json.encodeToString(MetaInfo.DeviceType.TABLET) shouldBe "\"TABLET\""
        json.encodeToString(MetaInfo.DeviceType.UNKNOWN) shouldBe "\"UNKNOWN\""
    }

    @Test
    fun `MetaSerializer round-trip via ByteString`() {
        val serializer = MetaSerializer(json)
        val bytes = serializer.serialize(fullInfo)
        val deserialized = serializer.deserialize(bytes)
        deserialized shouldBe fullInfo
    }

    @Test
    fun `MetaSerializer deserializes Moshi-written ByteString payload`() {
        val moshiPayload = """
            {
                "deviceLabel": "Work Phone",
                "deviceId": {"id": "moshi-device-1"},
                "octiVersionName": "0.8.0",
                "octiGitSha": "aaa1111",
                "deviceManufacturer": "Samsung",
                "deviceName": "Galaxy S24",
                "deviceType": "PHONE",
                "deviceBootedAt": "2024-03-01T06:00:00Z",
                "androidVersionName": "14",
                "androidApiLevel": 34,
                "androidSecurityPatch": "2024-02-01"
            }
        """.toByteString()
        val serializer = MetaSerializer(json)
        val result = serializer.deserialize(moshiPayload)
        result.deviceLabel shouldBe "Work Phone"
        result.deviceId shouldBe DeviceId(id = "moshi-device-1")
        result.deviceManufacturer shouldBe "Samsung"
        result.deviceType shouldBe MetaInfo.DeviceType.PHONE
        result.deviceBootedAt shouldBe Instant.parse("2024-03-01T06:00:00Z")
    }

    @Test
    fun `MetaSerializer serialize output matches Moshi wire format`() {
        val serializer = MetaSerializer(json)
        val bytes = serializer.serialize(fullInfo)
        bytes.utf8().toComparableJson() shouldBe """
            {
                "deviceLabel": "My Phone",
                "deviceId": {"id": "device-123"},
                "octiVersionName": "1.0.0",
                "octiGitSha": "abc1234",
                "deviceManufacturer": "Google",
                "deviceName": "Pixel 8",
                "deviceType": "PHONE",
                "deviceBootedAt": "2024-01-15T08:00:00Z",
                "androidVersionName": "14",
                "androidApiLevel": 34,
                "androidSecurityPatch": "2024-01-05"
            }
        """.toComparableJson()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            {
                "deviceId": {"id": "dev-1"},
                "octiVersionName": "2.0.0",
                "octiGitSha": "xyz",
                "deviceManufacturer": "Google",
                "deviceName": "Pixel 10",
                "deviceType": "PHONE",
                "deviceBootedAt": "2025-01-01T00:00:00Z",
                "androidVersionName": "16",
                "androidApiLevel": 36,
                "newMetaField": "surprise"
            }
        """
        val decoded = json.decodeFromString<MetaInfo>(futureJson)
        decoded.deviceName shouldBe "Pixel 10"
    }
}
