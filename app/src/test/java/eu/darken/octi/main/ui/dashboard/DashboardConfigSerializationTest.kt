package eu.darken.octi.main.ui.dashboard

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class DashboardConfigSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `round-trip serialization`() {
        val config = DashboardConfig(
            collapsedDevices = setOf("device-1", "device-2"),
            deviceOrder = listOf("device-2", "device-1"),
        )
        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<DashboardConfig>(encoded)
        decoded shouldBe config
    }

    @Test
    fun `default values serialization`() {
        val config = DashboardConfig()
        val encoded = json.encodeToString(config)
        encoded.toComparableJson() shouldBe """
            {
                "collapsedDevices": [],
                "deviceOrder": []
            }
        """.toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """
            {
                "collapsedDevices": ["dev-abc", "dev-xyz"],
                "deviceOrder": ["dev-xyz", "dev-abc"]
            }
        """
        val decoded = json.decodeFromString<DashboardConfig>(moshiJson)
        decoded.collapsedDevices shouldBe setOf("dev-abc", "dev-xyz")
        decoded.deviceOrder shouldBe listOf("dev-xyz", "dev-abc")
    }

    @Test
    fun `backward compatibility - empty JSON uses defaults`() {
        val emptyJson = """{}"""
        val decoded = json.decodeFromString<DashboardConfig>(emptyJson)
        decoded.collapsedDevices shouldBe emptySet()
        decoded.deviceOrder shouldBe emptyList()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            {
                "collapsedDevices": ["dev-1"],
                "deviceOrder": [],
                "newFeature": true
            }
        """
        val decoded = json.decodeFromString<DashboardConfig>(futureJson)
        decoded.collapsedDevices shouldBe setOf("dev-1")
    }

    @Test
    fun `set deserialization is order-independent`() {
        val json1 = """{"collapsedDevices":["a","b"],"deviceOrder":[]}"""
        val json2 = """{"collapsedDevices":["b","a"],"deviceOrder":[]}"""
        val decoded1 = json.decodeFromString<DashboardConfig>(json1)
        val decoded2 = json.decodeFromString<DashboardConfig>(json2)
        decoded1.collapsedDevices shouldBe decoded2.collapsedDevices
    }
}
