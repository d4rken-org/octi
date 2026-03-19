package eu.darken.octi.main.ui.dashboard

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class TileLayoutConfigTest : BaseTest() {

    private val allModules = setOf(
        "eu.darken.octi.module.core.power",
        "eu.darken.octi.module.core.wifi",
        "eu.darken.octi.module.core.connectivity",
        "eu.darken.octi.module.core.apps",
        "eu.darken.octi.module.core.clipboard",
    )

    @Nested
    inner class `normalize` {
        @Test
        fun `appends new modules at the end`() {
            val config = TileLayoutConfig(
                order = listOf("eu.darken.octi.module.core.power"),
            )
            val normalized = config.normalize(allModules)
            normalized.order.first() shouldBe "eu.darken.octi.module.core.power"
            normalized.order.size shouldBe 5
            normalized.order.containsAll(allModules) shouldBe true
        }

        @Test
        fun `removes unknown module IDs`() {
            val config = TileLayoutConfig(
                order = listOf("eu.darken.octi.module.core.power", "unknown.module"),
                wideModules = setOf("eu.darken.octi.module.core.power", "unknown.module"),
                hiddenModules = setOf("unknown.module"),
            )
            val normalized = config.normalize(allModules)
            normalized.order.contains("unknown.module") shouldBe false
            normalized.wideModules.contains("unknown.module") shouldBe false
            normalized.hiddenModules.contains("unknown.module") shouldBe false
        }

        @Test
        fun `deduplicates order`() {
            val config = TileLayoutConfig(
                order = listOf(
                    "eu.darken.octi.module.core.power",
                    "eu.darken.octi.module.core.power",
                    "eu.darken.octi.module.core.wifi",
                ),
            )
            val normalized = config.normalize(allModules)
            normalized.order.count { it == "eu.darken.octi.module.core.power" } shouldBe 1
        }

        @Test
        fun `empty config gets all modules sorted`() {
            val config = TileLayoutConfig(order = emptyList())
            val normalized = config.normalize(allModules)
            normalized.order.size shouldBe allModules.size
            normalized.order.toSet() shouldBe allModules
        }
    }

    @Nested
    inner class `toRows` {
        @Test
        fun `wide tile gets full row`() {
            val config = TileLayoutConfig(
                order = listOf("power", "wifi", "connectivity"),
                wideModules = setOf("power"),
            )
            val rows = config.toRows(setOf("power", "wifi", "connectivity"))
            rows.size shouldBe 2
            rows[0].modules shouldBe listOf("power")
            rows[1].modules shouldBe listOf("wifi", "connectivity")
        }

        @Test
        fun `two half-width tiles share a row`() {
            val config = TileLayoutConfig(
                order = listOf("wifi", "connectivity"),
                wideModules = emptySet(),
            )
            val rows = config.toRows(setOf("wifi", "connectivity"))
            rows.size shouldBe 1
            rows[0].modules shouldBe listOf("wifi", "connectivity")
        }

        @Test
        fun `odd half-width tile gets own row`() {
            val config = TileLayoutConfig(
                order = listOf("wifi", "connectivity", "apps"),
                wideModules = emptySet(),
            )
            val rows = config.toRows(setOf("wifi", "connectivity", "apps"))
            rows.size shouldBe 2
            rows[0].modules shouldBe listOf("wifi", "connectivity")
            rows[1].modules shouldBe listOf("apps")
        }

        @Test
        fun `hidden modules are excluded`() {
            val config = TileLayoutConfig(
                order = listOf("power", "wifi", "connectivity"),
                wideModules = emptySet(),
                hiddenModules = setOf("wifi"),
            )
            val rows = config.toRows(setOf("power", "wifi", "connectivity"))
            val allModulesInRows = rows.flatMap { it.modules }
            allModulesInRows.contains("wifi") shouldBe false
        }

        @Test
        fun `unavailable modules are excluded`() {
            val config = TileLayoutConfig(
                order = listOf("power", "wifi", "connectivity"),
                wideModules = emptySet(),
            )
            val rows = config.toRows(setOf("power", "connectivity"))
            val allModulesInRows = rows.flatMap { it.modules }
            allModulesInRows.contains("wifi") shouldBe false
        }

        @Test
        fun `empty available modules produces no rows`() {
            val config = TileLayoutConfig()
            val rows = config.toRows(emptySet())
            rows shouldBe emptyList()
        }

        @Test
        fun `single module produces one row`() {
            val config = TileLayoutConfig(
                order = listOf("power"),
                wideModules = emptySet(),
            )
            val rows = config.toRows(setOf("power"))
            rows.size shouldBe 1
            rows[0].modules shouldBe listOf("power")
        }

        @Test
        fun `half-width before wide gets own row`() {
            val config = TileLayoutConfig(
                order = listOf("wifi", "power", "connectivity"),
                wideModules = setOf("power"),
            )
            val rows = config.toRows(setOf("wifi", "power", "connectivity"))
            rows.size shouldBe 3
            rows[0].modules shouldBe listOf("wifi")
            rows[1].modules shouldBe listOf("power")
            rows[2].modules shouldBe listOf("connectivity")
        }

        @Test
        fun `mixed wide and half layout`() {
            val config = TileLayoutConfig(
                order = listOf("power", "wifi", "connectivity", "apps", "clipboard"),
                wideModules = setOf("power"),
            )
            val rows = config.toRows(setOf("power", "wifi", "connectivity", "apps", "clipboard"))
            rows.size shouldBe 3
            rows[0].modules shouldBe listOf("power")
            rows[1].modules shouldBe listOf("wifi", "connectivity")
            rows[2].modules shouldBe listOf("apps", "clipboard")
        }
    }
}
