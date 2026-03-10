package eu.darken.octi.main.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel4
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class SettingsIndexVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val placeholder: Boolean = true,
    )

    val state = flow {
        emit(State())
    }.asStateFlow()

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    companion object {
        private val TAG = logTag("Settings", "Index", "VM")
    }
}
