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
                "deviceOrder": [],
                "isSyncExpanded": false,
                "defaultTileLayout": {
                    "order": [
                        "eu.darken.octi.module.core.power",
                        "eu.darken.octi.module.core.wifi",
                        "eu.darken.octi.module.core.connectivity",
                        "eu.darken.octi.module.core.apps",
                        "eu.darken.octi.module.core.clipboard"
                    ],
                    "wideModules": ["eu.darken.octi.module.core.power"],
                    "hiddenModules": []
                },
                "deviceTileLayouts": {}
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
        decoded.defaultTileLayout shouldBe TileLayoutConfig()
        decoded.deviceTileLayouts shouldBe emptyMap()
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

    @Test
    fun `backward compatibility - old JSON without tile layout fields uses defaults`() {
        val oldJson = """
            {
                "collapsedDevices": ["dev-1"],
                "deviceOrder": ["dev-1"],
                "isSyncExpanded": true
            }
        """
        val decoded = json.decodeFromString<DashboardConfig>(oldJson)
        decoded.defaultTileLayout shouldBe TileLayoutConfig()
        decoded.deviceTileLayouts shouldBe emptyMap()
        decoded.isSyncExpanded shouldBe true
    }

    @Test
    fun `round-trip with tile layouts`() {
        val config = DashboardConfig(
            defaultTileLayout = TileLayoutConfig(
                order = listOf("eu.darken.octi.module.core.power", "eu.darken.octi.module.core.wifi"),
                wideModules = setOf("eu.darken.octi.module.core.power"),
                hiddenModules = setOf("eu.darken.octi.module.core.wifi"),
            ),
            deviceTileLayouts = mapOf(
                "device-1" to TileLayoutConfig(
                    order = listOf("eu.darken.octi.module.core.wifi", "eu.darken.octi.module.core.power"),
                    wideModules = emptySet(),
                    hiddenModules = emptySet(),
                ),
            ),
        )
        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<DashboardConfig>(encoded)
        decoded shouldBe config
    }

    @Test
    fun `toCleaned removes stale device tile layouts`() {
        val config = DashboardConfig(
            collapsedDevices = setOf("dev-1", "dev-2"),
            deviceTileLayouts = mapOf(
                "dev-1" to TileLayoutConfig(),
                "dev-gone" to TileLayoutConfig(),
            ),
        )
        val cleaned = config.toCleaned(setOf("dev-1"))
        cleaned.collapsedDevices shouldBe setOf("dev-1")
        cleaned.deviceTileLayouts.keys shouldBe setOf("dev-1")
    }

    @Test
    fun `effectiveLayout returns per-device when available`() {
        val perDevice = TileLayoutConfig(order = listOf("a", "b"))
        val config = DashboardConfig(
            defaultTileLayout = TileLayoutConfig(),
            deviceTileLayouts = mapOf("dev-1" to perDevice),
        )
        config.effectiveLayout("dev-1") shouldBe perDevice
        config.effectiveLayout("dev-2") shouldBe TileLayoutConfig()
    }
}
