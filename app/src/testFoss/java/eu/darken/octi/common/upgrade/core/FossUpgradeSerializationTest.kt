package eu.darken.octi.common.upgrade.core

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class FossUpgradeSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `round-trip with GITHUB_SPONSORS`() {
        val upgrade = FossUpgrade(
            upgradedAt = Instant.parse("2024-06-15T12:00:00Z"),
            upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
        )
        val encoded = json.encodeToString(upgrade)
        val decoded = json.decodeFromString<FossUpgrade>(encoded)
        decoded shouldBe upgrade
    }

    @Test
    fun `legacy enum wire names - LEGACY_DONATED`() {
        val moshiJson = """{"upgradedAt":"2024-01-01T00:00:00Z","reason":"foss.upgrade.reason.donated"}"""
        val decoded = json.decodeFromString<FossUpgrade>(moshiJson)
        decoded.upgradeType shouldBe FossUpgrade.Type.LEGACY_DONATED
    }

    @Test
    fun `legacy enum wire names - LEGACY_ALREADY_DONATED`() {
        val moshiJson = """{"upgradedAt":"2024-01-01T00:00:00Z","reason":"foss.upgrade.reason.alreadydonated"}"""
        val decoded = json.decodeFromString<FossUpgrade>(moshiJson)
        decoded.upgradeType shouldBe FossUpgrade.Type.LEGACY_ALREADY_DONATED
    }

    @Test
    fun `legacy enum wire names - LEGACY_NO_MONEY`() {
        val moshiJson = """{"upgradedAt":"2024-01-01T00:00:00Z","reason":"foss.upgrade.reason.nomoney"}"""
        val decoded = json.decodeFromString<FossUpgrade>(moshiJson)
        decoded.upgradeType shouldBe FossUpgrade.Type.LEGACY_NO_MONEY
    }

    @Test
    fun `legacy enum wire names - GITHUB_SPONSORS`() {
        val moshiJson = """{"upgradedAt":"2024-01-01T00:00:00Z","reason":"GITHUB_SPONSORS"}"""
        val decoded = json.decodeFromString<FossUpgrade>(moshiJson)
        decoded.upgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
    }

    @Test
    fun `all Type enum wire names are stable`() {
        json.encodeToString(FossUpgrade.Type.GITHUB_SPONSORS) shouldBe "\"GITHUB_SPONSORS\""
        json.encodeToString(FossUpgrade.Type.LEGACY_DONATED) shouldBe "\"foss.upgrade.reason.donated\""
        json.encodeToString(FossUpgrade.Type.LEGACY_ALREADY_DONATED) shouldBe "\"foss.upgrade.reason.alreadydonated\""
        json.encodeToString(FossUpgrade.Type.LEGACY_NO_MONEY) shouldBe "\"foss.upgrade.reason.nomoney\""
    }

    @Test
    fun `wire format stability`() {
        val upgrade = FossUpgrade(
            upgradedAt = Instant.parse("2024-06-15T12:00:00Z"),
            upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
        )
        val encoded = json.encodeToString(upgrade)
        encoded.toComparableJson() shouldBe """
            {
                "upgradedAt": "2024-06-15T12:00:00Z",
                "reason": "GITHUB_SPONSORS"
            }
        """.toComparableJson()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """{"upgradedAt":"2024-01-01T00:00:00Z","reason":"GITHUB_SPONSORS","platform":"android"}"""
        val decoded = json.decodeFromString<FossUpgrade>(futureJson)
        decoded.upgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
    }
}
