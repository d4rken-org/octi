package eu.darken.octi.modules.wifi.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.wifi.WifiModule
import kotlinx.coroutines.CoroutineScope
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiSync @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    syncSettings: eu.darken.octi.sync.core.SyncSettings,
    syncManager: eu.darken.octi.sync.core.SyncManager,
    private val moshi: Moshi,
) : eu.darken.octi.module.core.BaseModuleSync<WifiInfo>(
    tag = TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
) {

    private val adapter by lazy { moshi.adapter<WifiInfo>() }

    override val moduleId: ModuleId = WifiModule.MODULE_ID

    override fun onSerialize(item: WifiInfo): ByteString = adapter.toByteString(item)

    override fun onDeserialize(raw: ByteString): WifiInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Wifi", "Sync")
    }
}