package eu.darken.octi.sync.ui.add

import androidx.lifecycle.SavedStateHandle
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavEvent
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.ui.add.SyncAddVM.Companion.forMode
import eu.darken.octi.syncs.gdrive.ui.GDriveUiContribution
import eu.darken.octi.syncs.octiserver.ui.OctiServerUiContribution
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class SyncAddVMTest : BaseTest() {

    private object CreateDestination : NavigationDestination
    private object JoinDestination : NavigationDestination

    private fun fakeContribution(
        type: ConnectorType = ConnectorType.GDRIVE,
        displayOrder: Int = 0,
        joinDestination: NavigationDestination? = null,
    ): ConnectorUiContribution = mockk<ConnectorUiContribution>().apply {
        every { this@apply.type } returns type
        every { this@apply.displayOrder } returns displayOrder
        every { addAccountDestination() } returns CreateDestination
        every { joinDeviceDestination() } returns joinDestination
    }

    private fun makeVM(vararg contributions: ConnectorUiContribution) = SyncAddVM(
        handle = SavedStateHandle(),
        dispatcherProvider = TestDispatcherProvider(),
        contributions = contributions.associateBy { it.type },
    )

    @Nested
    inner class `click dispatch` {
        @Test
        fun `CREATE mode navigates to addAccountDestination`() = runTest2 {
            val contribution = fakeContribution()
            val vm = makeVM(contribution)

            vm.onContributionClicked(contribution, Nav.Sync.AddPicker.Mode.CREATE)

            vm.navEvents.first() shouldBe NavEvent.GoTo(CreateDestination)
        }

        @Test
        fun `LINK mode navigates to joinDeviceDestination`() = runTest2 {
            val contribution = fakeContribution(joinDestination = JoinDestination)
            val vm = makeVM(contribution)

            vm.onContributionClicked(contribution, Nav.Sync.AddPicker.Mode.LINK)

            vm.navEvents.first() shouldBe NavEvent.GoTo(JoinDestination)
        }

        @Test
        fun `LINK mode without join destination emits no navigation`() = runTest2 {
            val contribution = fakeContribution(joinDestination = null)
            val vm = makeVM(contribution)

            vm.onContributionClicked(contribution, Nav.Sync.AddPicker.Mode.LINK)

            withTimeoutOrNull(100) { vm.navEvents.firstOrNull() } shouldBe null
        }
    }

    @Nested
    inner class `mode filtering` {
        @Test
        fun `CREATE mode keeps all contributions`() {
            val joinable = fakeContribution(type = ConnectorType.OCTISERVER, joinDestination = JoinDestination)
            val nonJoinable = fakeContribution(type = ConnectorType.GDRIVE, joinDestination = null)

            listOf(joinable, nonJoinable).forMode(Nav.Sync.AddPicker.Mode.CREATE) shouldContainExactly listOf(
                joinable,
                nonJoinable,
            )
        }

        @Test
        fun `LINK mode filters out contributions without join destination`() {
            val joinable = fakeContribution(type = ConnectorType.OCTISERVER, joinDestination = JoinDestination)
            val nonJoinable = fakeContribution(type = ConnectorType.GDRIVE, joinDestination = null)

            listOf(joinable, nonJoinable).forMode(Nav.Sync.AddPicker.Mode.LINK) shouldContainExactly listOf(joinable)
        }

        @Test
        fun `LINK mode is empty when no contribution can join`() {
            val nonJoinable = fakeContribution(type = ConnectorType.GDRIVE, joinDestination = null)

            listOf(nonJoinable).forMode(Nav.Sync.AddPicker.Mode.LINK) shouldBe emptyList()
        }
    }

    @Nested
    inner class `real contributions` {
        @Test
        fun `GDrive joins via its add screen in linking mode`() {
            GDriveUiContribution().joinDeviceDestination() shouldBe Nav.Sync.AddGDrive(linking = true)
        }

        @Test
        fun `GDrive creates via its add screen in non-linking mode`() {
            GDriveUiContribution().addAccountDestination() shouldBe Nav.Sync.AddGDrive(linking = false)
        }

        @Test
        fun `OctiServer joins via the link client screen`() {
            OctiServerUiContribution().joinDeviceDestination() shouldBe Nav.Sync.OctiServerLinkClient
        }
    }
}
