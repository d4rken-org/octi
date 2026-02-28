package eu.darken.octi.common.error

import eu.darken.octi.common.flow.SingleEventFlow

interface ErrorEventSource2 {
    val errorEvents2: SingleEventFlow<Throwable>
}
