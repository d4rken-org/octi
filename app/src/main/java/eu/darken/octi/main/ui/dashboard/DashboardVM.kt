package eu.darken.octi.main.ui.dashboard

import android.annotation.SuppressLint
import androidx.lifecycle.LiveData
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
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.updater.UpdateService
import eu.darken.octi.main.ui.dashboard.items.DeviceLimitVH
import eu.darken.octi.main.ui.dashboard.items.PermissionVH
import eu.darken.octi.main.ui.dashboard.items.SyncSetupVH
import eu.darken.octi.main.ui.dashboard.items.UpdateCardVH
import eu.darken.octi.main.ui.dashboard.items.UpgradeCardVH
import eu.darken.octi.main.ui.dashboard.items.perdevice.DeviceVH
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.getInstallerIntent
import eu.darken.octi.modules.apps.ui.dashboard.DeviceAppsVH
import eu.darken.octi.modules.clipboard.ClipboardHandler
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.ClipboardVH
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.core.alert.PowerAlertManager
import eu.darken.octi.modules.power.ui.dashboard.DevicePowerVH
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.ui.dashboard.DeviceConnectivityVH
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.ui.dashboard.DeviceWifiVH
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    init {
        if (!generalSettings.isOnboardingDone.valueBlocking) {
            DashboardFragmentDirections.actionDashFragmentToWelcomeFragment().navigate()
        }
    }

    val dashboardEvents = SingleLiveEvent<DashboardEvent>()

    var onStartDragListener: ((androidx.recyclerview.widget.RecyclerView.ViewHolder) -> Unit)? = null

    private val isManuallyRefreshing = MutableStateFlow(false)

    private val tickerUiRefresh = flow {
        while (currentCoroutineContext().isActive) {
            emit(Instant.now())
            delay(60 * 1000)
        }
    }

    val upgradeStatus = upgradeRepo.upgradeInfo.asLiveData2()

    data class State(
        val items: List<DashboardAdapter.Item>,
        val deviceCount: Int,
        val lastSyncAt: Instant?,
        val isRefreshing: Boolean,
        val isOffline: Boolean,
    )

    val state: LiveData<State> = combine(
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
        val items = mutableListOf<DashboardAdapter.Item>()

        val connectorCount = syncManager.connectors.first().size
        if (!isSyncSetupDismissed && connectorCount == 0) {
            SyncSetupVH.Item(
                onDismiss = { launch { generalSettings.isSyncSetupDismissed.value(true) } },
                onSetup = { DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate() }
            ).run { items.add(this) }
        }

        missingPermissions
            .filterNot { WIFI_PERMISSIONS.contains(it) } // Inline handling by wifi row
            .map { perm ->
                PermissionVH.Item(
                    permission = perm,
                    onGrant = {
                        dashboardEvents.postValue(DashboardEvent.RequestPermissionEvent(it))
                    },
                    onDismiss = {
                        runBlocking { generalSettings.addDismissedPermission(it) }
                        dashboardEvents.postValue(DashboardEvent.ShowPermissionDismissHint(it))
                    }
                )
            }.run { items.addAll(this) }

        if (update != null) {
            UpdateCardVH.Item(
                update = update,
                onDismiss = {
                    launch {
                        updateService.dismissUpdate(update)
                        updateService.refresh()
                    }
                },
                onViewUpdate = {
                    launch { updateService.viewUpdate(update) }
                },
                onUpdate = {
                    launch { updateService.startUpdate(update) }
                }
            ).run { items.add(this) }
        }

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
                isCurrentDevice = item.isCurrentDevice, // Preserve current device flag
                onToggleCollapse = { deviceId: String ->
                    launch { generalSettings.toggleDeviceCollapsed(deviceId) }
                },
                onStartDrag = onStartDragListener
            )
        }

        if (orderedDeviceItems.size > DEVICE_LIMIT && !upgradeInfo.isPro) {
            log(TAG, WARN) { "Exceeding device limit: $orderedDeviceItems" }
            items.addAll(orderedDeviceItems.take(DEVICE_LIMIT))
            DeviceLimitVH.Item(
                current = orderedDeviceItems.size,
                maximum = DEVICE_LIMIT,
                onManageDevices = { DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate() },
                onUpgrade = { DashboardFragmentDirections.goToUpgradeFragment().navigate() },
            ).run { items.add(this) }
            items.addAll(orderedDeviceItems.drop(DEVICE_LIMIT))
        } else {
            items.addAll(orderedDeviceItems)
        }

        if (!upgradeInfo.isPro) {
            UpgradeCardVH.Item(
                onUpgrade = { DashboardFragmentDirections.goToUpgradeFragment().navigate() }
            ).run { items.add(this) }
        }

        val lastConnectorActivity = syncManager.states.first().mapNotNull { it.lastSyncAt }.maxByOrNull { it }

        State(
            items = items,
            deviceCount = orderedDeviceItems.size,
            lastSyncAt = lastConnectorActivity,
            isRefreshing = isRefreshing,
            isOffline = !networkState.isInternetAvailable,
        )
    }.asLiveData2()

    fun goToSyncServices() = launch {
        log(TAG) { "goToSyncServices()" }
        DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate()
    }

    private val refreshLock = Mutex()
    fun refresh() = appScope.launch {
        log(TAG) { "refresh()" }
        if (isManuallyRefreshing.value) return@launch
        refreshLock.withLock {
            try {
                isManuallyRefreshing.value = true
                moduleManager.refresh()
                syncManager.sync()
            } finally {
                isManuallyRefreshing.value = false
            }
        }
    }


    fun onPermissionResult(granted: Boolean) {
        if (granted) permissionTool.recheck()
    }

    fun updateDeviceOrder(newOrder: List<String>) = launch {
        log(TAG) { "updateDeviceOrder(newOrder=$newOrder)" }
        generalSettings.updateDeviceOrder(newOrder)
    }

    private fun deviceItems(): Flow<List<DeviceVH.Item>> = combine(
        tickerUiRefresh,
        moduleManager.byDevice,
        permissionTool.missingPermissions,
        syncManager.connectors,
        alertManager.alerts,
        upgradeRepo.upgradeInfo,
    ) { now, byDevice, missingPermissions, connectors, alerts, upgradeInfo ->
        byDevice.devices
            .mapNotNull { (deviceId, moduleDatas) ->
                val metaModule = moduleDatas.firstOrNull { it.data is MetaInfo } as? ModuleData<MetaInfo>
                if (metaModule == null) {
                    log(TAG, WARN) { "Missing meta module for $deviceId" }
                    return@mapNotNull null
                }

                val powerAlerts = alerts.filter { it.deviceId == deviceId }
                val moduleItems = (moduleDatas.toList() - metaModule)
                    .sortedBy { it.orderPrio }
                    .mapNotNull { moduleData ->
                        when (moduleData.data) {
                            is PowerInfo -> (moduleData as ModuleData<PowerInfo>).createVHItem(powerAlerts)
                            is ConnectivityInfo -> (moduleData as ModuleData<ConnectivityInfo>).createVHItem()
                            is WifiInfo -> (moduleData as ModuleData<WifiInfo>).createVHItem(missingPermissions)
                            is AppsInfo -> (moduleData as ModuleData<AppsInfo>).createVHItem()
                            is ClipboardInfo -> (moduleData as ModuleData<ClipboardInfo>).createVHItem()
                            else -> {
                                log(TAG, WARN) { "Unsupported module data: ${moduleData.data}" }
                                null
                            }
                        }
                    }

                DeviceVH.Item(
                    now = now,
                    meta = metaModule,
                    moduleItems = moduleItems,
                    isCollapsed = false, // Will be updated when applying preferences
                    isLimited = false, // Will be updated when applying preferences based on position
                    isCurrentDevice = metaModule.deviceId == syncSettings.deviceId,
                    onUpgrade = { DashboardFragmentDirections.goToUpgradeFragment().navigate() },
                    onManageStaleDevice = {
                        DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate()
                    },
                    onToggleCollapse = { _ -> }, // Will be updated when applying preferences
                    onStartDrag = null // Will be updated when applying preferences
                )
            }
            .sortedBy { it.meta.data.deviceLabel ?: it.meta.data.deviceName }
            .sortedByDescending { it.meta.deviceId == syncSettings.deviceId }
    }

    private val ModuleData<out Any>.orderPrio: Int
        get() = INFO_ORDER.indexOfFirst { it.isInstance(this.data) }


    private fun ModuleData<PowerInfo>.createVHItem(
        powerAlertRules: Collection<PowerAlert<*>>
    ): DevicePowerVH.Item = DevicePowerVH.Item(
        data = this,
        batteryLowAlert = run {
            @Suppress("UNCHECKED_CAST")
            powerAlertRules.firstOrNull { it.rule is BatteryLowAlertRule } as PowerAlert<BatteryLowAlertRule>?
        },
        onSettingsAction = {
            DashboardFragmentDirections.actionDashFragmentToPowerAlertsFragment(deviceId).navigate()
        }.takeIf { deviceId != syncSettings.deviceId },
    )

    private fun ModuleData<WifiInfo>.createVHItem(
        missingPermissions: Collection<Permission>,
    ) = DeviceWifiVH.Item(
        data = this,
        onGrantPermission = missingPermissions
            .firstOrNull { WIFI_PERMISSIONS.contains(it) }
            ?.takeIf { deviceId == syncSettings.deviceId }
            ?.let {
                // Click listener for row
                {
                    DashboardEvent.ShowPermissionPopup(
                        permission = it,
                        onGrant = {
                            dashboardEvents.postValue(DashboardEvent.RequestPermissionEvent(it))
                        },
                        onDismiss = {
                            runBlocking { generalSettings.addDismissedPermission(it) }
                            dashboardEvents.postValue(DashboardEvent.ShowPermissionDismissHint(it))
                        }
                    ).run { dashboardEvents.postValue(this) }
                }
            }
    )

    private fun ModuleData<AppsInfo>.createVHItem() = DeviceAppsVH.Item(
        data = this,
        onAppsInfoClicked = {
            DashboardFragmentDirections.actionDashFragmentToAppsListFragment(deviceId).navigate()
        },
        onInstallClicked = {
            this.data.installedPackages.maxByOrNull { it.installedAt }?.let {
                val (main, fallback) = it.getInstallerIntent()
                dashboardEvents.postValue(DashboardEvent.OpenAppOrStore(main, fallback))
            }
        }
    )

    private fun ModuleData<ConnectivityInfo>.createVHItem() = DeviceConnectivityVH.Item(
        data = this,
        onDetailClicked = {
            DashboardFragmentDirections.actionDashFragmentToConnectivityDetailFragment(deviceId).navigate()
        },
    )

    private fun ModuleData<ClipboardInfo>.createVHItem() = ClipboardVH.Item(
        data = this,
        isOurDevice = deviceId == syncSettings.deviceId,
        onClearClicked = { launch { clipboardHandler.setSharedClipboard(ClipboardInfo()) } },
        onPasteClicked = { launch { clipboardHandler.shareCurrentOSClipboard() } }
            .takeIf { deviceId == syncSettings.deviceId },
        onCopyClicked = { launch { clipboardHandler.setOSClipboard(data) } }
    )


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