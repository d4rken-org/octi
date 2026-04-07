package eu.darken.octi.common

import eu.darken.octi.common.debug.logging.Logging
import kotlin.time.Clock

class JUnitLogger(private val minLogLevel: Logging.Priority = Logging.Priority.VERBOSE) : Logging.Logger {

    override fun isLoggable(priority: Logging.Priority): Boolean = priority.intValue >= minLogLevel.intValue

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        println("${Clock.System.now()} ${priority.shortLabel}/$tag: $message")
    }

}
