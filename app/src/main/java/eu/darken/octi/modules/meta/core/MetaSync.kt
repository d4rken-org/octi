package eu.darken.octi.modules.meta.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.meta.MetaModule
import kotlinx.coroutines.CoroutineScope
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaSync @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val moshi: Moshi,
    syncSettings: eu.darken.octi.sync.core.SyncSettings,
    syncManager: eu.darken.octi.sync.core.SyncManager,
) : eu.darken.octi.module.core.BaseModuleSync<MetaInfo>(
    tag = TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
) {

    private val adapter by lazy { moshi.adapter<MetaInfo>() }

    override val moduleId: ModuleId = MetaModule.MODULE_ID

    override fun onSerialize(item: MetaInfo): ByteString = adapter.toByteString(item)

    override fun onDeserialize(raw: ByteString): MetaInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Meta", "Sync")
    }
}