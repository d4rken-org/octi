package eu.darken.octi.main.ui.dashboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.ui.dashboard.items.PermissionVH
import eu.darken.octi.main.ui.dashboard.items.WelcomeVH
import eu.darken.octi.main.ui.dashboard.items.perdevice.DeviceVH
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.ui.dashboard.DevicePowerVH
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.ui.dashboard.DeviceWifiVH
import eu.darken.octi.sync.core.SyncManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class DashboardVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val syncManager: SyncManager,
    private val moduleManager: ModuleManager,
    private val permissionTool: PermissionTool,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val dashboardEvents = SingleLiveEvent<DashboardEvent>()

    data class State(
        val items: List<DashboardAdapter.Item>,
    )

    private val refreshTicker = flow {
        while (currentCoroutineContext().isActive) {
            emit(Instant.now())
            if (BuildConfigWrap.DEBUG) {
                delay(1000)
            } else {
                delay(60 * 1000)
            }
        }
    }

    val listItems: LiveData<State> = combine(
        refreshTicker,
        generalSettings.isWelcomeDismissed.flow,
        moduleManager.byDevice,
        permissionTool.missingPermissions
    ) { now, isWelcomeDismissed, byDevice, missingPermissions ->
        val items = mutableListOf<DashboardAdapter.Item>()

        if (!isWelcomeDismissed) {
            WelcomeVH.Item(
                onDismiss = { generalSettings.isWelcomeDismissed.value = true },
                onSetup = { DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate() }
            ).run { items.add(this) }
        }

        missingPermissions.map { perm ->
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
                val metaModule = moduleDatas.firstOrNull { it.data is MetaInfo } as? ModuleData<MetaInfo>
                if (metaModule == null) {
                    log(TAG, WARN) { "Missing meta module for $deviceId" }
                    return@mapNotNull null
                }

                val moduleItems = (moduleDatas.toList() - metaModule)
                    .sortedBy { it.orderPrio }
                    .mapNotNull {
                        when (it.data) {
                            is PowerInfo -> DevicePowerVH.Item(
                                data = it as ModuleData<PowerInfo>,
                            )
                            is WifiInfo -> DeviceWifiVH.Item(
                                data = it as ModuleData<WifiInfo>,
                            )
                            else -> {
                                log(TAG, WARN) { "Unsupported module data: ${it.data}" }
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
            .toList().let { items.addAll(it) }

        State(items = items)
    }.asLiveData2()

    fun goToSyncServices() = launch {
        log(TAG) { "goToSyncServices()" }
        DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate()
    }

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        syncManager.triggerSync()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) permissionTool.recheck()
    }

    private val ModuleData<out Any>.orderPrio: Int
        get() = INFO_ORDER.indexOfFirst { it.isInstance(this.data) }

    companion object {
        private val INFO_ORDER = listOf(
            PowerInfo::class,
            WifiInfo::class
        )
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}