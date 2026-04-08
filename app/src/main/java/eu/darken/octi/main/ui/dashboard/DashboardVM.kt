package eu.darken.octi.main.ui.dashboard

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.updater.UpdateChecker
import eu.darken.octi.main.core.updater.UpdateService
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.getInstallerIntent
import eu.darken.octi.modules.clipboard.ClipboardHandler
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.alert.BatteryHighAlertRule
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.core.alert.PowerAlertManager
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.ConnectorIssueAggregator
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.sync.core.SyncExecutor
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOrchestrator
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class DashboardVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @AppScope private val appScope: CoroutineScope,
    private val generalSettings: GeneralSettings,
    private val syncManager: SyncManager,
    private val moduleManager: ModuleManager,
    networkStateProvider: NetworkStateProvider,
    private val permissionTool: PermissionTool,
    private val syncSettings: SyncSettings,
    private val upgradeRepo: UpgradeRepo,
    private val webpageTool: WebpageTool,
    private val clipboardHandler: ClipboardHandler,
    private val updateService: UpdateService,
    private val alertManager: PowerAlertManager,
    private val syncExecutor: SyncExecutor,
    private val syncOrchestrator: SyncOrchestrator,
    private val issueAggregator: ConnectorIssueAggregator,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    init {
        if (!generalSettings.isOnboardingDone.valueBlocking) {
            navTo(Nav.Main.Welcome)
        }
    }

    val dashboardEvents = SingleEventFlow<DashboardEvent>()

    private val reorderMutex = Mutex()
    private val isManuallyRefreshing = MutableStateFlow(false)

    private val tickerUiRefresh = flow {
        while (currentCoroutineContext().isActive) {
            emit(Clock.System.now())
            delay(1.minutes)
        }
    }

    private val syncStatusFlow: Flow<SyncStatus?> = combine(
        syncSettings.showDashboardCard.flow,
        isManuallyRefreshing,
        syncManager.connectors,
        syncManager.states,
        syncSettings.pausedConnectors.flow,
        tickerUiRefresh,
        moduleManager.syncingModules,
        moduleManager.moduleSyncStates,
        syncOrchestrator.state,
    ) { showCard, isRefreshing, connectors, allStates, pausedIds, now, syncingModules, moduleSyncStates, orchestratorState ->
        if (!showCard) return@combine null
        val activeConnectors = connectors.filter { !pausedIds.contains(it.identifier) }
        if (activeConnectors.isEmpty()) return@combine null

        val connectorTypes = activeConnectors.map { it.identifier.type }.distinct()
        val statesMap = connectors.zip(allStates.toList()).associate { (c, s) -> c.identifier to s }
        val activeStates = activeConnectors.mapNotNull { statesMap[it.identifier] }

        val connectorDetails = activeConnectors.mapNotNull { connector ->
            val state = statesMap[connector.identifier] ?: return@mapNotNull null
            ConnectorDetail(
                connectorId = connector.identifier,
                type = connector.identifier.type,
                isBusy = state.isBusy,
                lastSyncAt = state.lastSyncAt,
                accountLabel = connector.accountLabel,
            )
        }.disambiguateLabels()

        val syncDetail = SyncDetail(
            modules = moduleSyncStates,
            connectors = connectorDetails,
        )

        val totalDevices = activeStates.sumOf { it.deviceMetadata.size }

        when {
            isRefreshing || activeStates.any { it.isBusy } || syncingModules.isNotEmpty() -> {
                SyncStatus.Syncing(connectorTypes, syncingModules, syncDetail, orchestratorState, now, totalDevices)
            }

            activeStates.any { it.lastError != null } -> {
                val error = activeStates.first { it.lastError != null }.lastError
                SyncStatus.Error(error?.message, connectorTypes, syncDetail, orchestratorState, now, totalDevices)
            }

            else -> {
                val lastSync = activeStates.mapNotNull { it.lastSyncAt }.maxByOrNull { it }
                SyncStatus.Idle(lastSync, connectorTypes, syncDetail, orchestratorState, now, totalDevices)
            }
        }
    }.setupCommonEventHandlers(TAG) { "syncStatus" }

    data class State(
        val devices: List<DeviceItem>,
        val deviceCount: Int,
        val syncStatus: SyncStatus?,
        val isSyncExpanded: Boolean = false,
        val isOffline: Boolean,
        val showSyncSetup: Boolean,
        val missingPermissions: List<Permission>,
        val update: UpdateChecker.Update?,
        val upgradeInfo: UpgradeRepo.Info,
        val deviceLimitReached: Boolean,
        val deviceLimit: Int = DEVICE_LIMIT,
        val issues: List<ConnectorIssue> = emptyList(),
    )

    data class DeviceItem(
        val now: Instant,
        val deviceId: DeviceId,
        val meta: ModuleData<MetaInfo>?,
        val moduleItems: List<ModuleItem>,
        val tileLayout: TileLayoutConfig = TileLayoutConfig(),
        val isCollapsed: Boolean,
        val isLimited: Boolean,
        val isCurrentDevice: Boolean,
        val infos: List<ConnectorIssue> = emptyList(),
        val isDegraded: Boolean = false,
        val degradedConnectorId: ConnectorId? = null,
        val degradedLabel: String? = null,
        val degradedPlatform: String? = null,
        val degradedVersion: String? = null,
        val degradedLastSeen: Instant? = null,
    ) {
        val displayLabel: String
            get() = meta?.data?.labelOrFallback ?: degradedLabel ?: deviceId.id.take(8)
    }

    sealed interface ModuleItem {
        data class Power(
            val data: ModuleData<PowerInfo>,
            val batteryLowAlert: PowerAlert<BatteryLowAlertRule>? = null,
            val batteryHighAlert: PowerAlert<BatteryHighAlertRule>? = null,
            val showSettings: Boolean,
        ) : ModuleItem

        data class Wifi(
            val data: ModuleData<WifiInfo>,
            val showPermissionAction: Boolean,
        ) : ModuleItem

        data class Connectivity(val data: ModuleData<ConnectivityInfo>) : ModuleItem
        data class Apps(val data: ModuleData<AppsInfo>) : ModuleItem
        data class Clipboard(
            val data: ModuleData<ClipboardInfo>,
            val isOurDevice: Boolean,
        ) : ModuleItem
    }

    data class SyncDetail(
        val modules: List<ModuleManager.ModuleSyncState>,
        val connectors: List<ConnectorDetail>,
    )

    data class ConnectorDetail(
        val connectorId: ConnectorId,
        val type: ConnectorType,
        val isBusy: Boolean,
        val lastSyncAt: Instant?,
        val accountLabel: String,
    )

    sealed interface SyncStatus {
        val connectorTypes: List<ConnectorType>
        val syncDetail: SyncDetail
        val orchestratorState: SyncOrchestrator.State
        val now: Instant
        val totalDeviceCount: Int

        data class Syncing(
            override val connectorTypes: List<ConnectorType>,
            val syncingModules: Set<ModuleId> = emptySet(),
            override val syncDetail: SyncDetail,
            override val orchestratorState: SyncOrchestrator.State,
            override val now: Instant,
            override val totalDeviceCount: Int = 0,
        ) : SyncStatus

        data class Idle(
            val lastSyncAt: Instant?,
            override val connectorTypes: List<ConnectorType>,
            override val syncDetail: SyncDetail,
            override val orchestratorState: SyncOrchestrator.State,
            override val now: Instant,
            override val totalDeviceCount: Int = 0,
        ) : SyncStatus

        data class Error(
            val message: String?,
            override val connectorTypes: List<ConnectorType>,
            override val syncDetail: SyncDetail,
            override val orchestratorState: SyncOrchestrator.State,
            override val now: Instant,
            override val totalDeviceCount: Int = 0,
        ) : SyncStatus
    }

    val state: Flow<State> = combine(
        tickerUiRefresh,
        networkStateProvider.networkState,
        generalSettings.isSyncSetupDismissed.flow,
        deviceItems(),
        permissionTool.missingPermissions,
        syncStatusFlow,
        upgradeRepo.upgradeInfo,
        updateService.availableUpdate.onStart { emit(null) },
        generalSettings.dashboardConfig.flow,
        issueAggregator.issues,
    ) { now, networkState, isSyncSetupDismissed, deviceItems, missingPermissions, syncStatus, upgradeInfo, update, uiConfig, issues ->

        val connectorCount = syncManager.connectors.first().size
        val showSyncSetup = !isSyncSetupDismissed && connectorCount == 0

        val filteredPermissions = missingPermissions
            .filterNot { WIFI_PERMISSIONS.contains(it) }
            .take(1)

        // Clean config to remove stale device data, then apply custom ordering
        val currentDeviceIds = deviceItems.map { it.deviceId.id }.toSet()
        val cleanedConfig = uiConfig.toCleaned(currentDeviceIds)

        // Persist cleaned config if it changed (remove stale data from storage)
        if (cleanedConfig != uiConfig) {
            launch { generalSettings.dashboardConfig.value(cleanedConfig) }
        }

        val deviceItemsWithOrder = cleanedConfig.applyCustomOrdering(deviceItems.map { it.deviceId.id })
            .mapNotNull { deviceId -> deviceItems.find { it.deviceId.id == deviceId } }
            .let { ordered ->
                // Add any devices not in the ordered list (shouldn't happen, but safety)
                ordered + deviceItems.filter { device ->
                    ordered.none { it.deviceId.id == device.deviceId.id }
                }
            }

        // Collect all known module IDs for normalization
        val allModuleIds = ALL_MODULE_IDS

        // Apply device limit status and tile layout dynamically based on position after reordering
        val orderedDeviceItems = deviceItemsWithOrder.mapIndexed { index, item ->
            val deviceIdStr = item.deviceId.id
            val effectiveLayout = cleanedConfig.effectiveLayout(deviceIdStr).normalize(allModuleIds)
            item.copy(
                tileLayout = effectiveLayout,
                isCollapsed = cleanedConfig.isCollapsed(deviceIdStr),
                infos = buildDeviceInfos(item, issues),
                isLimited = !upgradeInfo.isPro && index >= DEVICE_LIMIT,
            )
        }

        State(
            devices = orderedDeviceItems,
            deviceCount = orderedDeviceItems.size,
            syncStatus = syncStatus,
            isSyncExpanded = uiConfig.isSyncExpanded,
            isOffline = !networkState.isInternetAvailable,
            showSyncSetup = showSyncSetup,
            missingPermissions = filteredPermissions,
            update = update,
            upgradeInfo = upgradeInfo,
            deviceLimitReached = orderedDeviceItems.size > DEVICE_LIMIT && !upgradeInfo.isPro,
            issues = issues,
        )
    }
        .setupCommonEventHandlers(TAG) { "state" }
        .asStateFlow()

    fun goToSyncServices() = launch {
        log(TAG) { "goToSyncServices()" }
        navTo(Nav.Sync.List)
    }

    fun goToConnectorDevices(connectorId: ConnectorId) = launch {
        log(TAG) { "goToConnectorDevices($connectorId)" }
        navTo(Nav.Sync.Devices(connectorId = connectorId.idString))
    }

    fun goToDeviceDetails(connectorId: ConnectorId, deviceId: DeviceId) = launch {
        log(TAG) { "goToDeviceDetails($connectorId, $deviceId)" }
        navTo(Nav.Sync.Devices(connectorId = connectorId.idString, deviceId = deviceId.id))
    }

    fun goToUpgrade() {
        navTo(Nav.Main.Upgrade())
    }

    fun goToSettings() {
        navTo(Nav.Settings.Index)
    }

    fun dismissSyncSetup() = launch {
        generalSettings.isSyncSetupDismissed.value(true)
    }

    fun setupSync() {
        navTo(Nav.Sync.List)
    }

    fun refresh() = appScope.launch {
        log(TAG) { "refresh()" }
        if (!isManuallyRefreshing.compareAndSet(expect = false, update = true)) return@launch
        try {
            syncExecutor.execute("DashboardRefresh")
        } finally {
            isManuallyRefreshing.value = false
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) permissionTool.recheck()
    }

    fun requestPermission(permission: Permission) {
        dashboardEvents.tryEmit(DashboardEvent.RequestPermissionEvent(permission))
    }

    fun dismissPermission(permission: Permission) = launch {
        generalSettings.addDismissedPermission(permission)
        dashboardEvents.tryEmit(DashboardEvent.ShowPermissionDismissHint(permission))
    }

    fun showPermissionPopup(permission: Permission) {
        dashboardEvents.tryEmit(
            DashboardEvent.ShowPermissionPopup(
                permission = permission,
                onGrant = {
                    dashboardEvents.tryEmit(DashboardEvent.RequestPermissionEvent(it))
                },
                onDismiss = {
                    runBlocking { generalSettings.addDismissedPermission(it) }
                    dashboardEvents.tryEmit(DashboardEvent.ShowPermissionDismissHint(it))
                }
            )
        )
    }

    fun moveDeviceUp(deviceId: String) = launch {
        reorderMutex.withLock {
            val currentOrder = state.first().devices.map { it.deviceId.id }
            val idx = currentOrder.indexOf(deviceId)
            if (idx <= 0) return@withLock
            val newOrder = currentOrder.toMutableList().apply { add(idx - 1, removeAt(idx)) }
            log(TAG) { "moveDeviceUp($deviceId) idx=$idx" }
            generalSettings.updateDeviceOrder(newOrder)
        }
    }

    fun moveDeviceDown(deviceId: String) = launch {
        reorderMutex.withLock {
            val currentOrder = state.first().devices.map { it.deviceId.id }
            val idx = currentOrder.indexOf(deviceId)
            if (idx < 0 || idx >= currentOrder.lastIndex) return@withLock
            val newOrder = currentOrder.toMutableList().apply { add(idx + 1, removeAt(idx)) }
            log(TAG) { "moveDeviceDown($deviceId) idx=$idx" }
            generalSettings.updateDeviceOrder(newOrder)
        }
    }

    fun toggleDeviceCollapsed(deviceId: String) = launch {
        generalSettings.toggleDeviceCollapsed(deviceId)
    }

    fun toggleSyncExpanded() = launch {
        generalSettings.dashboardConfig.update { it.copy(isSyncExpanded = !it.isSyncExpanded) }
    }

    fun saveTileLayout(deviceId: String, config: TileLayoutConfig) = launch {
        log(TAG) { "saveTileLayout(deviceId=$deviceId)" }
        generalSettings.updateTileLayout(deviceId, config, ALL_MODULE_IDS)
    }

    fun saveAsDefaultTileLayout(config: TileLayoutConfig) = launch {
        log(TAG) { "saveAsDefaultTileLayout()" }
        generalSettings.setDefaultTileLayout(config, ALL_MODULE_IDS)
    }

    fun resetTileLayout(deviceId: String) = launch {
        log(TAG) { "resetTileLayout(deviceId=$deviceId)" }
        generalSettings.resetDeviceTileLayout(deviceId)
    }

    fun goToPowerAlerts(deviceId: DeviceId) {
        navTo(Nav.Main.PowerAlerts(deviceId.id))
    }

    fun goToAppsList(deviceId: DeviceId) {
        navTo(Nav.Main.AppsList(deviceId.id))
    }

    fun onInstallLatestApp(appsInfo: AppsInfo) = launch {
        appsInfo.installedPackages.maxByOrNull { it.installedAt }?.let {
            val (main, fallback) = it.getInstallerIntent()
            dashboardEvents.tryEmit(DashboardEvent.OpenAppOrStore(main, fallback))
        }
    }

    fun clearClipboard() = launch {
        clipboardHandler.setSharedClipboard(ClipboardInfo())
    }

    fun shareCurrentClipboard() = launch {
        clipboardHandler.shareCurrentOSClipboard()
    }

    fun setOsClipboard(info: ClipboardInfo) = launch {
        clipboardHandler.setOSClipboard(info)
    }

    fun dismissUpdate() = launch {
        state.first().update?.let {
            updateService.dismissUpdate(it)
            updateService.refresh()
        }
    }

    fun viewUpdate() = launch {
        state.first().update?.let { updateService.viewUpdate(it) }
    }

    fun startUpdate() = launch {
        state.first().update?.let { updateService.startUpdate(it) }
    }

    private fun deviceItems(): Flow<List<DeviceItem>> = combine(
        tickerUiRefresh,
        moduleManager.byDevice,
        permissionTool.missingPermissions,
        syncManager.connectors,
        syncManager.states,
        alertManager.alerts,
        upgradeRepo.upgradeInfo,
        syncSettings.pausedConnectors.flow,
    ) { now, byDevice, missingPermissions, connectors, allStates, alerts, _, pausedIds ->
        val statesList = allStates.toList()
        val activeConnectors = connectors.filter { !pausedIds.contains(it.identifier) }
        val statesMap = connectors.zip(statesList).associate { (c, s) -> c.identifier to s }

        // Build normal device items from module data
        val normalItems = byDevice.devices
            .mapNotNull { (deviceId, moduleDatas) ->
                @Suppress("UNCHECKED_CAST")
                val metaModule = moduleDatas.firstOrNull { it.data is MetaInfo } as? ModuleData<MetaInfo>
                if (metaModule == null) {
                    log(TAG, WARN) { "Missing meta module for $deviceId" }
                    return@mapNotNull null
                }

                val powerAlerts = alerts.filter { it.deviceId == deviceId }
                val moduleItems = (moduleDatas.toList() - metaModule)
                    .sortedBy { it.orderPrio }
                    .mapNotNull { moduleData ->
                        @Suppress("UNCHECKED_CAST")
                        when (moduleData.data) {
                            is PowerInfo -> ModuleItem.Power(
                                data = moduleData as ModuleData<PowerInfo>,
                                batteryLowAlert = powerAlerts.firstOrNull { it.rule is BatteryLowAlertRule } as? PowerAlert<BatteryLowAlertRule>,
                                batteryHighAlert = powerAlerts.firstOrNull { it.rule is BatteryHighAlertRule } as? PowerAlert<BatteryHighAlertRule>,
                                showSettings = deviceId != syncSettings.deviceId,
                            )

                            is ConnectivityInfo -> ModuleItem.Connectivity(
                                data = moduleData as ModuleData<ConnectivityInfo>,
                            )

                            is WifiInfo -> ModuleItem.Wifi(
                                data = moduleData as ModuleData<WifiInfo>,
                                showPermissionAction = missingPermissions
                                    .any { WIFI_PERMISSIONS.contains(it) } && deviceId == syncSettings.deviceId,
                            )

                            is AppsInfo -> ModuleItem.Apps(
                                data = moduleData as ModuleData<AppsInfo>,
                            )

                            is ClipboardInfo -> ModuleItem.Clipboard(
                                data = moduleData as ModuleData<ClipboardInfo>,
                                isOurDevice = deviceId == syncSettings.deviceId,
                            )

                            else -> {
                                log(TAG, WARN) { "Unsupported module data: ${moduleData.data}" }
                                null
                            }
                        }
                    }

                DeviceItem(
                    now = now,
                    deviceId = deviceId,
                    meta = metaModule,
                    moduleItems = moduleItems,
                    isCollapsed = false, // Will be updated when applying preferences
                    isLimited = false, // Will be updated when applying preferences based on position
                    isCurrentDevice = metaModule.deviceId == syncSettings.deviceId,
                )
            }

        // Build degraded device items from connector metadata (devices known but no module data)
        val normalDeviceIds = normalItems.map { it.deviceId }.toSet()
        val degradedItems = activeConnectors.flatMap { connector ->
            val connState = statesMap[connector.identifier] ?: return@flatMap emptyList()
            connState.deviceMetadata
                .filter { meta ->
                    meta.deviceId !in normalDeviceIds
                            && meta.deviceId != syncSettings.deviceId
                            && meta.addedAt?.let { (Clock.System.now() - it) >= SyncSettings.FIRST_SYNC_GRACE_PERIOD } != false
                }
                .map { meta ->
                    DeviceItem(
                        now = now,
                        deviceId = meta.deviceId,
                        meta = null,
                        moduleItems = emptyList(),
                        isCollapsed = false,
                        isLimited = false,
                        isCurrentDevice = false,
                        isDegraded = true,
                        degradedConnectorId = connector.identifier,
                        degradedLabel = meta.label,
                        degradedPlatform = meta.platform,
                        degradedVersion = meta.version,
                        degradedLastSeen = meta.lastSeen,
                    )
                }
        }

        (normalItems + degradedItems)
            .sortedBy { it.displayLabel.lowercase() }
            .sortedByDescending { it.deviceId == syncSettings.deviceId }
            .sortedBy { it.isDegraded }
    }

    private val ModuleData<out Any>.orderPrio: Int
        get() = INFO_ORDER.indexOfFirst { it.isInstance(this.data) }


    private fun List<ConnectorDetail>.disambiguateLabels(): List<ConnectorDetail> {
        val duplicates = groupBy { it.type to it.accountLabel }.filterValues { it.size > 1 }
        if (duplicates.isEmpty()) return this
        return map { detail ->
            if (duplicates.containsKey(detail.type to detail.accountLabel)) {
                val shortId = detail.connectorId.account.take(8)
                detail.copy(accountLabel = "${detail.accountLabel} ($shortId)")
            } else {
                detail
            }
        }
    }

    companion object {
        internal fun buildDeviceInfos(
            item: DeviceItem,
            allIssues: List<ConnectorIssue>,
        ): List<ConnectorIssue> {
            return allIssues
                .filter { it.deviceId == item.deviceId }
                .sortedWith(compareBy({ if (it.severity == IssueSeverity.ERROR) 0 else 1 }, { it::class.simpleName }))
        }

        private val INFO_ORDER = listOf(
            PowerInfo::class,
            ConnectivityInfo::class,
            WifiInfo::class,
            ClipboardInfo::class,
            AppsInfo::class,
        )
        private val ALL_MODULE_IDS = setOf(
            "eu.darken.octi.module.core.power",
            "eu.darken.octi.module.core.wifi",
            "eu.darken.octi.module.core.connectivity",
            "eu.darken.octi.module.core.apps",
            "eu.darken.octi.module.core.clipboard",
        )
        private const val DEVICE_LIMIT = 3
        private val WIFI_PERMISSIONS = setOf(Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION)

        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}
