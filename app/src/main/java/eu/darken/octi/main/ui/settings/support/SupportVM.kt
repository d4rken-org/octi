package eu.darken.octi.main.ui.settings.support

import android.content.Context
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.recording.core.RecorderModule
import eu.darken.octi.common.debug.recording.ui.RecorderActivity
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val webpageTool: WebpageTool,
    @ApplicationContext private val context: Context,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isRecording: Boolean = false,
        val currentLogPath: File? = null,
        val totalLogSize: Long = 0L,
        val sessionCount: Int = 0,
        val showShortRecordingWarning: Boolean = false,
    )

    private val stater = DynamicStateFlow(TAG, vmScope) { State() }
    val state = stater.flow.shareLatest(scope = vmScope)

    val launchRecorderEvent = SingleEventFlow<Intent>()

    init {
        recorderModule.state
            .onEach { recState ->
                stater.updateBlocking {
                    copy(
                        isRecording = recState.isRecording,
                        currentLogPath = recState.currentLogPath,
                    )
                }
            }
            .launchInViewModel()

        launch { refreshSessionInfo() }
    }

    private suspend fun refreshSessionInfo() {
        val sessions = recorderModule.listSessions()
        val totalSize = recorderModule.totalSessionSize()
        stater.updateBlocking {
            copy(totalLogSize = totalSize, sessionCount = sessions.size)
        }
    }

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        log(TAG) { "stopDebugLog()" }
        val recState = recorderModule.state.first()
        val startedAt = recState.recordingStartedAt
        if (startedAt != null && System.currentTimeMillis() - startedAt < SHORT_RECORDING_THRESHOLD) {
            stater.updateBlocking { copy(showShortRecordingWarning = true) }
            return@launch
        }
        doStopAndShowRecorder()
    }

    fun dismissShortRecordingWarning() = launch {
        stater.updateBlocking { copy(showShortRecordingWarning = false) }
    }

    fun forceStopDebugLog() = launch {
        stater.updateBlocking { copy(showShortRecordingWarning = false) }
        doStopAndShowRecorder()
    }

    private suspend fun doStopAndShowRecorder() {
        val session = recorderModule.stopRecorder() ?: return
        refreshSessionInfo()
        val intent = RecorderActivity.getLaunchIntent(context, session.sessionDir.absolutePath).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchRecorderEvent.tryEmit(intent)
    }

    fun deleteAllLogs() = launch {
        log(TAG) { "deleteAllLogs()" }
        recorderModule.deleteAllSessions()
        refreshSessionInfo()
    }

    fun navigateToContactSupport() {
        navTo(Nav.Settings.ContactSupport)
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "VM")
        private const val SHORT_RECORDING_THRESHOLD = 5_000L
    }
}
