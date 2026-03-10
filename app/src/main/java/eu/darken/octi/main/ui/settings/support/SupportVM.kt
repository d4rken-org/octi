package eu.darken.octi.main.ui.settings.support

import android.content.Context
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.recording.core.DebugSession
import eu.darken.octi.common.debug.recording.core.DebugSessionManager
import eu.darken.octi.common.debug.recording.core.LogSession
import eu.darken.octi.common.debug.recording.core.RecorderModule
import eu.darken.octi.common.debug.recording.ui.RecorderActivity
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import kotlinx.coroutines.flow.onEach
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val sessionManager: DebugSessionManager,
    private val webpageTool: WebpageTool,
    @ApplicationContext private val context: Context,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isRecording: Boolean = false,
        val currentLogPath: File? = null,
        val debugSessions: List<DebugSession> = emptyList(),
        val showShortRecordingWarning: Boolean = false,
    ) {
        val sessionCount: Int get() = debugSessions.size
        val totalLogSize: Long get() = debugSessions.sumOf { it.size }
    }

    private val stater = DynamicStateFlow(TAG, vmScope) { State() }
    val state = stater.flow.asStateFlow()

    val launchRecorderEvent = SingleEventFlow<Intent>()

    init {
        sessionManager.state
            .setupCommonEventHandlers(TAG) { "sessionManagerState" }
            .onEach { managerState ->
                stater.updateBlocking {
                    copy(
                        isRecording = managerState.isRecording,
                        currentLogPath = managerState.currentLogPath,
                        debugSessions = managerState.debugSessions,
                    )
                }
            }
            .launchInViewModel()
    }

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun openPrivacyPolicy() {
        webpageTool.open(PrivacyPolicy.URL)
    }

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        sessionManager.startRecording()
    }

    fun stopDebugLog() = launch {
        log(TAG) { "stopDebugLog()" }
        when (val result = sessionManager.requestStopRecording()) {
            is RecorderModule.StopResult.TooShort -> {
                stater.updateBlocking { copy(showShortRecordingWarning = true) }
            }
            is RecorderModule.StopResult.Stopped -> showRecorder(result.session)
            is RecorderModule.StopResult.NotRecording -> {}
        }
    }

    fun dismissShortRecordingWarning() = launch {
        stater.updateBlocking { copy(showShortRecordingWarning = false) }
    }

    fun forceStopDebugLog() = launch {
        stater.updateBlocking { copy(showShortRecordingWarning = false) }
        val session = sessionManager.forceStopRecording() ?: return@launch
        showRecorder(session)
    }

    private fun showRecorder(session: LogSession) {
        val intent = RecorderActivity.getLaunchIntent(context, session.sessionDir.absolutePath).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchRecorderEvent.tryEmit(intent)
    }

    fun deleteSession(session: LogSession) = launch {
        log(TAG) { "deleteSession(${session.name})" }
        sessionManager.deleteSession(session)
    }

    fun openSession(session: LogSession) {
        log(TAG) { "openSession(${session.name})" }
        val intent = RecorderActivity.getLaunchIntent(context, session.sessionDir.absolutePath).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchRecorderEvent.tryEmit(intent)
    }

    fun deleteAllLogs() = launch {
        log(TAG) { "deleteAllLogs()" }
        sessionManager.deleteAllSessions()
    }

    fun navigateToContactSupport() {
        navTo(Nav.Settings.ContactSupport)
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "VM")
    }
}
