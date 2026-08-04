package eu.darken.octi.common.debug.logging

import android.annotation.SuppressLint
import android.util.Log
import eu.darken.octi.common.error.addSuppressedSafely
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import kotlin.time.Clock


@SuppressLint("LogNotTimber")
class FileLogger(private val logFile: File) : Logging.Logger {
    private var logWriter: OutputStreamWriter? = null

    /**
     * Throws if the writer could not be initialized. Swallowing the failure here would install a
     * logger that silently writes nowhere, and the recorder would report a successful start for a
     * recording that can never produce a log file.
     */
    @SuppressLint("SetWorldReadable")
    @Synchronized
    fun start() {
        if (logWriter != null) return

        logFile.parentFile!!.mkdirs()
        // Only a log file THIS attempt created may be deleted again when the start fails: a session
        // being resumed already holds the recording the user made, and dropping it on a failed
        // restart destroys the very data they were collecting.
        val createdHere = logFile.createNewFile()
        if (createdHere) {
            Log.i(TAG, "File logger writing to " + logFile.path)
        }
        if (logFile.setReadable(true, false)) {
            Log.i(TAG, "Debug run log read permission set")
        }

        val writer = try {
            OutputStreamWriter(FileOutputStream(logFile, true))
        } catch (e: IOException) {
            Log.e(TAG, "File logger could not open $logFile", e)
            if (createdHere) logFile.delete()
            throw e
        }

        try {
            writer.write("=== BEGIN ===\n")
            writer.write("Logfile: $logFile\n")
            writer.flush()
        } catch (e: IOException) {
            Log.e(TAG, "File logger could not write to $logFile", e)
            try {
                writer.close()
            } catch (closeError: IOException) {
                e.addSuppressedSafely(closeError)
            }
            if (createdHere) logFile.delete()
            throw e
        }

        logWriter = writer
        Log.i(TAG, "File logger started.")
    }

    @Synchronized
    fun stop() {
        logWriter?.let {
            logWriter = null
            try {
                it.write("=== END ===\n")
                it.close()
            } catch (ignore: IOException) {
            }
            Log.i(TAG, "File logger stopped.")
        }
    }

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        logWriter?.let {
            try {
                it.write("${Clock.System.now()}  ${priority.shortLabel}/$tag: $message\n")
                it.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write log line.", e)
                try {
                    it.close()
                } catch (ignore: Exception) {
                }
                logWriter = null
            }
        }
    }

    override fun toString(): String = "FileLogger(file=$logFile)"

    companion object {
        private val TAG = logTag("Debug", "FileLogger")
    }
}

