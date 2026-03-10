package eu.darken.octi.common.debug.recording.core

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
            .map { it.shouldRecord }
            .distinctUntilChanged()
            .onEach { shouldRecord ->
                log(TAG) { "shouldRecord changed: $shouldRecord" }

                internalState.updateBlocking {
                    if (shouldRecord && !isRecording) {
                        val (sessionDir, startedAt) = findOrCreateSession(lastSession)
                        val newRecorder = Recorder()
                        newRecorder.start(sessionDir)
                        writeTriggerFile(sessionDir, startedAt)

                        copy(
                            recorder = newRecorder,
                            recordingStartedAt = startedAt,
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

    internal fun writeTriggerFile(sessionDir: File, startedAt: Long) {
        triggerFile.parentFile?.mkdirs()
        triggerFile.writeText("${sessionDir.absolutePath}\n$startedAt")
    }

    internal fun readTriggerFile(): Pair<File, Long>? {
        if (!triggerFile.exists()) return null
        val lines = try {
            triggerFile.readLines()
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to read trigger file: ${e.asLog()}" }
            return null
        }
        if (lines.size < 2) return null
        val sessionDir = File(lines[0])
        val startedAt = lines[1].toLongOrNull() ?: return null
        if (startedAt !in 1..System.currentTimeMillis()) return null
        return sessionDir to startedAt
    }

    internal fun findOrCreateSession(lastSession: LogSession? = null): Pair<File, Long> {
        val triggerData = readTriggerFile()
        if (triggerData != null) {
            val (sessionDir, startedAt) = triggerData
            val coreLog = File(sessionDir, "core.log")
            if (sessionDir.isDirectory && coreLog.exists()) {
                log(TAG, INFO) { "Resuming session: ${sessionDir.name}, startedAt=$startedAt" }
                return sessionDir to startedAt
            }
            log(TAG, WARN) { "Trigger references missing session: $sessionDir, creating new" }
        }

        val existingDir = findExistingSessionDir(lastSession)
        if (existingDir != null) {
            val startedAt = existingDir.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
            log(TAG, INFO) { "Legacy resume from scan: ${existingDir.name}" }
            return existingDir to startedAt
        }

        return createSessionDir() to System.currentTimeMillis()
    }

    internal fun findExistingSessionDir(lastSession: LogSession? = null): File? {
        val prefix = BuildConfigWrap.APPLICATION_ID
        val excludePath = lastSession?.sessionDir?.absolutePath
        for (logDir in getLogDirectories()) {
            if (!logDir.isDirectory) continue
            val candidates = logDir.listFiles()
                ?.filter { dir ->
                    dir.isDirectory
                        && dir.name.startsWith(prefix)
                        && File(dir, "core.log").exists()
                        && dir.absolutePath != excludePath
                        && !File(dir.parentFile, "${dir.name}.zip").exists()
                }
                ?.sortedByDescending { it.lastModified() }
                ?: continue
            if (candidates.isNotEmpty()) return candidates.first()
        }
        return null
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

    suspend fun requestStopRecorder(): StopResult {
        val currentState = internalState.value()
        if (!currentState.isRecording) return StopResult.NotRecording

        val sessionDir = currentState.recorder?.sessionDir ?: return StopResult.NotRecording
        val elapsed = System.currentTimeMillis() - (currentState.recordingStartedAt ?: 0L)
        if (elapsed < MIN_RECORDING_MS) return StopResult.TooShort

        stopRecorder()
        return StopResult.Stopped(LogSession(sessionDir))
    }

    sealed class StopResult {
        data object TooShort : StopResult()
        data class Stopped(val session: LogSession) : StopResult()
        data object NotRecording : StopResult()
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
        internal const val MIN_RECORDING_MS = 5_000L
    }
}
