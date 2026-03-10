package eu.darken.octi.modules.power.core

import eu.darken.octi.common.collections.toByteString
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class PowerInfoSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val fullInfo = PowerInfo(
        status = PowerInfo.Status.CHARGING,
        battery = PowerInfo.Battery(
            level = 75,
            scale = 100,
            health = 2,
            temp = 28.5f,
        ),
        chargeIO = PowerInfo.ChargeIO(
            currentNow = 1500000,
            currenAvg = 1200000,
            fullSince = null,
            fullAt = Instant.parse("2024-06-15T14:00:00Z"),
            emptyAt = null,
        ),
    )

    @Test
    fun `round-trip serialization`() {
        val encoded = json.encodeToString(fullInfo)
        val decoded = json.decodeFromString<PowerInfo>(encoded)
        decoded shouldBe fullInfo
    }

    @Test
    fun `wire key mismatch - currentAvg maps to currenAvg property`() {
        val moshiJson = """
            {
                "status": "CHARGING",
                "battery": {"level": 50, "scale": 100, "health": 2, "temp": 25.0},
                "chargeIO": {
                    "currentNow": 1000000,
                    "currentAvg": 900000,
                    "fullAt": "2024-06-15T14:00:00Z"
                }
            }
        """
        val decoded = json.decodeFromString<PowerInfo>(moshiJson)
        decoded.chargeIO.currenAvg shouldBe 900000
    }

    @Test
    fun `wire format uses currentAvg not currenAvg`() {
        val encoded = json.encodeToString(fullInfo)
        encoded.contains("\"currentAvg\"") shouldBe true
        encoded.contains("\"currenAvg\"") shouldBe false
    }

    @Test
    fun `Status enum wire names are stable`() {
        json.encodeToString(PowerInfo.Status.FULL) shouldBe "\"FULL\""
        json.encodeToString(PowerInfo.Status.CHARGING) shouldBe "\"CHARGING\""
        json.encodeToString(PowerInfo.Status.DISCHARGING) shouldBe "\"DISCHARGING\""
        json.encodeToString(PowerInfo.Status.UNKNOWN) shouldBe "\"UNKNOWN\""
    }

    @Test
    fun `Speed enum wire names are stable`() {
        json.encodeToString(PowerInfo.ChargeIO.Speed.SLOW) shouldBe "\"SLOW\""
        json.encodeToString(PowerInfo.ChargeIO.Speed.NORMAL) shouldBe "\"NORMAL\""
        json.encodeToString(PowerInfo.ChargeIO.Speed.FAST) shouldBe "\"FAST\""
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written JSON with all fields`() {
        val moshiJson = """
            {
                "status": "DISCHARGING",
                "battery": {
                    "level": 42,
                    "scale": 100,
                    "health": null,
                    "temp": null
                },
                "chargeIO": {
                    "currentNow": null,
                    "currentAvg": null,
                    "fullSince": null,
                    "fullAt": null,
                    "emptyAt": "2024-06-15T18:30:00Z"
                }
            }
        """
        val decoded = json.decodeFromString<PowerInfo>(moshiJson)
        decoded.status shouldBe PowerInfo.Status.DISCHARGING
        decoded.battery.level shouldBe 42
        decoded.battery.health shouldBe null
        decoded.chargeIO.currentNow shouldBe null
        decoded.chargeIO.currenAvg shouldBe null
        decoded.chargeIO.emptyAt shouldBe Instant.parse("2024-06-15T18:30:00Z")
    }

    @Test
    fun `null fields are omitted from JSON`() {
        val info = fullInfo.copy(
            chargeIO = fullInfo.chargeIO.copy(
                currentNow = null,
                currenAvg = null,
            ),
        )
        val encoded = json.encodeToString(info)
        encoded.contains("currentNow") shouldBe false
        encoded.contains("currentAvg") shouldBe false
    }

    @Test
    fun `wire format stability`() {
        val encoded = json.encodeToString(fullInfo)
        encoded.toComparableJson() shouldBe """
            {
                "status": "CHARGING",
                "battery": {
                    "level": 75,
                    "scale": 100,
                    "health": 2,
                    "temp": 28.5
                },
                "chargeIO": {
                    "currentNow": 1500000,
                    "currentAvg": 1200000,
                    "fullAt": "2024-06-15T14:00:00Z"
                }
            }
        """.toComparableJson()
    }

    @Test
    fun `PowerSerializer round-trip via ByteString`() {
        val serializer = PowerSerializer(json)
        val bytes = serializer.serialize(fullInfo)
        val deserialized = serializer.deserialize(bytes)
        deserialized shouldBe fullInfo
    }

    @Test
    fun `PowerSerializer deserializes Moshi-written ByteString payload`() {
        val moshiPayload = """
            {
                "status": "DISCHARGING",
                "battery": {"level": 42, "scale": 100, "health": 2, "temp": 30.0},
                "chargeIO": {
                    "currentNow": null,
                    "currentAvg": 800000,
                    "fullSince": null,
                    "fullAt": null,
                    "emptyAt": "2024-06-15T18:30:00Z"
                }
            }
        """.toByteString()
        val serializer = PowerSerializer(json)
        val result = serializer.deserialize(moshiPayload)
        result.status shouldBe PowerInfo.Status.DISCHARGING
        result.battery.level shouldBe 42
        result.battery.temp shouldBe 30.0f
        result.chargeIO.currentNow shouldBe null
        result.chargeIO.currenAvg shouldBe 800000
        result.chargeIO.emptyAt shouldBe Instant.parse("2024-06-15T18:30:00Z")
    }

    @Test
    fun `PowerSerializer serialize output matches Moshi wire format`() {
        val serializer = PowerSerializer(json)
        val bytes = serializer.serialize(fullInfo)
        bytes.utf8().toComparableJson() shouldBe """
            {
                "status": "CHARGING",
                "battery": {"level": 75, "scale": 100, "health": 2, "temp": 28.5},
                "chargeIO": {"currentNow": 1500000, "currentAvg": 1200000, "fullAt": "2024-06-15T14:00:00Z"}
            }
        """.toComparableJson()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            {
                "status": "CHARGING",
                "battery": {"level": 80, "scale": 100, "health": 2, "temp": 30.0},
                "chargeIO": {"currentNow": 2000000, "currentAvg": 1800000},
                "batteryTechnology": "Li-ion"
            }
        """
        val decoded = json.decodeFromString<PowerInfo>(futureJson)
        decoded.battery.level shouldBe 80
    }
}
