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

    var path: File? = null
        private set

    suspend fun start(path: File) = mutex.withLock {
        if (fileLogger != null) return@withLock
        this.path = path
        fileLogger = FileLogger(path)
        fileLogger?.let { logger ->
            if (Logging.loggers.none { it is LogCatLogger }) {
                log(TAG, INFO) { "Adding LogCatLogger: $this" }
                LogCatLogger().apply {
                    Logging.install(this)
                    logcatLogger = this
                }
            }
            logger.start()
            Logging.install(logger)
            log(TAG, INFO) { "Now logging to file!" }
        }
    }

    suspend fun stop() = mutex.withLock {
        fileLogger?.let {
            log(TAG, INFO) { "Stopping file-logger-tree: $it" }
            Logging.remove(it)
            it.stop()
            fileLogger = null
            this.path = null
        }
        logcatLogger?.let {
            log(TAG, INFO) { "Stopping LogCatLogger: $it" }
            Logging.remove(it)
            logcatLogger = null
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "eu.darken.octi.common.debug.recording.core.Recorder")
    }

}