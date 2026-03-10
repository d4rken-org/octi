package eu.darken.octi.modules.power.core.alert

import eu.darken.octi.sync.core.DeviceId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class PowerAlertRuleSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `round-trip BatteryLowAlertRule`() {
        val rule: PowerAlertRule = BatteryLowAlertRule(
            deviceId = DeviceId(id = "device-1"),
            threshold = 0.15f,
        )
        val encoded = json.encodeToString(PowerAlertRule.serializer(), rule)
        val decoded = json.decodeFromString(PowerAlertRule.serializer(), encoded)
        decoded shouldBe rule
    }

    @Test
    fun `round-trip BatteryHighAlertRule`() {
        val rule: PowerAlertRule = BatteryHighAlertRule(
            deviceId = DeviceId(id = "device-2"),
            threshold = 0.9f,
        )
        val encoded = json.encodeToString(PowerAlertRule.serializer(), rule)
        val decoded = json.decodeFromString(PowerAlertRule.serializer(), encoded)
        decoded shouldBe rule
    }

    @Test
    fun `BatteryLowAlertRule wire format`() {
        val rule: PowerAlertRule = BatteryLowAlertRule(
            deviceId = DeviceId(id = "x"),
            threshold = 0.15f,
        )
        val encoded = json.encodeToString(PowerAlertRule.serializer(), rule)
        encoded.toComparableJson() shouldBe """
            {
                "type": "BATTERY_LOW",
                "deviceId": {"id": "x"},
                "threshold": 0.15
            }
        """.toComparableJson()
    }

    @Test
    fun `BatteryHighAlertRule wire format`() {
        val rule: PowerAlertRule = BatteryHighAlertRule(
            deviceId = DeviceId(id = "x"),
            threshold = 0.9f,
        )
        val encoded = json.encodeToString(PowerAlertRule.serializer(), rule)
        encoded.toComparableJson() shouldBe """
            {
                "type": "BATTERY_HIGH",
                "deviceId": {"id": "x"},
                "threshold": 0.9
            }
        """.toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize BATTERY_LOW from Moshi`() {
        val moshiJson = """{"type":"BATTERY_LOW","deviceId":{"id":"dev-1"},"threshold":0.15}"""
        val decoded = json.decodeFromString(PowerAlertRule.serializer(), moshiJson)
        decoded.shouldBeInstanceOf<BatteryLowAlertRule>()
        decoded.deviceId shouldBe DeviceId(id = "dev-1")
        (decoded as BatteryLowAlertRule).threshold shouldBe 0.15f
    }

    @Test
    fun `backward compatibility - deserialize BATTERY_HIGH from Moshi`() {
        val moshiJson = """{"type":"BATTERY_HIGH","deviceId":{"id":"dev-2"},"threshold":0.9}"""
        val decoded = json.decodeFromString(PowerAlertRule.serializer(), moshiJson)
        decoded.shouldBeInstanceOf<BatteryHighAlertRule>()
        (decoded as BatteryHighAlertRule).threshold shouldBe 0.9f
    }

    @Test
    fun `unknown discriminator throws`() {
        val unknownJson = """{"type":"BATTERY_CRITICAL","deviceId":{"id":"dev-3"},"threshold":0.05}"""
        shouldThrow<Exception> {
            json.decodeFromString(PowerAlertRule.serializer(), unknownJson)
        }
    }

    @Test
    fun `Event round-trip`() {
        val event = PowerAlertRule.Event(
            id = "device-1-batterlow",
            triggeredAt = Instant.parse("2024-06-15T12:00:00Z"),
            dismissedAt = null,
        )
        val encoded = json.encodeToString(event)
        val decoded = json.decodeFromString<PowerAlertRule.Event>(encoded)
        decoded.id shouldBe event.id
        decoded.triggeredAt shouldBe event.triggeredAt
        decoded.dismissedAt shouldBe null
    }
}
