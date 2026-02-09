package eu.darken.octi.modules.apps.ui.appslist

import android.annotation.SuppressLint
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.modules.apps.core.AppsRepo
import eu.darken.octi.modules.apps.core.AppsSettings
import eu.darken.octi.modules.apps.core.AppsSortMode
import eu.darken.octi.modules.apps.core.getInstallerIntent
import eu.darken.octi.modules.meta.core.MetaRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class AppsListVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    metaRepo: MetaRepo,
    appsRepo: AppsRepo,
    private val appsSettings: AppsSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: AppsListFragmentArgs by handle.navArgs()

    val events = SingleLiveEvent<AppListAction>()

    data class State(
        val deviceLabel: String = "",
        val items: List<AppsListAdapter.Item> = emptyList(),
        val sortMode: AppsSortMode = AppsSortMode.NAME,
    )

    val listItems: LiveData<State> = combine(
        metaRepo.state,
        appsRepo.state,
        appsSettings.sortMode.flow,
    ) { metaState, appsState, sortMode ->
        val metaData = metaState.all.firstOrNull { it.deviceId == navArgs.deviceId }
        val moduleData = appsState.all.firstOrNull { it.deviceId == navArgs.deviceId }

        if (metaData == null || moduleData == null) {
            log(TAG, ERROR) { "No module data found for ${navArgs.deviceId}" }
            popNavStack()
            return@combine State()
        }

        val items = moduleData.data.installedPackages
            .map { pkg ->
                DefaultPkgVH.Item(
                    pkg = pkg,
                    onClick = {
                        val (main, fallback) = pkg.getInstallerIntent()
                        events.postValue(AppListAction.OpenAppOrStore(main, fallback))
                    }
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
    }.asLiveData2()

    fun updateSortMode(mode: AppsSortMode) = launch {
        appsSettings.sortMode.value(mode)
    }

    companion object {
        private val TAG = logTag("Module", "Apps", "List", "Fragment", "VM")
    }
}