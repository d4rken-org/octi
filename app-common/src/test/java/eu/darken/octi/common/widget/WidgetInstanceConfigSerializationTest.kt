package eu.darken.octi.common.widget

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class WidgetInstanceConfigSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `round-trip - default config`() {
        val original = WidgetInstanceConfig.DEFAULT
        val decoded = json.decodeFromString<WidgetInstanceConfig>(json.encodeToString(original))
        decoded shouldBe original
    }

    @Test
    fun `round-trip - fully populated custom config`() {
        val original = WidgetInstanceConfig(
            isMaterialYou = false,
            presetName = "DARK",
            customBg = -12345,
            customAccent = -67890,
            allowedDeviceIds = setOf("dev-a", "dev-b"),
        )
        val decoded = json.decodeFromString<WidgetInstanceConfig>(json.encodeToString(original))
        decoded shouldBe original
    }

    @Test
    fun `wire format - default config`() {
        val encoded = json.encodeToString(WidgetInstanceConfig.DEFAULT)
        encoded.toComparableJson() shouldBe """
            {
                "isMaterialYou": true,
                "allowedDeviceIds": []
            }
        """.toComparableJson()
    }

    @Test
    fun `wire format - material you preset is normalized`() {
        val encoded = json.encodeToString(
            WidgetInstanceConfig(
                isMaterialYou = true,
                presetName = WidgetTheme.MATERIAL_YOU.name,
                customBg = null,
                customAccent = null,
                allowedDeviceIds = setOf("dev-a"),
            ),
        )
        encoded.toComparableJson() shouldBe """
            {
                "isMaterialYou": true,
                "presetName": "MATERIAL_YOU",
                "allowedDeviceIds": ["dev-a"]
            }
        """.toComparableJson()
    }

    @Test
    fun `wire format - fully populated custom config`() {
        val encoded = json.encodeToString(
            WidgetInstanceConfig(
                isMaterialYou = false,
                presetName = "DARK",
                customBg = -12345,
                customAccent = -67890,
                allowedDeviceIds = setOf("dev-a", "dev-b"),
            ),
        )
        encoded.toComparableJson() shouldBe """
            {
                "isMaterialYou": false,
                "presetName": "DARK",
                "customBg": -12345,
                "customAccent": -67890,
                "allowedDeviceIds": ["dev-a", "dev-b"]
            }
        """.toComparableJson()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            {
                "isMaterialYou": false,
                "presetName": "DARK",
                "customBg": 1,
                "customAccent": 2,
                "allowedDeviceIds": ["x"],
                "futureField": "ignore me",
                "newNestedThing": {"nested": true}
            }
        """.trimIndent()
        val decoded = json.decodeFromString<WidgetInstanceConfig>(futureJson)
        decoded shouldBe WidgetInstanceConfig(
            isMaterialYou = false,
            presetName = "DARK",
            customBg = 1,
            customAccent = 2,
            allowedDeviceIds = setOf("x"),
        )
    }

    @Test
    fun `decode - omitted optional fields default to null or empty`() {
        val minimal = """{"isMaterialYou":true,"allowedDeviceIds":[]}"""
        json.decodeFromString<WidgetInstanceConfig>(minimal) shouldBe WidgetInstanceConfig.DEFAULT
    }
}
