package eu.darken.octi.modules.clipboard.ui.detail

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.clipboard.ClipboardHandler
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.ClipboardRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class ClipboardDetailVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    clipboardRepo: ClipboardRepo,
    private val clipboardHandler: ClipboardHandler,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: ClipboardDetailFragmentArgs by handle.navArgs()

    data class State(
        val clipboardData: ModuleData<ClipboardInfo>? = null,
    )

    val state = clipboardRepo.state
        .map { repoState ->
            val data = repoState.all.firstOrNull { it.deviceId == navArgs.deviceId }
            State(clipboardData = data)
        }
        .asLiveData2()

    fun copyToClipboard() = launch {
        val info = state.value?.clipboardData?.data ?: return@launch
        clipboardHandler.setOSClipboard(info)
    }

    companion object {
        private val TAG = logTag("Module", "Clipboard", "Detail", "VM")
    }
}
