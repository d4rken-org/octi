package eu.darken.octi.common.debug.recording.core

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val triggerFile = try {
        File(context.getExternalFilesDir(null), FORCE_FILE)
    } catch (e: Exception) {
        File(
            Environment.getExternalStorageDirectory(),
            "/Android/data/${BuildConfigWrap.APPLICATION_ID}/files/$FORCE_FILE"
        )
    }

    private val internalState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        val triggerFileExists = triggerFile.exists()
        State(shouldRecord = triggerFileExists)
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .onEach {
                log(TAG) { "New Recorder state: $internalState" }

                internalState.updateBlocking {
                    if (!isRecording && shouldRecord) {
                        val sessionDir = createSessionDir()
                        val newRecorder = Recorder()
                        newRecorder.start(sessionDir)
                        triggerFile.createNewFile()

                        copy(
                            recorder = newRecorder,
                            recordingStartedAt = System.currentTimeMillis(),
                        )
                    } else if (!shouldRecord && isRecording) {
                        val recorderSessionDir = recorder?.sessionDir
                            ?: return@updateBlocking this
                        val session = LogSession(recorderSessionDir)
                        recorder.stop()

                        if (triggerFile.exists() && !triggerFile.delete()) {
                            log(TAG, ERROR) { "Failed to delete trigger file" }
                        }

                        copy(
                            recorder = null,
                            lastSession = session,
                            recordingStartedAt = null,
                        )
                    } else {
                        this
                    }
                }
            }
            .launchIn(appScope)
    }

    private fun getPreferredLogDir(): File {
        val externalDir = try {
            context.getExternalFilesDir(null)?.let { File(it, "debug/logs") }
        } catch (e: Exception) {
            log(TAG, WARN) { "getExternalFilesDir failed: ${e.asLog()}" }
            null
        }

        if (externalDir != null) {
            if (externalDir.mkdirs() || externalDir.isDirectory) {
                log(TAG) { "Using external log dir: $externalDir" }
                return externalDir
            }
            log(TAG, WARN) { "External dir not writable, falling back to cache" }
        }

        return File(context.cacheDir, "debug/logs").also {
            it.mkdirs()
            log(TAG) { "Using cache log dir: $it" }
        }
    }

    private fun createSessionDir(): File {
        val sanitizedVersion = BuildConfigWrap.VERSION_NAME.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dirName = "${BuildConfigWrap.APPLICATION_ID}_${sanitizedVersion}_${System.currentTimeMillis()}"

        val baseDir = getPreferredLogDir()
        val sessionDir = File(baseDir, dirName)

        if (!sessionDir.mkdirs() && !sessionDir.isDirectory) {
            log(TAG, WARN) { "Failed to create session dir at $sessionDir, trying fallback" }
            val fallbackDir = File(File(context.cacheDir, "debug/logs"), dirName)
            fallbackDir.mkdirs()
            return fallbackDir
        }

        return sessionDir
    }

    internal fun getLogDirectories(): List<File> {
        val dirs = mutableListOf<File>()

        try {
            context.getExternalFilesDir(null)?.let {
                dirs.add(File(it, "debug/logs"))
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "getExternalFilesDir failed: ${e.asLog()}" }
        }

        dirs.add(File(context.cacheDir, "debug/logs"))

        return dirs
    }

    suspend fun startRecorder(): LogSession {
        internalState.updateBlocking {
            copy(shouldRecord = true)
        }
        val recordingState = internalState.flow.filter { it.isRecording }.first()
        val sessionDir = checkNotNull(recordingState.recorder?.sessionDir) { "Recorder started but sessionDir is null" }
        return LogSession(sessionDir)
    }

    suspend fun stopRecorder(): LogSession? {
        val currentState = internalState.value()
        val sessionDir = currentState.recorder?.sessionDir ?: return null

        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.filter { !it.isRecording }.first()
        return LogSession(sessionDir)
    }

    data class State(
        val shouldRecord: Boolean = false,
        internal val recorder: Recorder? = null,
        val lastSession: LogSession? = null,
        val recordingStartedAt: Long? = null,
    ) {
        val isRecording: Boolean
            get() = recorder != null

        val currentLogPath: File?
            get() = recorder?.path
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "force_debug_run"
    }
}
