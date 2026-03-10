package eu.darken.octi.modules.apps.core

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppsSortModeSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `all enum values have stable wire names`() {
        json.encodeToString(AppsSortMode.NAME) shouldBe "\"NAME\""
        json.encodeToString(AppsSortMode.INSTALLED_AT) shouldBe "\"INSTALLED_AT\""
        json.encodeToString(AppsSortMode.UPDATED_AT) shouldBe "\"UPDATED_AT\""
    }

    @Test
    fun `round-trip all enum values`() {
        AppsSortMode.entries.forEach { mode ->
            val encoded = json.encodeToString(mode)
            val decoded = json.decodeFromString<AppsSortMode>(encoded)
            decoded shouldBe mode
        }
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written values`() {
        json.decodeFromString<AppsSortMode>("\"NAME\"") shouldBe AppsSortMode.NAME
        json.decodeFromString<AppsSortMode>("\"INSTALLED_AT\"") shouldBe AppsSortMode.INSTALLED_AT
        json.decodeFromString<AppsSortMode>("\"UPDATED_AT\"") shouldBe AppsSortMode.UPDATED_AT
    }
}
