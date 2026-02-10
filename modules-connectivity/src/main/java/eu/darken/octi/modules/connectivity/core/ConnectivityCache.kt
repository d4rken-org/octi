package eu.darken.octi.modules.connectivity.core

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.connectivity.ConnectivityModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    connectivitySerializer: ConnectivitySerializer,
    moshi: Moshi,
) : BaseModuleCache<ConnectivityInfo>(
    moduleId = ConnectivityModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = connectivitySerializer,
    moshi = moshi,
) {

    companion object {
        val TAG = logTag("Module", "Connectivity", "Cache")
    }
}
