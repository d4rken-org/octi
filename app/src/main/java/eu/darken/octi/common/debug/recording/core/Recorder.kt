package eu.darken.octi.common.debug.recording.core

import eu.darken.octi.common.debug.logging.FileLogger
import eu.darken.octi.common.debug.logging.LogCatLogger
import eu.darken.octi.common.debug.logging.Logging
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
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
     * Uninstalling the loggers, closing the writer and clearing the published state are guarded
     * independently: whatever fails, this recorder must not stay globally installed while the
     * module reports it as stopped.
     */
    suspend fun stop(): Unit = mutex.withLock {
        try {
            fileLogger?.let { logger ->
                log(TAG, INFO) { "Stopping file-logger-tree: $logger" }
                try {
                    Logging.remove(logger)
                } finally {
                    try {
                        logger.stop()
                    } finally {
                        fileLogger = null
                        this.path = null
                        this.sessionDir = null
                    }
                }
            }
        } finally {
            logcatLogger?.let { logger ->
                log(TAG, INFO) { "Stopping LogCatLogger: $logger" }
                try {
                    Logging.remove(logger)
                } finally {
                    logcatLogger = null
                }
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder")
    }
}
