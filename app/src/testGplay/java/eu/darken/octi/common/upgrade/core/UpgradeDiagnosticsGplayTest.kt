package eu.darken.octi.common.upgrade.core

import eu.darken.octi.main.core.CurriculumVitae
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    @Test
    fun `a cancelled billing cache read is not swallowed`() = runTest {
        val diag = UpgradeDiagnosticsGplay(
            billingCache = mockk<BillingCache>().apply {
                coEvery { snapshot() } throws CancellationException("scope died")
            },
            curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory },
        )

        shouldThrow<CancellationException> { diag.debugInfo() }
    }

    @Test
    fun `a cancelled history read is not swallowed`() = runTest {
        // Symmetric to the cache read: cancellation is not a diagnostics failure, it means the
        // caller's scope died and the header read must unwind with it.
        val diag = UpgradeDiagnosticsGplay(
            billingCache = mockk<BillingCache>().apply {
                coEvery { snapshot() } returns BillingCache.Snapshot(
                    lastProStateAt = 0L,
                    lastProStateSku = "",
                    proUnconfirmedSince = 0L,
                )
            },
            curriculumVitae = mockk<CurriculumVitae>().apply {
                coEvery { proHistory() } throws CancellationException("scope died")
            },
        )

        shouldThrow<CancellationException> { diag.debugInfo() }
    }

    @Test
    fun `a wedged billing cache is reported as unavailable, not as a never-pro install`() = runTest {
        // End-to-end over a real BillingCache whose store never answers: the bounded read throws,
        // and the header must say the evidence is missing instead of claiming "never bought".
        val diag = UpgradeDiagnosticsGplay(
            billingCache = BillingCache(HangingPreferencesDataStore()).apply { cacheTimeoutMs = 50L },
            curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory },
        )

        val info = diag.debugInfo()

        info shouldContain "BillingCache=unavailable"
        info shouldNotContain "lastProStateAt=never"
        info shouldContain "ProHistory=$proHistory"
    }

    @Test
    fun `a wedged history is reported as unavailable`() = runTest {
        // Counterpart to the wedged cache above: a never-answering CurriculumVitae store would hold
        // the debug-log header -- and with it the start of the recording -- forever.
        val diagnostics = UpgradeDiagnosticsGplay(
            billingCache = mockk<BillingCache>().apply {
                coEvery { snapshot() } returns BillingCache.Snapshot(
                    lastProStateAt = 0L,
                    lastProStateSku = "",
                    proUnconfirmedSince = 0L,
                )
            },
            curriculumVitae = mockk<CurriculumVitae>().apply {
                coEvery { proHistory() } coAnswers { awaitCancellation() }
            },
        ).apply { historyTimeoutMs = 50L }

        // Real time on purpose: the bound below runs on real dispatchers, virtual time would skip it.
        // The outer envelope is independent of the seam -- if the bound is ignored entirely, this
        // fails as a timeout instead of hanging the suite.
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val info = withTimeout(10_000L) { diagnostics.debugInfo() }
            val elapsed = System.currentTimeMillis() - start

            // Materially below the 2s production bound: proves the seam was honoured, with plenty
            // of CI margin over the 50ms it was set to.
            elapsed shouldBeLessThan 1_000L
            // The cache read is independent evidence and must still be reported.
            info shouldContain "BillingCache(lastProStateAt=never"
            info shouldContain "ProHistory=unavailable"
        }
    }
}
