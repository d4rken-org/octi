package eu.darken.octi.common.flow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlin.time.Duration


fun <T> Flow<T>.throttleLatest(window: Duration): Flow<T> = this
    .conflate()
    .transform {
        emit(it)
        delay(window)
    }
