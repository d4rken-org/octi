package eu.darken.octi.modules.apps.ui.appslist

import android.annotation.SuppressLint
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.AppsRepo
import eu.darken.octi.modules.apps.core.AppsSettings
import eu.darken.octi.modules.apps.core.AppsSortMode
import eu.darken.octi.modules.apps.core.getInstallerIntent
import eu.darken.octi.modules.meta.core.MetaRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class AppsListVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    metaRepo: MetaRepo,
    appsRepo: AppsRepo,
    private val appsSettings: AppsSettings,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val deviceIdFlow = MutableStateFlow<DeviceId?>(null)

    val events = SingleEventFlow<AppListAction>()

    data class PkgItem(
        val pkg: AppsInfo.Pkg,
        val onClick: () -> Unit,
    )

    data class State(
        val deviceLabel: String = "",
        val items: List<PkgItem> = emptyList(),
        val sortMode: AppsSortMode = AppsSortMode.NAME,
    )

    val state = deviceIdFlow
        .filterNotNull()
        .flatMapLatest { deviceId ->
            combine(
                metaRepo.state,
                appsRepo.state,
                appsSettings.sortMode.flow,
            ) { metaState, appsState, sortMode ->
                val metaData = metaState.all.firstOrNull { it.deviceId == deviceId }
                val moduleData = appsState.all.firstOrNull { it.deviceId == deviceId }

                if (metaData == null || moduleData == null) {
                    log(TAG, ERROR) { "No module data found for $deviceId" }
                    navUp()
                    return@combine State()
                }

                val items = moduleData.data.installedPackages
                    .map { pkg ->
                        PkgItem(
                            pkg = pkg,
                            onClick = {
                                val (main, fallback) = pkg.getInstallerIntent()
                                events.tryEmit(AppListAction.OpenAppOrStore(main, fallback))
                            },
                        )
                    }
                    .let { list ->
                        when (sortMode) {
                            AppsSortMode.NAME -> list.sortedBy { it.pkg.label?.lowercase() ?: it.pkg.packageName.lowercase() }
                            AppsSortMode.INSTALLED_AT -> list.sortedByDescending { it.pkg.installedAt }
                            AppsSortMode.UPDATED_AT -> list.sortedByDescending { it.pkg.updatedAt ?: it.pkg.installedAt }
                        }
                    }

                State(
                    deviceLabel = metaData.data.deviceLabel ?: metaData.data.deviceName,
                    items = items,
                    sortMode = sortMode,
                )
            }
        }
        .asStateFlow()

    fun initialize(deviceId: String) {
        log(TAG) { "initialize($deviceId)" }
        deviceIdFlow.value = DeviceId(deviceId)
    }

    fun updateSortMode(mode: AppsSortMode) = launch {
        appsSettings.sortMode.value(mode)
    }

    companion object {
        private val TAG = logTag("Module", "Apps", "List", "VM")
    }
}
