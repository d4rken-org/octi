package eu.darken.octi.module.core

import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class CachedModuleDataSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val testData = BaseModuleCache.CachedModuleData(
        modifiedAt = Instant.parse("2024-06-15T12:00:00Z"),
        deviceId = DeviceId(id = "device-abc"),
        moduleId = ModuleId(id = "eu.darken.octi.module.power"),
        data = "test-payload-data".encodeUtf8(),
    )

    @Test
    fun `round-trip serialization`() {
        val encoded = json.encodeToString(testData)
        val decoded = json.decodeFromString<BaseModuleCache.CachedModuleData>(encoded)
        decoded shouldBe testData
    }

    @Test
    fun `wire format stability`() {
        val encoded = json.encodeToString(testData)
        encoded.toComparableJson() shouldBe """
            {
                "modifiedAt": "2024-06-15T12:00:00Z",
                "deviceId": {"id": "device-abc"},
                "moduleId": {"id": "eu.darken.octi.module.power"},
                "data": "dGVzdC1wYXlsb2FkLWRhdGE="
            }
        """.toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written cache file`() {
        val moshiJson = """
            {
                "modifiedAt": "2024-01-01T00:00:00Z",
                "deviceId": {"id": "old-device"},
                "moduleId": {"id": "eu.darken.octi.module.meta"},
                "data": "SGVsbG8gV29ybGQ="
            }
        """
        val decoded = json.decodeFromString<BaseModuleCache.CachedModuleData>(moshiJson)
        decoded.modifiedAt shouldBe Instant.parse("2024-01-01T00:00:00Z")
        decoded.deviceId shouldBe DeviceId(id = "old-device")
        decoded.moduleId shouldBe ModuleId(id = "eu.darken.octi.module.meta")
        decoded.data shouldBe "Hello World".encodeUtf8()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            {
                "modifiedAt": "2024-06-15T12:00:00Z",
                "deviceId": {"id": "dev-1"},
                "moduleId": {"id": "eu.darken.octi.module.power"},
                "data": "dGVzdA==",
                "cacheVersion": 2,
                "checksum": "abc123"
            }
        """
        val decoded = json.decodeFromString<BaseModuleCache.CachedModuleData>(futureJson)
        decoded.deviceId shouldBe DeviceId(id = "dev-1")
    }

    @Test
    fun `data field uses base64 encoding for ByteString`() {
        val encoded = json.encodeToString(testData)
        // "test-payload-data" in base64 is "dGVzdC1wYXlsb2FkLWRhdGE="
        encoded.contains("\"dGVzdC1wYXlsb2FkLWRhdGE=\"") shouldBe true
    }
}
