package eu.darken.octi.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.recording.core.DebugSession
import eu.darken.octi.common.debug.recording.core.DebugSessionManager
import eu.darken.octi.common.debug.recording.core.LogSession
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.themeState
import eu.darken.octi.main.core.themeStateBlocking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class RecorderActivityVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
    private val generalSettings: GeneralSettings,
    private val sessionManager: DebugSessionManager,
) : ViewModel4(dispatcherProvider) {

    val themeState = generalSettings.themeState.stateIn(
        vmScope,
        SharingStarted.Eagerly,
        generalSettings.themeStateBlocking,
    )

    private val sessionDirPath = handle.get<String>(RecorderActivity.SESSION_DIR)!!
    private val session = LogSession(File(sessionDirPath))

    private val stater = DynamicStateFlow(TAG, vmScope) { State() }
    val state = stater.flow.shareLatest(scope = vmScope)

    val shareEvent = SingleEventFlow<Intent>()
    val finishEvent = SingleEventFlow<Unit>()

    init {
        launch {
            val recordingDuration = try {
                val attrs = java.nio.file.Files.readAttributes(
                    session.sessionDir.toPath(),
                    java.nio.file.attribute.BasicFileAttributes::class.java,
                )
                Duration.between(attrs.creationTime().toInstant(), Instant.now())
            } catch (e: Exception) {
                log(TAG) { "Failed to read session dir creation time: $e" }
                null
            }

            val files = session.files
                .map { LogFileInfo(it.name, it.length()) }
                .sortedByDescending { it.size }
            val totalSize = files.sumOf { it.size }

            stater.updateBlocking {
                copy(
                    sessionName = session.name,
                    sessionPath = session.sessionDir.absolutePath,
                    files = files,
                    fileCount = files.size,
                    totalUncompressedSize = totalSize,
                    recordingDuration = recordingDuration,
                    loading = false,
                )
            }
        }

        sessionManager.state
            .map { managerState ->
                managerState.debugSessions.firstOrNull {
                    it.session.sessionDir.absolutePath == sessionDirPath
                }
            }
            .setupCommonEventHandlers(TAG) { "debugSession" }
            .onEach { debugSession ->
                stater.updateBlocking {
                    copy(
                        isCompressing = debugSession is DebugSession.Compressing,
                        isFailed = debugSession is DebugSession.Failed,
                    )
                }
            }
            .launchInViewModel()
    }

    fun share() = launch {
        stater.updateBlocking { copy(actionInProgress = true) }
        try {
            val uri = sessionManager.compressSession(session)

            stater.updateBlocking { copy(compressedSize = session.zipFile.length()) }

            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "application/zip"
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "${BuildConfigWrap.APPLICATION_ID} DebugLog - ${BuildConfigWrap.VERSION_DESCRIPTION}",
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${BuildConfigWrap.APPLICATION_ID} DebugLog\n${BuildConfigWrap.VERSION_DESCRIPTION}",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(intent, context.getString(R.string.debug_debuglog_file_label))
            shareEvent.tryEmit(chooserIntent)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to share: ${e.asLog()}" }
            errorEvents2.tryEmit(e)
        } finally {
            stater.updateBlocking { copy(actionInProgress = false) }
        }
    }

    fun keep() = launch {
        stater.updateBlocking { copy(actionInProgress = true) }
        try {
            sessionManager.compressSession(session)
            if (session.hasZip && session.sessionDir.isDirectory) {
                session.sessionDir.deleteRecursively()
            } else if (!session.hasZip) {
                log(TAG, ERROR) { "Zip failed, not deleting session dir" }
                errorEvents2.tryEmit(
                    IllegalStateException(context.getString(R.string.support_contact_debuglog_zip_error))
                )
                return@launch
            }
            finishEvent.tryEmit(Unit)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to keep: ${e.asLog()}" }
            errorEvents2.tryEmit(e)
        } finally {
            stater.updateBlocking { copy(actionInProgress = false) }
        }
    }

    fun discard() = launch {
        stater.updateBlocking { copy(actionInProgress = true) }
        try {
            sessionManager.deleteSession(session)
            finishEvent.tryEmit(Unit)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to discard: ${e.asLog()}" }
            errorEvents2.tryEmit(e)
        } finally {
            stater.updateBlocking { copy(actionInProgress = false) }
        }
    }

    fun goPrivacyPolicy() {
        webpageTool.open(PrivacyPolicy.URL)
    }

    data class LogFileInfo(val name: String, val size: Long)

    data class State(
        val sessionName: String? = null,
        val sessionPath: String? = null,
        val files: List<LogFileInfo> = emptyList(),
        val fileCount: Int = 0,
        val totalUncompressedSize: Long = -1L,
        val compressedSize: Long = -1L,
        val recordingDuration: Duration? = null,
        val loading: Boolean = true,
        val actionInProgress: Boolean = false,
        val isCompressing: Boolean = false,
        val isFailed: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Debug", "Recorder", "VM")
    }
}
