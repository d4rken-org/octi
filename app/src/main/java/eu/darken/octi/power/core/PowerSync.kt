package eu.darken.octi.power.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.sync.core.BaseSyncHelper
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncModuleId
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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
    powerSettings: PowerSettings,
    powerInfoSource: PowerInfoSource,
    powerRepo: PowerRepo,
) : BaseSyncHelper<PowerInfo>(
    tag = TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
    moduleRepo = powerRepo,
    infoSource = powerInfoSource
) {

    private val adapter by lazy { moshi.adapter<PowerInfo>() }

    override val isEnabled: Flow<Boolean> = powerSettings.isEnabled.flow

    override val moduleId: SyncModuleId = MODULE_ID

    override fun onSerialize(item: PowerInfo): ByteString = adapter.toByteString(item)

    override fun onDeserialize(raw: ByteString): PowerInfo = adapter.fromJson(raw)!!

    companion object {
        val MODULE_ID = SyncModuleId("${BuildConfigWrap.APPLICATION_ID}.module.core.power")
        val TAG = logTag("Module", "Power", "Sync")
    }
}