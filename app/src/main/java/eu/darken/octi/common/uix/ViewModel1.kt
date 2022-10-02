package eu.darken.octi.common.uix

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag

abstract class ViewModel1 : ViewModel() {
   internal val _tag: String = logTag("VM", javaClass.simpleName)

    init {
        log(_tag) { "Initialized" }
    }

    @CallSuper
    override fun onCleared() {
        log(_tag) { "onCleared()" }
        super.onCleared()
    }
}