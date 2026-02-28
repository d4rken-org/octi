package eu.darken.octi.main.ui.settings.acks

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel4
import javax.inject.Inject

@HiltViewModel
class AcknowledgementsVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    companion object {
        private val TAG = logTag("Settings", "Acknowledgements", "VM")
    }
}
