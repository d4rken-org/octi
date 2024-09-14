package eu.darken.octi.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.recording.core.RecorderModule
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.uix.ViewModel3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SupportFragmentVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
) : ViewModel3(dispatcherProvider) {

    val isRecording = recorderModule.state.map { it.currentLogPath }.asLiveData2()

    val events = SingleLiveEvent<SupportEvent>()

    fun toggleDebugLog(consent: Boolean = false) = launch {
        log { "toggleDebugLog()" }
        recorderModule.apply {
            if (state.first().isRecording) {
                stopRecorder()
            } else {
                if (consent) {
                    startRecorder()
                } else {
                    events.postValue(SupportEvent.DebugLogInfo)
                }
            }
        }
    }
}