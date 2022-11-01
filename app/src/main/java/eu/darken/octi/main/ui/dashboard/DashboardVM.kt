package eu.darken.octi.main.ui.dashboard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
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
import eu.darken.octi.main.ui.dashboard.items.DeviceLimitVH
import eu.darken.octi.main.ui.dashboard.items.PermissionVH
import eu.darken.octi.main.ui.dashboard.items.SyncSetupVH
import eu.darken.octi.main.ui.dashboard.items.WelcomeVH
import eu.darken.octi.main.ui.dashboard.items.perdevice.DeviceVH
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.ui.dashboard.DeviceAppsVH
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.ui.dashboard.DevicePowerVH
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.ui.dashboard.DeviceWifiVH
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class DashboardVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val generalSettings: GeneralSettings,
    private val syncManager: SyncManager,
    private val moduleManager: ModuleManager,
    private val networkStateProvider: NetworkStateProvider,
    private val permissionTool: PermissionTool,
    private val syncSettings: SyncSettings,
    private val upgradeRepo: UpgradeRepo,
    private val webpageTool: WebpageTool,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val dashboardEvents = SingleLiveEvent<DashboardEvent>()

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
        generalSettings.isWelcomeDismissed.flow,
        generalSettings.isSyncSetupDismissed.flow,
        deviceItems(),
        permissionTool.missingPermissions,
        isManuallyRefreshing,
        upgradeRepo.upgradeInfo,
    ) { now, networkState, isWelcomeDismissed, isSyncSetupDismissed, deviceItems, missingPermissions, isRefreshing, upgradeInfo ->
        val items = mutableListOf<DashboardAdapter.Item>()

        if (!isWelcomeDismissed && !upgradeInfo.isPro) {
            WelcomeVH.Item(
                onDismiss = { generalSettings.isWelcomeDismissed.value = true },
                onUpgrade = { upgradeToOctiPro() }
            ).run { items.add(this) }
        }

        val connectorCount = syncManager.connectors.first().size
        if (!isSyncSetupDismissed && connectorCount == 0) {
            SyncSetupVH.Item(
                onDismiss = { generalSettings.isSyncSetupDismissed.value = true },
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
                        generalSettings.addDismissedPermission(it)
                        dashboardEvents.postValue(DashboardEvent.ShowPermissionDismissHint(it))
                    }
                )
            }.run { items.addAll(this) }

        if (deviceItems.size > DEVICE_LIMIT && !upgradeInfo.isPro) {
            log(TAG, WARN) { "Exceeding device limit: $deviceItems" }
            DeviceLimitVH.Item(
                current = deviceItems.size,
                maximum = DEVICE_LIMIT,
                onUpgrade = { dashboardEvents.postValue(DashboardEvent.LaunchUpgradeFlow(UpgradeRepo.Type.GPLAY)) },
            ).run { items.add(this) }
            items.addAll(deviceItems.take(DEVICE_LIMIT))
        } else {
            items.addAll(deviceItems)
        }

        val lastConnectorActivity = syncManager.states.first().mapNotNull { it.lastSyncAt }.maxByOrNull { it }

        State(
            items = items,
            deviceCount = deviceItems.size,
            lastSyncAt = lastConnectorActivity,
            isRefreshing = isRefreshing,
            isOffline = !networkState.isInternetAvailable,
        )
    }.asLiveData2()

    fun goToSyncServices() = launch {
        log(TAG) { "goToSyncServices()" }
        DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate()
    }

    fun upgradeToOctiPro() = launch {
        log(TAG) { "upgradeToOctiPro()" }
        dashboardEvents.postValue(DashboardEvent.LaunchUpgradeFlow(UpgradeRepo.Type.GPLAY))
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

    fun launchUpgradeFlow(activity: Activity) {
        log(TAG) { "launchUpgradeFlow(activity=$activity)" }
        upgradeRepo.launchBillingFlow(activity)
    }

    private fun deviceItems(): Flow<List<DeviceVH.Item>> = combine(
        tickerUiRefresh,
        moduleManager.byDevice,
        permissionTool.missingPermissions,
        syncManager.connectors,
    ) { now, byDevice, missingPermissions, connectors ->
        byDevice.devices
            .mapNotNull { (deviceId, moduleDatas) ->
                val metaModule =
                    moduleDatas.firstOrNull { it.data is MetaInfo } as? ModuleData<MetaInfo>
                if (metaModule == null) {
                    log(TAG, WARN) { "Missing meta module for $deviceId" }
                    return@mapNotNull null
                }

                val moduleItems = (moduleDatas.toList() - metaModule)
                    .sortedBy { it.orderPrio }
                    .mapNotNull { moduleData ->
                        when (moduleData.data) {
                            is PowerInfo -> (moduleData as ModuleData<PowerInfo>).createVHItem()
                            is WifiInfo -> (moduleData as ModuleData<WifiInfo>).createVHItem(missingPermissions)
                            is AppsInfo -> (moduleData as ModuleData<AppsInfo>).createVHItem()
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
                )
            }
            .sortedBy { it.meta.data.deviceLabel ?: it.meta.data.deviceName }
            .sortedByDescending { it.meta.deviceId == syncSettings.deviceId }
    }

    private val ModuleData<out Any>.orderPrio: Int
        get() = INFO_ORDER.indexOfFirst { it.isInstance(this.data) }

    private fun ModuleData<PowerInfo>.createVHItem() = DevicePowerVH.Item(
        data = this,
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
                            generalSettings.addDismissedPermission(it)
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
            val pkg =
                data.installedPackages.maxByOrNull { it.installedAt }?.packageName ?: BuildConfigWrap.APPLICATION_ID
            webpageTool.open("https://play.google.com/store/apps/details?id=$pkg")
        }
    )

    companion object {
        private val INFO_ORDER = listOf(
            PowerInfo::class,
            WifiInfo::class,
            AppsInfo::class,
        )
        private const val DEVICE_LIMIT = 3
        private val WIFI_PERMISSIONS = setOf(Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION)

        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}