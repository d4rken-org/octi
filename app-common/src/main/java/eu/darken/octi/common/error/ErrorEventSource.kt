package eu.darken.octi.common.error

import eu.darken.octi.common.flow.SingleEventFlow

interface ErrorEventSource {
    val errorEvents: SingleEventFlow<Throwable>
}
