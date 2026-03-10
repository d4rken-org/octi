package eu.darken.octi.sync.core

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class DeviceIdSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `round-trip serialization`() {
        val deviceId = DeviceId(id = "test-device-123")
        val encoded = json.encodeToString(deviceId)
        val decoded = json.decodeFromString<DeviceId>(encoded)
        decoded shouldBe deviceId
    }

    @Test
    fun `wire format stability`() {
        val deviceId = DeviceId(id = "test-device-123")
        val encoded = json.encodeToString(deviceId)
        encoded.toComparableJson() shouldBe """{"id":"test-device-123"}""".toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """{"id":"abc-def-ghi"}"""
        val decoded = json.decodeFromString<DeviceId>(moshiJson)
        decoded.id shouldBe "abc-def-ghi"
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """{"id":"test-123","unknownField":"surprise"}"""
        val decoded = json.decodeFromString<DeviceId>(futureJson)
        decoded.id shouldBe "test-123"
    }
}
