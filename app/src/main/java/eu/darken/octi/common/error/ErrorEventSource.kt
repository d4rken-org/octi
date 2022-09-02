package eu.darken.octi.common.error

import eu.darken.octi.common.livedata.SingleLiveEvent

interface ErrorEventSource {
    val errorEvents: SingleLiveEvent<Throwable>
}