package eu.darken.octi.common.debug.recording.core

import android.net.Uri
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSessionManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val debugLogZipper: DebugLogZipper,
    private val generalSettings: GeneralSettings,
) {

    data class SessionInfo(
        val session: LogSession,
        val size: Long,
        val lastModified: Long,
    )

    data class State(
        val isRecording: Boolean = false,
        val recordingStartedAt: Long? = null,
        val currentLogPath: File? = null,
        val sessions: List<SessionInfo> = emptyList(),
        val debugSessions: List<DebugSession> = emptyList(),
    ) {
        val sessionCount: Int get() = debugSessions.size
        val totalSessionSize: Long get() = debugSessions.sumOf { it.size }
    }

    private val compressionMutex = Mutex()
    private val compressionJobs = mutableMapOf<String, Deferred<Uri>>()

    private val refreshTrigger = MutableStateFlow(0)

    val state: Flow<State> = combine(
        refreshTrigger,
        recorderModule.state,
    ) { _, recState ->
        withContext(dispatcherProvider.IO) {
            val sessions = scanSessions(recState)
            val compressingPaths = compressionMutex.withLock { compressionJobs.keys.toSet() }
            val debugSessions = buildDebugSessions(recState, sessions, compressingPaths)
            State(
                isRecording = recState.isRecording,
                recordingStartedAt = recState.recordingStartedAt,
                currentLogPath = recState.currentLogPath,
                sessions = sessions,
                debugSessions = debugSessions,
            )
        }
    }
        .setupCommonEventHandlers(TAG) { "state" }
        .shareLatest(scope = appScope)

    init {
        appScope.launch(dispatcherProvider.IO) {
            cleanupOrphanedTempZips()
            performLegacyCleanup()
        }
    }

    private fun cleanupOrphanedTempZips() {
        for (logDir in recorderModule.getLogDirectories()) {
            if (!logDir.isDirectory) continue
            logDir.listFiles()?.filter { it.name.endsWith(".zip.tmp") }?.forEach { tmpFile ->
                tmpFile.delete()
                log(TAG) { "Cleaned up orphaned temp zip: ${tmpFile.name}" }
            }
        }
    }

    private fun invalidate() {
        refreshTrigger.update { it + 1 }
    }

    suspend fun startRecording(): LogSession {
        return recorderModule.startRecorder()
    }

    suspend fun requestStopRecording(): RecorderModule.StopResult {
        val result = recorderModule.requestStopRecorder()
        if (result is RecorderModule.StopResult.Stopped) {
            launchCompression(result.session)
            invalidate()
        }
        return result
    }

    suspend fun forceStopRecording(): LogSession? {
        val session = recorderModule.stopRecorder() ?: return null
        launchCompression(session)
        invalidate()
        return session
    }

    private suspend fun ensureCompressed(session: LogSession): Uri {
        if (session.hasZip) {
            return debugLogZipper.getUriForZip(session)
        }

        val dirPath = session.sessionDir.absolutePath
        val deferred = compressionMutex.withLock {
            compressionJobs.getOrPut(dirPath) {
                appScope.async(dispatcherProvider.IO) {
                    try {
                        log(TAG) { "Compressing ${session.name}" }
                        val uri = debugLogZipper.zipAndGetUri(session)
                        log(TAG, INFO) { "Compression complete for ${session.name}" }
                        uri
                    } finally {
                        compressionMutex.withLock { compressionJobs.remove(dirPath) }
                        invalidate()
                    }
                }
            }
        }
        invalidate()
        return deferred.await()
    }

    private fun launchCompression(session: LogSession) {
        appScope.launch {
            try {
                ensureCompressed(session)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Auto-compression failed for ${session.name}: ${e.asLog()}" }
            }
        }
    }

    suspend fun deleteSession(session: LogSession) {
        val dirPath = session.sessionDir.absolutePath
        compressionMutex.withLock { compressionJobs.remove(dirPath)?.cancel() }
        withContext(dispatcherProvider.IO) {
            log(TAG) { "deleteSession(${session.name})" }
            if (session.sessionDir.isDirectory) {
                session.sessionDir.deleteRecursively()
            }
            if (session.hasZip) {
                session.zipFile.delete()
            }
        }
        invalidate()
    }

    suspend fun deleteAllSessions() {
        withContext(dispatcherProvider.IO) {
            log(TAG) { "deleteAllSessions()" }
            val recState = recorderModule.state.first()
            scanSessions(recState).forEach { info ->
                val dirPath = info.session.sessionDir.absolutePath
                compressionMutex.withLock { compressionJobs.remove(dirPath)?.cancel() }
                if (info.session.sessionDir.isDirectory) info.session.sessionDir.deleteRecursively()
                if (info.session.hasZip) info.session.zipFile.delete()
            }
        }
        invalidate()
    }

    suspend fun compressSession(session: LogSession): Uri {
        return ensureCompressed(session)
    }

    fun getZipUri(session: LogSession): Uri {
        return debugLogZipper.getUriForZip(session)
    }

    private fun buildDebugSessions(
        recState: RecorderModule.State,
        sessions: List<SessionInfo>,
        compressingPaths: Set<String>,
    ): List<DebugSession> {
        val result = mutableListOf<DebugSession>()

        // Active recording as first item
        if (recState.isRecording) {
            val recorderDir = recState.recorder?.sessionDir
            if (recorderDir != null) {
                val session = LogSession(recorderDir)
                val dirSize = session.files.sumOf { it.length() }
                result.add(
                    DebugSession.Recording(
                        session = session,
                        size = dirSize,
                        lastModified = System.currentTimeMillis(),
                        startedAt = recState.recordingStartedAt ?: System.currentTimeMillis(),
                    )
                )
            }
        }

        // Classify each scanned session
        for (info in sessions) {
            val dirPath = info.session.sessionDir.absolutePath
            val debugSession = when {
                compressingPaths.contains(dirPath) -> DebugSession.Compressing(
                    session = info.session,
                    size = info.size,
                    lastModified = info.lastModified,
                )

                info.session.hasZip -> DebugSession.Ready(
                    session = info.session,
                    size = info.size,
                    lastModified = info.lastModified,
                )

                else -> DebugSession.Failed(
                    session = info.session,
                    size = info.size,
                    lastModified = info.lastModified,
                )
            }
            result.add(debugSession)
        }

        return result
    }

    private fun scanSessions(recState: RecorderModule.State): List<SessionInfo> {
        val activeSessionDir = recState.recorder?.sessionDir
        val sessions = mutableMapOf<String, LogSession>()

        for (logDir in recorderModule.getLogDirectories()) {
            if (!logDir.isDirectory) continue

            // Find session directories
            logDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                if (dir.absolutePath == activeSessionDir?.absolutePath) return@forEach

                val key = dir.name
                if (!sessions.containsKey(key)) {
                    sessions[key] = LogSession(dir)
                }
            }

            // Find orphan zips (no corresponding directory)
            logDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".zip") && !it.name.endsWith(".zip.tmp") }
                ?.forEach { zipFile ->
                    val key = zipFile.nameWithoutExtension
                    if (!sessions.containsKey(key)) {
                        val virtualDir = File(logDir, key)
                        sessions[key] = LogSession(virtualDir)
                    }
                }
        }

        return sessions.values.map { session ->
            val dirSize = session.files.sumOf { it.length() }
            val zipSize = if (session.hasZip) session.zipFile.length() else 0L
            val lastModified = session.sessionDir.lastModified().takeIf { it > 0 }
                ?: session.files.maxOfOrNull { it.lastModified() }
                ?: session.zipFile.lastModified().takeIf { it > 0 }
                ?: 0L
            SessionInfo(session = session, size = dirSize + zipSize, lastModified = lastModified)
        }.sortedByDescending { it.lastModified }
    }

    private suspend fun performLegacyCleanup() {
        if (generalSettings.isLegacyLogCleanupDone.value()) {
            log(TAG) { "Legacy log cleanup already done" }
            return
        }

        log(TAG, INFO) { "Performing one-time legacy log cleanup" }
        val pattern = Regex("${Regex.escape(BuildConfigWrap.APPLICATION_ID)}_logfile_\\d+\\.log(\\.zip)?")

        for (logDir in recorderModule.getLogDirectories()) {
            if (!logDir.isDirectory) continue
            logDir.listFiles()?.filter { it.isFile && pattern.matches(it.name) }?.forEach { file ->
                log(TAG) { "Deleting legacy log file: ${file.name}" }
                file.delete()
            }
        }

        generalSettings.isLegacyLogCleanupDone.value(true)
        log(TAG, INFO) { "Legacy log cleanup complete" }
    }

    companion object {
        internal val TAG = logTag("Debug", "Session", "Manager")
    }
}
