package eu.darken.octi.main.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.permissions.PermissionState
import eu.darken.octi.common.review.ReviewTool
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.updater.UpdateService
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.clipboard.ClipboardHandler
import eu.darken.octi.modules.files.core.FileShareRepo
import eu.darken.octi.modules.power.core.alert.PowerAlertManager
import eu.darken.octi.sync.core.ConnectorIssueAggregator
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncExecutor
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOrchestrator
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.StorageStatusManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import kotlin.time.Instant

/**
 * The review prompt is the lowest-priority dashboard card: anything that asks the user for a
 * decision has to win over asking them for a favor.
 */
class DashboardReviewCardTest : BaseTest() {

    private val proInfo = object : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY
        override val isPro: Boolean = true
        override val isSettled: Boolean = true
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }

    private fun <T> setting(value: T): DataStoreValue<T> = mockk<DataStoreValue<T>>(relaxed = true).apply {
        every { flow } returns flowOf(value)
    }

    private fun orchestratorState() = SyncOrchestrator.State(
        quickSync = SyncOrchestrator.QuickSyncState(isActive = false, connectorModes = emptyMap()),
        backgroundSync = SyncOrchestrator.BackgroundSyncState(
            defaultWorker = SyncOrchestrator.BackgroundSyncState.WorkerInfo(
                isEnabled = true,
                isRunning = false,
                isBlocked = false,
                nextRunAt = null,
            ),
            chargingWorker = SyncOrchestrator.BackgroundSyncState.WorkerInfo(
                isEnabled = false,
                isRunning = false,
                isBlocked = false,
                nextRunAt = null,
            ),
        ),
    )

    private fun vm(
        shouldAskForReview: Boolean,
        hasConnector: Boolean,
    ): DashboardVM {
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { isOnboardingDone } returns setting(true)
            every { dashboardConfig } returns setting(DashboardConfig())
        }
        val syncManager = mockk<SyncManager>(relaxed = true).apply {
            every { allConnectors } returns flowOf(
                if (hasConnector) listOf(mockk<SyncConnector>(relaxed = true)) else emptyList(),
            )
            every { connectors } returns flowOf(emptyList())
            every { states } returns flowOf(emptyList())
            every { allStates } returns flowOf(emptyList())
            every { busyConnectorIds } returns flowOf(emptySet())
        }
        val moduleManager = mockk<ModuleManager>(relaxed = true).apply {
            every { byDevice } returns flowOf(ModuleManager.ByDevice(emptyMap()))
            every { moduleSyncStates } returns flowOf(emptyList())
            every { syncingModules } returns flowOf(emptySet())
        }
        val networkStateProvider = mockk<NetworkStateProvider>(relaxed = true).apply {
            every { networkState } returns flowOf(NetworkStateProvider.State.Fallback)
        }
        val permissionTool = mockk<PermissionTool>(relaxed = true).apply {
            every { missingPermissions } returns flowOf(emptySet())
        }
        val syncSettings = mockk<SyncSettings>(relaxed = true).apply {
            every { showDashboardCard } returns setting(false)
            every { pausedConnectorIds } returns flowOf(emptySet())
            every { deviceId } returns DeviceId("test-device")
        }
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
            every { upgradeInfo } returns flowOf(proInfo)
        }
        val updateService = mockk<UpdateService>(relaxed = true).apply {
            every { availableUpdate } returns emptyFlow()
        }
        val alertManager = mockk<PowerAlertManager>(relaxed = true).apply {
            every { alerts } returns flowOf(emptyList())
        }
        val syncOrchestrator = mockk<SyncOrchestrator>(relaxed = true).apply {
            every { state } returns flowOf(orchestratorState())
        }
        val issueAggregator = mockk<ConnectorIssueAggregator>(relaxed = true).apply {
            every { issues } returns flowOf(emptyList())
        }
        val fileShareRepo = mockk<FileShareRepo>(relaxed = true).apply {
            every { isEnabled } returns flowOf(false)
        }
        val storageStatusManager = mockk<StorageStatusManager>(relaxed = true).apply {
            every { configuredConnectorIds } returns flowOf(emptySet())
        }
        val reviewTool = mockk<ReviewTool>().apply {
            every { state } returns flowOf(ReviewTool.State(shouldAskForReview = shouldAskForReview))
        }

        return DashboardVM(
            handle = SavedStateHandle(),
            dispatcherProvider = TestDispatcherProvider(),
            appScope = mockk<CoroutineScope>(relaxed = true),
            generalSettings = generalSettings,
            syncManager = syncManager,
            moduleManager = moduleManager,
            networkStateProvider = networkStateProvider,
            permissionTool = permissionTool,
            permissionState = mockk<PermissionState>(relaxed = true),
            syncSettings = syncSettings,
            upgradeRepo = upgradeRepo,
            webpageTool = mockk<WebpageTool>(relaxed = true),
            clipboardHandler = mockk<ClipboardHandler>(relaxed = true),
            updateService = updateService,
            alertManager = alertManager,
            syncExecutor = mockk<SyncExecutor>(relaxed = true),
            syncOrchestrator = syncOrchestrator,
            issueAggregator = issueAggregator,
            fileShareRepo = fileShareRepo,
            blobManager = mockk<BlobManager>(relaxed = true),
            storageStatusManager = storageStatusManager,
            connectorContributions = emptyMap<ConnectorType, ConnectorUiContribution>(),
            cardSnoozer = DashboardCardSnoozer(),
            reviewTool = reviewTool,
        )
    }

    @Test
    fun `the sync setup card suppresses the review card`() = runTest2 {
        val state = vm(shouldAskForReview = true, hasConnector = false).state.first()

        state.showSyncSetup shouldBe true
        // Asking for a favor must not compete with the setup step the user still has to do.
        state.showReviewCard shouldBe false
    }

    @Test
    fun `an otherwise quiet dashboard shows the review card`() = runTest2 {
        val state = vm(shouldAskForReview = true, hasConnector = true).state.first()

        state.showSyncSetup shouldBe false
        state.showReviewCard shouldBe true
    }
}
