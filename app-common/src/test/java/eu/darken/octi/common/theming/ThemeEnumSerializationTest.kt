package eu.darken.octi.common.theming

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ThemeEnumSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `ThemeMode wire names are stable`() {
        json.encodeToString(ThemeMode.SYSTEM) shouldBe "\"SYSTEM\""
        json.encodeToString(ThemeMode.DARK) shouldBe "\"DARK\""
        json.encodeToString(ThemeMode.LIGHT) shouldBe "\"LIGHT\""
    }

    @Test
    fun `ThemeMode round-trip`() {
        ThemeMode.entries.forEach { mode ->
            val encoded = json.encodeToString(mode)
            val decoded = json.decodeFromString<ThemeMode>(encoded)
            decoded shouldBe mode
        }
    }

    @Test
    fun `ThemeColor wire names are stable`() {
        json.encodeToString(ThemeColor.GREEN) shouldBe "\"GREEN\""
        json.encodeToString(ThemeColor.BLUE) shouldBe "\"BLUE\""
        json.encodeToString(ThemeColor.SUNSET) shouldBe "\"SUNSET\""
    }

    @Test
    fun `ThemeColor round-trip`() {
        ThemeColor.entries.forEach { color ->
            val encoded = json.encodeToString(color)
            val decoded = json.decodeFromString<ThemeColor>(encoded)
            decoded shouldBe color
        }
    }

    @Test
    fun `ThemeStyle wire names are stable`() {
        json.encodeToString(ThemeStyle.DEFAULT) shouldBe "\"DEFAULT\""
        json.encodeToString(ThemeStyle.MATERIAL_YOU) shouldBe "\"MATERIAL_YOU\""
        json.encodeToString(ThemeStyle.MEDIUM_CONTRAST) shouldBe "\"MEDIUM_CONTRAST\""
        json.encodeToString(ThemeStyle.HIGH_CONTRAST) shouldBe "\"HIGH_CONTRAST\""
    }

    @Test
    fun `ThemeStyle round-trip`() {
        ThemeStyle.entries.forEach { style ->
            val encoded = json.encodeToString(style)
            val decoded = json.decodeFromString<ThemeStyle>(encoded)
            decoded shouldBe style
        }
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written values`() {
        json.decodeFromString<ThemeMode>("\"SYSTEM\"") shouldBe ThemeMode.SYSTEM
        json.decodeFromString<ThemeColor>("\"GREEN\"") shouldBe ThemeColor.GREEN
        json.decodeFromString<ThemeStyle>("\"DEFAULT\"") shouldBe ThemeStyle.DEFAULT
        json.decodeFromString<ThemeStyle>("\"MATERIAL_YOU\"") shouldBe ThemeStyle.MATERIAL_YOU
    }
}
