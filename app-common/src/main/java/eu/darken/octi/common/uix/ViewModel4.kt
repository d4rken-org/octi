package eu.darken.octi.common.uix

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.error.ErrorEventSource
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.navigation.NavEvent
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.navigation.NavigationEventSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn

abstract class ViewModel4(
    dispatcherProvider: DispatcherProvider,
) : ViewModel2(dispatcherProvider), NavigationEventSource, ErrorEventSource {

    override val navEvents = SingleEventFlow<NavEvent>()
    override val errorEvents = SingleEventFlow<Throwable>()

    init {
        launchErrorHandler = CoroutineExceptionHandler { _, ex ->
            log(_tag) { "Error during launch: ${ex.asLog()}" }
            errorEvents.emitBlocking(ex)
        }
    }

    override fun <T> Flow<T>.launchInViewModel() = this
        .setupCommonEventHandlers(_tag) { "launchInViewModel()" }
        .launchIn(vmScope)

    fun navTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false,
    ) {
        log(_tag) { "navTo($destination)" }
        navEvents.tryEmit(NavEvent.GoTo(destination, popUpTo, inclusive))
    }

    fun navUp() {
        log(_tag) { "navUp()" }
        navEvents.tryEmit(NavEvent.Up)
    }

    fun popTo(destination: NavigationDestination, inclusive: Boolean = false) {
        log(_tag) { "popTo($destination, inclusive=$inclusive)" }
        navEvents.tryEmit(NavEvent.PopTo(destination, inclusive))
    }
}
