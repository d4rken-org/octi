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
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.getInstallerIntent
import eu.darken.octi.modules.clipboard.ClipboardHandler
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.core.alert.PowerAlertManager
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncExecutor
import eu.darken.octi.sync.core.SyncManager
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
import java.time.Instant
import javax.inject.Inject

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
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    init {
        if (!generalSettings.isOnboardingDone.valueBlocking) {
            navTo(Nav.Main.Welcome)
        }
    }

    val dashboardEvents = SingleEventFlow<DashboardEvent>()

    private val isManuallyRefreshing = MutableStateFlow(false)

    private val tickerUiRefresh = flow {
        while (currentCoroutineContext().isActive) {
            emit(Instant.now())
            delay(60 * 1000)
        }
    }

    data class State(
        val devices: List<DeviceItem>,
        val deviceCount: Int,
        val lastSyncAt: Instant?,
        val isRefreshing: Boolean,
        val isOffline: Boolean,
        val showSyncSetup: Boolean,
        val missingPermissions: List<Permission>,
        val update: UpdateChecker.Update?,
        val upgradeInfo: UpgradeRepo.Info,
        val deviceLimitReached: Boolean,
    )

    data class DeviceItem(
        val now: Instant,
        val meta: ModuleData<MetaInfo>,
        val moduleItems: List<ModuleItem>,
        val isCollapsed: Boolean,
        val isLimited: Boolean,
        val isCurrentDevice: Boolean,
    )

    sealed interface ModuleItem {
        data class Power(
            val data: ModuleData<PowerInfo>,
            val batteryLowAlert: PowerAlert<BatteryLowAlertRule>?,
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

    val state: Flow<State> = combine(
        tickerUiRefresh,
        networkStateProvider.networkState,
        generalSettings.isSyncSetupDismissed.flow,
        deviceItems(),
        permissionTool.missingPermissions,
        isManuallyRefreshing,
        upgradeRepo.upgradeInfo,
        updateService.availableUpdate.onStart { emit(null) },
        generalSettings.dashboardConfig.flow,
    ) { now, networkState, isSyncSetupDismissed, deviceItems, missingPermissions, isRefreshing, upgradeInfo, update, uiConfig ->

        val connectorCount = syncManager.connectors.first().size
        val showSyncSetup = !isSyncSetupDismissed && connectorCount == 0

        val filteredPermissions = missingPermissions
            .filterNot { WIFI_PERMISSIONS.contains(it) }
            .toList()

        // Clean config to remove stale device data, then apply custom ordering
        val currentDeviceIds = deviceItems.map { it.meta.deviceId.id }.toSet()
        val cleanedConfig = uiConfig.toCleaned(currentDeviceIds)

        // Persist cleaned config if it changed (remove stale data from storage)
        if (cleanedConfig != uiConfig) {
            launch { generalSettings.dashboardConfig.value(cleanedConfig) }
        }

        val deviceItemsWithOrder = cleanedConfig.applyCustomOrdering(deviceItems.map { it.meta.deviceId.id })
            .mapNotNull { deviceId -> deviceItems.find { it.meta.deviceId.id == deviceId } }
            .let { ordered ->
                // Add any devices not in the ordered list (shouldn't happen, but safety)
                ordered + deviceItems.filter { device ->
                    ordered.none { it.meta.deviceId.id == device.meta.deviceId.id }
                }
            }

        // Apply device limit status dynamically based on position after reordering
        val orderedDeviceItems = deviceItemsWithOrder.mapIndexed { index, item ->
            item.copy(
                isCollapsed = cleanedConfig.isCollapsed(item.meta.deviceId.id),
                isLimited = !upgradeInfo.isPro && index >= DEVICE_LIMIT,
            )
        }

        val lastConnectorActivity = syncManager.states.first().mapNotNull { it.lastSyncAt }.maxByOrNull { it }

        State(
            devices = orderedDeviceItems,
            deviceCount = orderedDeviceItems.size,
            lastSyncAt = lastConnectorActivity,
            isRefreshing = isRefreshing,
            isOffline = !networkState.isInternetAvailable,
            showSyncSetup = showSyncSetup,
            missingPermissions = filteredPermissions,
            update = update,
            upgradeInfo = upgradeInfo,
            deviceLimitReached = orderedDeviceItems.size > DEVICE_LIMIT && !upgradeInfo.isPro,
        )
    }
        .setupCommonEventHandlers(TAG) { "state" }
        .asStateFlow()

    fun goToSyncServices() = launch {
        log(TAG) { "goToSyncServices()" }
        navTo(Nav.Sync.List)
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

    fun dismissPermission(permission: Permission) {
        runBlocking { generalSettings.addDismissedPermission(permission) }
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

    fun updateDeviceOrder(newOrder: List<String>) = launch {
        log(TAG) { "updateDeviceOrder(newOrder=$newOrder)" }
        generalSettings.updateDeviceOrder(newOrder)
    }

    fun toggleDeviceCollapsed(deviceId: String) = launch {
        generalSettings.toggleDeviceCollapsed(deviceId)
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
        alertManager.alerts,
        upgradeRepo.upgradeInfo,
    ) { now, byDevice, missingPermissions, _, alerts, _ ->
        byDevice.devices
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
                    meta = metaModule,
                    moduleItems = moduleItems,
                    isCollapsed = false, // Will be updated when applying preferences
                    isLimited = false, // Will be updated when applying preferences based on position
                    isCurrentDevice = metaModule.deviceId == syncSettings.deviceId,
                )
            }
            .sortedBy { it.meta.data.deviceLabel ?: it.meta.data.deviceName }
            .sortedByDescending { it.meta.deviceId == syncSettings.deviceId }
    }

    private val ModuleData<out Any>.orderPrio: Int
        get() = INFO_ORDER.indexOfFirst { it.isInstance(this.data) }


    companion object {
        private val INFO_ORDER = listOf(
            PowerInfo::class,
            ConnectivityInfo::class,
            WifiInfo::class,
            ClipboardInfo::class,
            AppsInfo::class,
        )
        private const val DEVICE_LIMIT = 3
        private val WIFI_PERMISSIONS = setOf(Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION)

        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}
