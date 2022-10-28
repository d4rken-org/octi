package eu.darken.octi.main.ui.dashboard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val dashboardEvents = SingleLiveEvent<DashboardEvent>()

    data class State(
        val items: List<DashboardAdapter.Item>,
        val isRefreshing: Boolean,
    )

    private val isManuallyRefreshing = MutableStateFlow(false)

    private val tickerUiRefresh = flow {
        while (currentCoroutineContext().isActive) {
            emit(Instant.now())
            // "Last seen update"
            val netstate = networkStateProvider.networkState.first()
            if (netstate.isInternetAvailable && !netstate.isMeteredConnection) {
                log(TAG) { "Auto data refresh..." }
                doRefresh()
            }
            delay(60 * 1000)
        }
    }

    val listItems: LiveData<State> = combine(
        tickerUiRefresh,
        generalSettings.isWelcomeDismissed.flow,
        generalSettings.isSyncSetupDismissed.flow,
        moduleManager.byDevice,
        permissionTool.missingPermissions,
        syncManager.connectors,
        isManuallyRefreshing,
        upgradeRepo.upgradeInfo,
    ) { now, isWelcomeDismissed, isSyncSetupDismissed, byDevice, missingPermissions, connectors, isRefreshing, upgradeInfo ->
        val items = mutableListOf<DashboardAdapter.Item>()

        if (!isWelcomeDismissed && !upgradeInfo.isPro) {
            WelcomeVH.Item(
                onDismiss = { generalSettings.isWelcomeDismissed.value = true },
                onUpgrade = { dashboardEvents.postValue(DashboardEvent.LaunchUpgradeFlow(UpgradeRepo.Type.GPLAY)) }
            ).run { items.add(this) }
        }

        if (!isSyncSetupDismissed && byDevice.devices.size <= 1) {
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
            .toList().let { items.addAll(it) }

        State(
            items = items,
            isRefreshing = isRefreshing,
        )
    }.asLiveData2()

    fun goToSyncServices() = launch {
        log(TAG) { "goToSyncServices()" }
        DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate()
    }

    fun refresh() = appScope.launch {
        log(TAG) { "refresh()" }
        doRefresh()
    }

    private val refreshLock = Mutex()
    private suspend fun doRefresh() {
        if (isManuallyRefreshing.value) return
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
        }
    )

    companion object {
        private val INFO_ORDER = listOf(
            PowerInfo::class,
            WifiInfo::class,
            AppsInfo::class,
        )

        private val WIFI_PERMISSIONS = setOf(Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION)

        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}