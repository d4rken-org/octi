package eu.darken.octi.modules.apps.ui.appslist

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.modules.apps.core.AppsRepo
import eu.darken.octi.modules.meta.core.MetaRepo
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class AppsListVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    metaRepo: MetaRepo,
    appsRepo: AppsRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: AppsListFragmentArgs by handle.navArgs()

    val events = SingleLiveEvent<AppListAction>()

    data class State(
        val deviceLabel: String = "",
        val items: List<AppsListAdapter.Item> = emptyList(),
    )

    val listItems: LiveData<State> = combine(
        metaRepo.state,
        appsRepo.state
    ) { metaState, appsState ->
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
                        val intent = Intent().apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse("market://details?id=${pkg.packageName}")
                            setPackage("com.android.vending")
                        }
                        val fallback = Intent().apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse("https://play.google.com/store/apps/details?id=${pkg.packageName}")
                        }
                        events.postValue(AppListAction.OpenAppOrStore(intent, fallback))
                    }
                )
            }
            .sortedByDescending { it.pkg.installedAt }

        State(
            deviceLabel = metaData.data.deviceLabel ?: metaData.data.deviceName,
            items = items,
        )
    }.asLiveData2()

    companion object {
        private val TAG = logTag("Module", "Apps", "List", "Fragment", "VM")
    }
}