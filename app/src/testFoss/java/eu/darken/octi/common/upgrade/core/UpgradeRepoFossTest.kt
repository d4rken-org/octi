package eu.darken.octi.common.upgrade.core

import eu.darken.octi.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Instant

// Serialization of the persisted upgrade is covered by FossUpgradeSerializationTest.
class UpgradeRepoFossTest : BaseTest() {

    @Test
    fun `test upgrade info pro status mapping`() {
        UpgradeRepoFoss.Info(
            isPro = false,
            upgradedAt = null,
        ).apply {
            type shouldBe UpgradeRepo.Type.FOSS
            isPro shouldBe false
            // A local cache read is authoritative from the first emission, there is no billing
            // handshake to wait out.
            isSettled shouldBe true
            error shouldBe null
        }

        UpgradeRepoFoss.Info(
            isPro = true,
            upgradedAt = Instant.fromEpochMilliseconds(0L),
            fossUpgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
        ).apply {
            isPro shouldBe true
            upgradedAt shouldBe Instant.fromEpochMilliseconds(0L)
            fossUpgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
        }
    }
}
