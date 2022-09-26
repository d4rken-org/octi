package eu.darken.octi.modules.power.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.BaseModuleSync
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.power.PowerModule
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.CoroutineScope
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerSync @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    syncSettings: SyncSettings,
    syncManager: SyncManager,
    private val moshi: Moshi,
) : BaseModuleSync<PowerInfo>(
    tag = TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
) {

    private val adapter by lazy { moshi.adapter<PowerInfo>() }

    override val moduleId: ModuleId = PowerModule.MODULE_ID

    override fun onSerialize(item: PowerInfo): ByteString = adapter.toByteString(item)

    override fun onDeserialize(raw: ByteString): PowerInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Power", "Sync")
    }
}