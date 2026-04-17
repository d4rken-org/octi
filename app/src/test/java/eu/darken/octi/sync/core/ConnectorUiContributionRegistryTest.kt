package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.syncs.gdrive.ui.GDriveUiContribution
import eu.darken.octi.syncs.octiserver.ui.OctiServerUiContribution
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Verifies the @IntoMap contribution registry invariant: exactly one contribution per
 * [ConnectorType], each with a distinct [ConnectorUiContribution.displayOrder].
 *
 * This is a structural unit test — it instantiates each contribution directly rather than
 * going through Hilt. Dagger's `@IntoMap` already enforces uniqueness at compile time; this
 * test guards against future regressions like accidentally assigning the same `displayOrder`
 * to two contributions, or adding a new contribution without updating this test.
 */
class ConnectorUiContributionRegistryTest : BaseTest() {

    private val contributions: Map<ConnectorType, ConnectorUiContribution> = mapOf(
        ConnectorType.GDRIVE to GDriveUiContribution(),
        ConnectorType.OCTISERVER to OctiServerUiContribution(),
    )

    @Test
    fun `every known ConnectorType has a contribution`() {
        contributions.keys shouldContainExactlyInAnyOrder ConnectorType.entries.toList()
    }

    @Test
    fun `contribution type matches map key`() {
        contributions.forEach { (type, contribution) ->
            contribution.type shouldBe type
        }
    }

    @Test
    fun `displayOrder values are distinct`() {
        val orders = contributions.values.map { it.displayOrder }
        orders.toSet().size shouldBe orders.size
    }

    @Test
    fun `GDrive sorts before OctiServer`() {
        contributions.getValue(ConnectorType.GDRIVE).displayOrder shouldBe 10
        contributions.getValue(ConnectorType.OCTISERVER).displayOrder shouldBe 20
    }
}
