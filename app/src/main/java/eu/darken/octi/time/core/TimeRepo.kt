package eu.darken.octi.time.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val ticker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(10 * 1000)
        }
    }

    val time: Flow<TimeInfo> = ticker
        .map { TimeInfo() }
        .replayingShare(scope + dispatcherProvider.IO)

    companion object {
        val TAG = logTag("Module", "Time", "Repo")
    }
}