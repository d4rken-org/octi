package eu.darken.octi.common.debug

import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag

object Bugs {
    var isDebug: Boolean = BuildConfigWrap.DEBUG
    var reporter: AutomaticBugReporter? = null
    fun report(exception: Exception) {
        log(TAG, VERBOSE) { "Reporting $exception" }

        reporter?.notify(exception) ?: run {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
        }
    }

    private val TAG = logTag("Debug", "Bugs")
}