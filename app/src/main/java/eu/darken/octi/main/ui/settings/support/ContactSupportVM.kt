package eu.darken.octi.main.ui.settings.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.recording.core.DebugSessionManager
import eu.darken.octi.common.debug.recording.core.LogSession
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ContactSupportVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val sessionManager: DebugSessionManager,
    private val upgradeRepo: UpgradeRepo,
    @ApplicationContext private val context: Context,
) : ViewModel4(dispatcherProvider) {

    enum class Category { QUESTION, FEATURE_REQUEST, BUG_REPORT }

    data class State(
        val category: Category = Category.QUESTION,
        val description: String = "",
        val expectedBehavior: String = "",
        val isRecording: Boolean = false,
        val isSending: Boolean = false,
        val sessions: List<DebugSessionManager.SessionInfo> = emptyList(),
        val selectedSession: LogSession? = null,
        val showShortRecordingWarning: Boolean = false,
        val showRecordingConsent: Boolean = false,
    ) {
        val descriptionWordCount: Int get() = wordCount(description)
        val expectedBehaviorWordCount: Int get() = wordCount(expectedBehavior)

        val isSendEnabled: Boolean
            get() {
                if (isSending || isRecording) return false
                if (descriptionWordCount < 20) return false
                if (category == Category.BUG_REPORT && expectedBehaviorWordCount < 10) return false
                return true
            }
    }

    private val stater = DynamicStateFlow(TAG, vmScope) { State() }
    val state = stater.flow.shareLatest(scope = vmScope)

    init {
        sessionManager.state
            .setupCommonEventHandlers(TAG) { "sessionManagerState" }
            .onEach { managerState ->
                stater.updateBlocking {
                    val selectedStillExists = managerState.sessions.any {
                        it.session.sessionDir == selectedSession?.sessionDir
                    }
                    copy(
                        isRecording = managerState.isRecording,
                        sessions = managerState.sessions,
                        selectedSession = if (selectedStillExists) selectedSession else null,
                    )
                }
            }
            .launchInViewModel()
    }

    fun setCategory(category: Category) = launch {
        stater.updateBlocking { copy(category = category) }
    }

    fun setDescription(text: String) = launch {
        if (text.length <= 5000) {
            stater.updateBlocking { copy(description = text) }
        }
    }

    fun setExpectedBehavior(text: String) = launch {
        if (text.length <= 5000) {
            stater.updateBlocking { copy(expectedBehavior = text) }
        }
    }

    fun showRecordingConsent() = launch {
        stater.updateBlocking { copy(showRecordingConsent = true) }
    }

    fun dismissRecordingConsent() = launch {
        stater.updateBlocking { copy(showRecordingConsent = false) }
    }

    fun startRecording() = launch {
        stater.updateBlocking { copy(showRecordingConsent = false) }
        sessionManager.startRecording()
    }

    fun stopRecording() = launch {
        val managerState = sessionManager.state.first()
        val startedAt = managerState.recordingStartedAt
        if (startedAt != null && System.currentTimeMillis() - startedAt < DebugSessionManager.SHORT_RECORDING_THRESHOLD) {
            stater.updateBlocking { copy(showShortRecordingWarning = true) }
            return@launch
        }
        doStopRecording()
    }

    fun dismissShortRecordingWarning() = launch {
        stater.updateBlocking { copy(showShortRecordingWarning = false) }
    }

    fun forceStopRecording() = launch {
        stater.updateBlocking { copy(showShortRecordingWarning = false) }
        doStopRecording()
    }

    private suspend fun doStopRecording() {
        sessionManager.stopRecording()
    }

    fun selectSession(session: LogSession) = launch {
        stater.updateBlocking {
            val newSelection = if (selectedSession?.sessionDir == session.sessionDir) null else session
            copy(selectedSession = newSelection)
        }
    }

    fun deleteSession(session: LogSession) = launch {
        sessionManager.deleteSession(session)
    }

    fun sendEmail() = launch {
        stater.updateBlocking { copy(isSending = true) }
        try {
            val currentState = stater.value()

            val zipUri = currentState.selectedSession?.let { session ->
                try {
                    sessionManager.compressSession(session)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to zip session: ${e.asLog()}" }
                    errorEvents2.tryEmit(
                        IllegalStateException(context.getString(R.string.support_contact_debuglog_zip_error))
                    )
                    return@launch
                }
            }

            val isPro = try {
                upgradeRepo.upgradeInfo.first().isPro
            } catch (_: Exception) {
                false
            }

            val subject = buildSubject(currentState)
            val body = buildBody(currentState, isPro)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (zipUri != null) "application/zip" else "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@darken.eu"))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                if (zipUri != null) {
                    putExtra(Intent.EXTRA_STREAM, zipUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("", zipUri)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                val chooser = Intent.createChooser(intent, context.getString(R.string.support_contact_send_action))
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (_: ActivityNotFoundException) {
                errorEvents2.tryEmit(
                    IllegalStateException(context.getString(R.string.support_contact_no_email_app))
                )
            }
        } finally {
            stater.updateBlocking { copy(isSending = false) }
        }
    }

    private fun buildSubject(state: State): String {
        val categoryTag = when (state.category) {
            Category.QUESTION -> "QUESTION"
            Category.FEATURE_REQUEST -> "FEATURE"
            Category.BUG_REPORT -> "BUG"
        }
        val descWords = state.description.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val shortDesc = descWords.take(8).joinToString(" ")
        val raw = "[OCTI][$categoryTag] $shortDesc"
        return raw.replace("\n", " ").replace("\\s+".toRegex(), " ").take(120)
    }

    private fun buildBody(state: State, isPro: Boolean): String = buildString {
        appendLine(state.description.trim())

        if (state.category == Category.BUG_REPORT && state.expectedBehavior.isNotBlank()) {
            appendLine()
            appendLine("--- Expected Behavior ---")
            appendLine(state.expectedBehavior.trim())
        }

        val proIndicator = if (isPro) "★" else "☆"
        appendLine()
        appendLine("--- Device Info ---")
        appendLine("App: $proIndicator ${BuildConfigWrap.VERSION_DESCRIPTION}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "Contact", "VM")

        fun wordCount(text: String): Int {
            return text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        }
    }
}
