package eu.darken.octi.sync.core.blob

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Clock

class StorageThresholdsTest : BaseTest() {

    private val connectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = "test.example.com",
        account = "acc-1",
    )

    private fun snap(used: Long, total: Long, available: Long = total - used) = StorageSnapshot(
        connectorId = connectorId,
        accountLabel = null,
        usedBytes = used,
        totalBytes = total,
        availableBytes = available,
        maxFileBytes = null,
        perFileOverheadBytes = 0L,
        updatedAt = Clock.System.now(),
    )

    @Test
    fun `totalBytes 0 is never low`() {
        snap(used = 0, total = 0, available = 0).isLowStorage() shouldBe false
    }

    @Test
    fun `negative totalBytes is never low`() {
        snap(used = 0, total = -1, available = 0).isLowStorage() shouldBe false
    }

    @Test
    fun `exactly at the 10 percent boundary is not low`() {
        // available/total == 0.10 — strictly-below predicate returns false.
        snap(used = 90, total = 100, available = 10).isLowStorage() shouldBe false
    }

    @Test
    fun `just below 10 percent is low`() {
        snap(used = 91, total = 100, available = 9).isLowStorage() shouldBe true
    }

    @Test
    fun `comfortably above the threshold is not low`() {
        snap(used = 50, total = 100, available = 50).isLowStorage() shouldBe false
    }

    @Test
    fun `zero available is low`() {
        snap(used = 100, total = 100, available = 0).isLowStorage() shouldBe true
    }

    @Test
    fun `large terabyte-scale numbers do not overflow Long to Double conversion`() {
        // 5 GiB available out of 1 TiB = ~0.49% — comfortably below 10%.
        val tib = 1L shl 40
        val gib = 1L shl 30
        snap(used = tib - 5L * gib, total = tib, available = 5L * gib).isLowStorage() shouldBe true

        // 200 GiB available out of 1 TiB = ~19.5% — above 10%.
        snap(used = tib - 200L * gib, total = tib, available = 200L * gib).isLowStorage() shouldBe false
    }
}
