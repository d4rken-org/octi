package eu.darken.octi.common.upgrade.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.octi.common.datastore.value
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File

class BillingCacheTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private fun cache(scope: TestScope): BillingCache = BillingCache(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir, "billing.preferences_pb") },
        )
    )

    @Test
    fun `stamp sets anchor and clears any open episode`() = runTest {
        val cache = cache(this)

        cache.recordProUnconfirmed(0L) // no-op: no prior confirmation
        cache.proUnconfirmedAt.value() shouldBe 0L

        cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 1_000_000L)
        cache.lastProStateAt.value() shouldBe 1_000_000L
        cache.lastProStateSku.value() shouldBe OurSku.Iap.PRO_UPGRADE.id
        cache.proUnconfirmedAt.value() shouldBe 0L
    }

    @Test
    fun `stamp with null sku keeps the previous sku`() = runTest {
        val cache = cache(this)
        cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 1_000_000L)

        cache.stampLastProState(null, 2_000_000L)

        cache.lastProStateAt.value() shouldBe 2_000_000L
        cache.lastProStateSku.value() shouldBe OurSku.Iap.PRO_UPGRADE.id
    }

    @Test
    fun `unconfirmed is a no-op without a prior confirmation`() = runTest {
        val cache = cache(this)
        cache.recordProUnconfirmed(5_000_000L)
        cache.proUnconfirmedAt.value() shouldBe 0L
    }

    @Test
    fun `unconfirmed is rejected within the confirmation-age guard`() = runTest {
        val cache = cache(this)
        cache.stampLastProState("sku", 1_000_000L)

        // 30s after confirmation < MIN_CONFIRMATION_AGE_MS (60s): treated as emission reordering.
        cache.recordProUnconfirmed(1_000_000L + 30_000L)
        cache.proUnconfirmedAt.value() shouldBe 0L
    }

    @Test
    fun `unconfirmed opens the episode once and does not push it out`() = runTest {
        val cache = cache(this)
        cache.stampLastProState("sku", 1_000_000L)

        val first = 1_000_000L + 120_000L
        cache.recordProUnconfirmed(first)
        cache.proUnconfirmedAt.value() shouldBe first

        // A follow-up failure must NOT move the diagnostics threshold.
        cache.recordProUnconfirmed(first + 500_000L)
        cache.proUnconfirmedAt.value() shouldBe first
    }

    @Test
    fun `stamp closes an open episode atomically`() = runTest {
        val cache = cache(this)
        cache.stampLastProState("sku", 1_000_000L)
        cache.recordProUnconfirmed(1_000_000L + 120_000L)
        cache.proUnconfirmedAt.value() shouldBe 1_120_000L

        cache.stampLastProState("sku", 2_000_000L)
        cache.proUnconfirmedAt.value() shouldBe 0L
    }

    @Test
    fun `unconfirmed repairs a corrupt episode stamp`() = runTest {
        val cache = cache(this)
        cache.stampLastProState("sku", 5_000_000L)

        // Corrupt: an episode start at/older than the last confirmation can't be real.
        cache.proUnconfirmedAt.value(4_000_000L)

        cache.recordProUnconfirmed(5_200_000L)
        cache.proUnconfirmedAt.value() shouldBe 5_200_000L
    }
}
