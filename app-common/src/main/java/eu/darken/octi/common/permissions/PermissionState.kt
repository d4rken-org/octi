package eu.darken.octi.common.permissions

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable snapshot of [Permission] grant state, refreshed on process resume and on
 * explicit [recheck]. Consumers combine [state] into their own flows so they re-evaluate
 * when a permission flips (e.g. the user granted POST_NOTIFICATIONS via the dashboard chip
 * or from system settings).
 */
@Singleton
class PermissionState @Inject constructor(
    @ApplicationContext private val context: Context,
) : DefaultLifecycleObserver {

    private val _state = MutableStateFlow(computeCurrent())
    val state: StateFlow<Map<Permission, Boolean>> = _state.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        recheck()
    }

    fun recheck() {
        val next = computeCurrent()
        if (_state.value != next) {
            log(TAG, VERBOSE) { "recheck(): $next" }
        }
        _state.value = next
    }

    private fun computeCurrent(): Map<Permission, Boolean> =
        Permission.values().associateWith { it.isGranted(context) }

    companion object {
        private val TAG = logTag("Permission", "State")
    }
}
