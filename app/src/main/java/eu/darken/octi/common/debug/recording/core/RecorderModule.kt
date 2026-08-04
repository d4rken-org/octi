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
import eu.darken.octi.common.error.addSuppressedSafely
import eu.darken.octi.common.flow.DynamicStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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

    // Test seams for the two clocks the recording heuristics use: the durations are
    // wall-clock/monotonic, so virtual time cannot drive them.
    internal var wallClock: () -> Long = { Clock.System.now().toEpochMilliseconds() }
    internal var monotonicClock: () -> Long = android.os.SystemClock::elapsedRealtime

    // Test seam for the recorder itself: a failing stop() has no reachable production trigger, so
    // the guarantee that a failed stop still completes cannot be driven without substituting one.
    internal var recorderFactory: () -> Recorder = { Recorder() }

    // Serializes the public request surface: without it two concurrent callers can observe each
    // other's start failure, or a stale one, instead of their own attempt's outcome.
    private val requestMutex = Mutex()

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
                        // Keyed on the trigger file, NOT on directory reuse: leftover unzipped
                        // session dirs from a failed compression are picked up again by LIVE
                        // recordings, so their stale mtime must not decide a fresh recording's
                        // duration. Only a trigger that already existed before this start sequence
                        // means the process died mid-recording and we are resuming it — the one
                        // case with no usable monotonic base.
                        val isProcessResume = triggerFile.exists()

                        // Rollback bookkeeping: a start that throws half-way must leave nothing
                        // behind that this module can no longer reach - neither a running recorder
                        // with globally installed loggers, nor a trigger file that re-runs the
                        // failing start on every process launch.
                        var pendingRecorder: Recorder? = null
                        var createdSessionDir: File? = null
                        var triggerWriteStarted = false

                        try {
                            val target = findOrCreateSession(lastSession)
                            if (target.isNewDir) createdSessionDir = target.sessionDir

                            // Bound BEFORE start() so a throw from inside start() still has
                            // something to roll back.
                            val newRecorder = recorderFactory().also { pendingRecorder = it }
                            newRecorder.start(target.sessionDir)

                            triggerWriteStarted = true
                            writeTriggerFile(target.sessionDir, target.startedAt)

                            copy(
                                recorder = newRecorder,
                                recordingStartedAt = target.startedAt,
                                // A fresh start that reuses an old session dir gets BOTH bases: the
                                // scanned wall stamp above and this monotonic one. The heuristic prefers
                                // the monotonic base, so the stale mtime never decides a live recording.
                                recordingStartedAtMonotonic = if (isProcessResume) null else monotonicClock(),
                                startFailure = null,
                            )
                        } catch (e: Throwable) {
                            withContext(NonCancellable) {
                                pendingRecorder?.let { orphan ->
                                    try {
                                        orphan.stop()
                                    } catch (stopError: Throwable) {
                                        e.addSuppressedSafely(stopError)
                                    }
                                }
                                // Only a trigger this attempt created: one that was already there
                                // belongs to the session we were resuming.
                                if (triggerWriteStarted && !isProcessResume) {
                                    try {
                                        if (triggerFile.exists() && !triggerFile.delete()) {
                                            log(TAG, ERROR) { "Failed to delete trigger file after failed start" }
                                        }
                                    } catch (cleanupError: Throwable) {
                                        e.addSuppressedSafely(cleanupError)
                                    }
                                }
                                // Resumed session dirs are the user's existing log and are kept.
                                createdSessionDir?.let { dir ->
                                    try {
                                        if (dir.isDirectory && !dir.deleteRecursively()) {
                                            log(TAG, ERROR) { "Failed to delete session dir after failed start: $dir" }
                                        }
                                    } catch (cleanupError: Throwable) {
                                        e.addSuppressedSafely(cleanupError)
                                    }
                                }
                            }

                            log(TAG, ERROR) { "Failed to start recording: ${e.asLog()}" }

                            // Our own scope being cancelled must propagate; a CancellationException
                            // that merely came out of the start sequence must not, it would kill
                            // this collector and leave every future request unanswered.
                            currentCoroutineContext().ensureActive()

                            // shouldRecord is reset so the edge-triggered collector is re-armed for
                            // a retry, and the failure is committed so a waiting startRecorder()
                            // observes it instead of waiting forever.
                            copy(
                                shouldRecord = false,
                                startFailure = asStartFailure(e),
                                recorder = null,
                                recordingStartedAt = null,
                                recordingStartedAtMonotonic = null,
                            )
                        }
                    } else if (!shouldRecord && isRecording) {
                        val recorderSessionDir = recorder?.sessionDir
                            ?: return@updateBlocking this
                        val session = LogSession(recorderSessionDir)

                        // Both steps are best-effort: a stop that cannot complete is logged, but the
                        // cleared state is committed regardless so a waiting stopRecorder() returns.
                        try {
                            recorder.stop()
                        } catch (e: Throwable) {
                            currentCoroutineContext().ensureActive()
                            log(TAG, ERROR) { "Failed to stop the recorder: ${e.asLog()}" }
                        }

                        try {
                            if (triggerFile.exists() && !triggerFile.delete()) {
                                log(TAG, ERROR) { "Failed to delete trigger file" }
                            }
                        } catch (e: Throwable) {
                            currentCoroutineContext().ensureActive()
                            log(TAG, ERROR) { "Failed to delete trigger file: ${e.asLog()}" }
                        }

                        copy(
                            recorder = null,
                            lastSession = session,
                            recordingStartedAt = null,
                            recordingStartedAtMonotonic = null,
                        )
                    } else {
                        this
                    }
                }
            }
            .launchIn(appScope)
    }

    /**
     * A start failure that arrived as a [CancellationException] while this module's own scope was
     * still alive - a bounded read inside the start sequence timing out, for example. Stored and
     * rethrown unchanged it makes every caller treat it as their OWN cancellation: the launch that
     * requested the start ends "normally" and the error handler that would have surfaced the
     * failure never runs.
     */
    class RecorderStartFailedException(cause: Throwable) : IllegalStateException("Failed to start recording", cause)

    /**
     * Runs AFTER [ensureActive] has confirmed the scope is alive, so a cancellation seen here can
     * only be a foreign one. Everything else is committed unchanged.
     */
    private fun asStartFailure(error: Throwable): Throwable = when (error) {
        is CancellationException -> RecorderStartFailedException(error)
        else -> error
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

    /**
     * [created] is what the rollback of a failed start keys off, so it reports what actually
     * happened on disk, never the intent: a directory that was already there belongs to an earlier
     * session and deleting it would take that session's logs with it.
     */
    private data class NewSessionDir(
        val dir: File,
        val created: Boolean,
    )

    private fun createSessionDir(): NewSessionDir {
        val sanitizedVersion = BuildConfigWrap.VERSION_NAME.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dirName = "${BuildConfigWrap.APPLICATION_ID}_${sanitizedVersion}_${wallClock()}"

        val baseDir = getPreferredLogDir()
        createUniqueDir(baseDir, dirName)?.let { return NewSessionDir(it, created = true) }

        log(TAG, WARN) { "Failed to create session dir for $dirName in $baseDir, trying fallback" }
        val fallbackBase = File(context.cacheDir, "debug/logs")
        createUniqueDir(fallbackBase, dirName)?.let { return NewSessionDir(it, created = true) }

        // Nothing was created, so nothing may be rolled back either; the start attempt fails on the
        // unusable directory further down instead.
        log(TAG, ERROR) { "Failed to create a session dir for $dirName in $fallbackBase" }
        return NewSessionDir(File(fallbackBase, dirName), created = false)
    }

    /**
     * mkdirs() reports false for a directory that already exists, so the candidate that wins is
     * always one this call brought into existence. A name collision (two starts within the same
     * millisecond, or a leftover directory of that name) walks to a suffixed name rather than
     * adopting - and later deleting - somebody else's session.
     */
    private fun createUniqueDir(baseDir: File, dirName: String): File? {
        for (attempt in 0..NAME_COLLISION_LIMIT) {
            val candidate = File(baseDir, if (attempt == 0) dirName else "${dirName}_$attempt")
            if (candidate.mkdirs()) return candidate
            // Not a collision but an unusable base directory: further suffixes cannot help.
            if (!candidate.exists()) return null
        }
        return null
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
        // The trigger file stores wall-clock timestamps: it has to survive reboots, which monotonic
        // time does not.
        if (startedAt !in 1..wallClock()) return null
        return sessionDir to startedAt
    }

    /**
     * [isNewDir] separates a directory this call actually created from one it resumed into or found
     * already present: only the former may be deleted again when the start sequence fails.
     */
    internal data class SessionTarget(
        val sessionDir: File,
        val startedAt: Long,
        val isNewDir: Boolean,
    )

    internal fun findOrCreateSession(lastSession: LogSession? = null): SessionTarget {
        val triggerData = readTriggerFile()
        if (triggerData != null) {
            val (sessionDir, startedAt) = triggerData
            val coreLog = File(sessionDir, "core.log")
            if (sessionDir.isDirectory && coreLog.exists()) {
                log(TAG, INFO) { "Resuming session: ${sessionDir.name}, startedAt=$startedAt" }
                return SessionTarget(sessionDir, startedAt, isNewDir = false)
            }
            log(TAG, WARN) { "Trigger references missing session: $sessionDir, creating new" }
        }

        val existingDir = findExistingSessionDir(lastSession)
        if (existingDir != null) {
            val startedAt = existingDir.lastModified().takeIf { it > 0 } ?: wallClock()
            log(TAG, INFO) { "Legacy resume from scan: ${existingDir.name}" }
            return SessionTarget(existingDir, startedAt, isNewDir = false)
        }

        val fresh = createSessionDir()
        return SessionTarget(fresh.dir, wallClock(), isNewDir = fresh.created)
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

    suspend fun startRecorder(): LogSession = requestMutex.withLock {
        // Clearing the failure is what keeps a STALE one from satisfying this attempt's await.
        internalState.updateBlocking {
            copy(shouldRecord = true, startFailure = null)
        }

        val settled = internalState.flow.first { it.isRecording || it.startFailure != null }
        settled.startFailure?.let { throw it }

        val sessionDir = checkNotNull(settled.recorder?.sessionDir) { "Recorder started but sessionDir is null" }
        LogSession(sessionDir)
    }

    suspend fun stopRecorder(): LogSession? = requestMutex.withLock { stopRecorderLocked() }

    /**
     * Requires [requestMutex]. Reading the current session, publishing the request and observing
     * the stop have to happen without another caller in between, or two callers report having
     * stopped the same session - or a session that is still running.
     */
    private suspend fun stopRecorderLocked(): LogSession? {
        val currentState = internalState.value()
        val sessionDir = currentState.recorder?.sessionDir ?: return null

        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.first { !it.isRecording }
        return LogSession(sessionDir)
    }

    suspend fun requestStopRecorder(): StopResult = requestMutex.withLock {
        val currentState = internalState.value()
        if (!currentState.isRecording) return@withLock StopResult.NotRecording

        val elapsed = currentState.recordingStartedAtMonotonic
            ?.let { monotonicClock() - it }             // live session: immune to wall-clock adjustments
            ?: (wallClock() - (currentState.recordingStartedAt ?: 0L))  // resumed: persisted/derived wall start only
        // Negative = wall clock moved backward across a resume; fail open (no warning) rather than
        // trap the user in TooShort.
        if (elapsed in 0 until MIN_RECORDING.inWholeMilliseconds) return@withLock StopResult.TooShort

        // The delegated stop decides what was stopped: reporting the session read above would claim
        // a stop that a concurrent caller had already performed on a different one.
        val stopped = stopRecorderLocked() ?: return@withLock StopResult.NotRecording
        StopResult.Stopped(stopped)
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
        // Monotonic base for the duration heuristic, null when there is none: a session resumed
        // after process death has only the persisted wall-clock start, and a monotonic value from a
        // previous process or boot is meaningless. Nullable rather than 0L — 0 is a legal
        // elapsedRealtime near boot.
        val recordingStartedAtMonotonic: Long? = null,
        /**
         * The exception that ended the last start attempt, cleared when a new one is requested.
         * Identity is per-attempt on purpose: the state flow is distinctUntilChanged, so a failure
         * has to produce a value distinct from its predecessor or a waiting [startRecorder] never
         * wakes up. Throwable's reference equality provides that.
         */
        val startFailure: Throwable? = null,
    ) {
        val isRecording: Boolean
            get() = recorder != null

        val currentLogPath: File?
            get() = recorder?.path
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "force_debug_run"

        /**
         * Suffixes tried when the timestamped session dir name is already taken. A handful is
         * plenty: the name is millisecond-stamped, so a collision means a leftover directory or a
         * second start in the same millisecond, not a long run of them.
         */
        private const val NAME_COLLISION_LIMIT = 8

        /**
         * Duration heuristic for "did you forget to reproduce the issue?". A recording stopped
         * this quickly usually contains nothing but the recorder starting and stopping, which
         * costs a support round-trip to re-request.
         *
         * It stays a prompt because short recordings can be perfectly valid: a crash is logged
         * and flushed immediately, so the reproduction is already on disk. The
         * [StopResult.TooShort] consumers (the Support and ContactSupport screens) turn it into
         * their short-recording warning, and its "stop anyway" answer goes through the direct
         * force-stop path, which has no duration check.
         */
        internal val MIN_RECORDING = 10.seconds
    }
}
