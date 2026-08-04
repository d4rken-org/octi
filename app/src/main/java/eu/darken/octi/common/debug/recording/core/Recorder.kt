package eu.darken.octi.common.debug.recording.core

import eu.darken.octi.common.debug.logging.FileLogger
import eu.darken.octi.common.debug.logging.LogCatLogger
import eu.darken.octi.common.debug.logging.Logging
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.error.addSuppressedSafely
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

class Recorder @Inject constructor() {
    private val mutex = Mutex()
    private var fileLogger: FileLogger? = null
    private var logcatLogger: LogCatLogger? = null

    val isRecording: Boolean
        get() = path != null

    var sessionDir: File? = null
        private set

    var path: File? = null
        private set

    /**
     * Nothing is published and no logger is installed until the writer is live: a failing
     * [FileLogger.start] would otherwise leave this recorder claiming to record into a file that
     * receives nothing.
     */
    suspend fun start(sessionDir: File): Unit = mutex.withLock {
        if (fileLogger != null) return@withLock
        sessionDir.mkdirs()
        val logFile = File(sessionDir, "core.log")

        val logger = FileLogger(logFile)
        logger.start()

        this.sessionDir = sessionDir
        this.path = logFile
        fileLogger = logger

        if (Logging.loggers.none { it is LogCatLogger }) {
            log(TAG, INFO) { "Adding LogCatLogger: $this" }
            LogCatLogger().apply {
                Logging.install(this)
                logcatLogger = this
            }
        }
        Logging.install(logger)
        log(TAG, INFO) { "Now logging to file in $sessionDir" }
    }

    /**
     * Every teardown step runs, no matter what the ones before it did: the loggers leave the
     * registry BEFORE any diagnostic that they would still receive themselves, the published state
     * is cleared in the outermost finally, and only then is the first failure rethrown with the
     * later ones suppressed onto it. This recorder must never stay globally installed while the
     * module reports it as stopped.
     */
    suspend fun stop(): Unit = mutex.withLock {
        val file = fileLogger
        val logcat = logcatLogger
        var failure: Throwable? = null

        fun step(block: () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                val previous = failure
                if (previous == null) failure = e else previous.addSuppressedSafely(e)
            }
        }

        try {
            if (file != null) {
                step { Logging.remove(file) }
                step { log(TAG, INFO) { "Stopped file-logger-tree: $file" } }
                step { file.stop() }
            }
            if (logcat != null) {
                step { Logging.remove(logcat) }
                step { log(TAG, INFO) { "Stopped LogCatLogger: $logcat" } }
            }
        } finally {
            fileLogger = null
            logcatLogger = null
            this.path = null
            this.sessionDir = null
        }

        val settled = failure
        if (settled != null) throw settled
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder")
    }
}
