package eu.darken.octi.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.recording.core.RecorderModule
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.uix.ViewModel4
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isRecording: Boolean,
        val currentLogPath: File?,
    )

    val state = recorderModule.state
        .map {
            State(
                isRecording = it.isRecording,
                currentLogPath = it.currentLogPath,
            )
        }
        .shareLatest(scope = vmScope)

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        log(TAG) { "stopDebugLog()" }
        recorderModule.stopRecorder()
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "VM")
    }
}
