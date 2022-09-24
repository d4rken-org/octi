package eu.darken.octi.common.uix

import androidx.navigation.NavDirections
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.error.ErrorEventSource
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.navigation.NavEventSource
import eu.darken.octi.common.navigation.navVia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn


abstract class ViewModel3(
    dispatcherProvider: DispatcherProvider,
) : ViewModel2(dispatcherProvider), NavEventSource, ErrorEventSource {

    override val navEvents = SingleLiveEvent<NavDirections?>()
    override val errorEvents = SingleLiveEvent<Throwable>()

    init {

    }

    override fun <T> Flow<T>.launchInViewModel() = this
        .setupCommonEventHandlers(TAG) { "launchInViewModel()" }
        .launchIn(vmScope)

    fun NavDirections.navigate() {
        navVia(navEvents)
    }

    fun popNavStack() {
        navEvents.postValue(null)
    }
}