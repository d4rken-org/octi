package eu.darken.octi.common.upgrade.core

import eu.darken.octi.main.core.CurriculumVitae
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class UpgradeDiagnosticsGplayTest : BaseTest() {

    private val proHistory = CurriculumVitae.ProHistory(
        lastState = CurriculumVitae.ProState.PURCHASED,
        graceEngagedCount = 1,
        graceEngagedLast = null,
        proLostCount = 0,
        proLostLast = null,
    )

    private fun create(
        snapshot: BillingCache.Snapshot,
        history: CurriculumVitae.ProHistory = proHistory,
    ) = UpgradeDiagnosticsGplay(
        billingCache = mockk<BillingCache>().apply { coEvery { this@apply.snapshot() } returns snapshot },
        curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { this@apply.proHistory() } returns history },
    )

    @Test
    fun `a never-pro install is reported as never, not as epoch zero`() = runTest {
        // The whole point of this line in the log header is telling "never bought" apart from
        // "bought once, entitlement now missing". A raw 0 reads as a 1970 timestamp.
        val info = create(
            BillingCache.Snapshot(lastProStateAt = 0L, lastProStateSku = "", proUnconfirmedSince = 0L)
        ).debugInfo()

        info shouldContain "BillingCache(lastProStateAt=never, lastProStateSku=unknown/legacy, proUnconfirmedSince=none)"
        info shouldContain "ProHistory="
    }

    @Test
    fun `a confirmed purchase reports an instant and the sku`() = runTest {
        val info = create(
            BillingCache.Snapshot(
                lastProStateAt = 1_700_000_000_000L,
                lastProStateSku = OurSku.Iap.PRO_UPGRADE.id,
                proUnconfirmedSince = 0L,
            )
        ).debugInfo()

        info shouldContain "lastProStateAt=2023-11-14T22:13:20Z"
        info shouldContain "lastProStateSku=${OurSku.Iap.PRO_UPGRADE.id}"
        info shouldContain "proUnconfirmedSince=none"
    }

    @Test
    fun `an open unconfirmed episode is reported as an instant`() = runTest {
        val info = create(
            BillingCache.Snapshot(
                lastProStateAt = 1_700_000_000_000L,
                lastProStateSku = OurSku.Sub.PRO_UPGRADE.id,
                proUnconfirmedSince = 1_700_000_500_000L,
            )
        ).debugInfo()

        info shouldContain "proUnconfirmedSince=2023-11-14T22:21:40Z"
    }

    @Test
    fun `a failing cache read is isolated from the pro-state history`() = runTest {
        // Different DataStores: a cache read failure must still let the CV counters report.
        val diag = UpgradeDiagnosticsGplay(
            billingCache = mockk<BillingCache>().apply {
                coEvery { snapshot() } throws IllegalStateException("cache down")
            },
            curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory },
        )

        val info = diag.debugInfo()

        info shouldContain "BillingCache=unavailable"
        info shouldContain "ProHistory="
        info shouldContain "graceEngagedCount=1"
    }

    @Test
    fun `a failing pro-state history read is isolated from the cache`() = runTest {
        // The reverse direction: a CV counter read failure must still let the cache report.
        val diag = UpgradeDiagnosticsGplay(
            billingCache = mockk<BillingCache>().apply {
                coEvery { snapshot() } returns BillingCache.Snapshot(
                    lastProStateAt = 1_700_000_000_000L,
                    lastProStateSku = OurSku.Iap.PRO_UPGRADE.id,
                    proUnconfirmedSince = 0L,
                )
            },
            curriculumVitae = mockk<CurriculumVitae>().apply {
                coEvery { proHistory() } throws IllegalStateException("cv down")
            },
        )

        val info = diag.debugInfo()

        info shouldContain "lastProStateSku=${OurSku.Iap.PRO_UPGRADE.id}"
        info shouldContain "ProHistory=unavailable"
    }
}
