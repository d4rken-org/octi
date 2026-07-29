package eu.darken.octi.common.upgrade

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.IOException

class ProStateTest : BaseTest() {

    private fun info(
        isPro: Boolean = false,
        isSettled: Boolean = true,
        error: Throwable? = null,
    ): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().also {
        every { it.isPro } returns isPro
        every { it.isSettled } returns isSettled
        every { it.error } returns error
    }

    @Test
    fun `seeds Checking before the first emission`() = runTest2 {
        val repo = mockk<UpgradeRepo>()
        every { repo.upgradeInfo } returns flowOf(info(isPro = false))

        val emissions = repo.proState().toList()

        emissions.first() shouldBe ProState.Checking
    }

    @Test
    fun `maps isPro=true to Unlocked`() = runTest2 {
        val repo = mockk<UpgradeRepo>()
        every { repo.upgradeInfo } returns flowOf(info(isPro = true))

        val emissions = repo.proState().toList()

        emissions shouldBe listOf(ProState.Checking, ProState.Unlocked)
    }

    @Test
    fun `maps a settled error-free non-pro Info to Locked`() = runTest2 {
        val repo = mockk<UpgradeRepo>()
        every { repo.upgradeInfo } returns flowOf(info(isPro = false, isSettled = true))

        val emissions = repo.proState().toList()

        emissions shouldBe listOf(ProState.Checking, ProState.Locked)
    }

    @Test
    fun `maps an unsettled Info to Checking`() = runTest2 {
        // The GPlay cold-start seed: not Pro yet, but billing hasn't settled — must render as
        // Checking rather than flashing Locked at a paying user.
        val repo = mockk<UpgradeRepo>()
        every { repo.upgradeInfo } returns flowOf(info(isPro = false, isSettled = false))

        val emissions = repo.proState().toList()

        emissions shouldBe listOf(ProState.Checking, ProState.Checking)
    }

    @Test
    fun `maps a settled error Info to Error`() = runTest2 {
        val boom = IOException("billing broke")
        val repo = mockk<UpgradeRepo>()
        every { repo.upgradeInfo } returns flowOf(info(isPro = false, isSettled = true, error = boom))

        val emissions = repo.proState().toList()

        emissions[0] shouldBe ProState.Checking
        val error = emissions[1]
        error.shouldBeInstanceOf<ProState.Error>()
        error.cause shouldBe boom
    }

    @Test
    fun `Locked then Unlocked when upgrade lands after first emission`() = runTest2 {
        val repo = mockk<UpgradeRepo>()
        every { repo.upgradeInfo } returns flowOf(info(isPro = false), info(isPro = true))

        val emissions = repo.proState().toList()

        emissions shouldBe listOf(ProState.Checking, ProState.Locked, ProState.Unlocked)
    }

    @Test
    fun `surfaces upstream errors as ProState_Error`() = runTest2 {
        val cause = IOException("boom")
        val repo = mockk<UpgradeRepo>()
        every { repo.upgradeInfo } returns flow { throw cause }

        val emissions = repo.proState().toList()

        emissions[0] shouldBe ProState.Checking
        val error = emissions[1]
        error.shouldBeInstanceOf<ProState.Error>()
        error.cause shouldBe cause
    }
}
