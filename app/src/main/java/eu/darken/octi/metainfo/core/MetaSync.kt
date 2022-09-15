package eu.darken.octi.metainfo.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.sync.core.ModuleId
import eu.darken.octi.sync.core.Sync
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaSync @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncOptions: SyncOptions,
    private val syncManager: SyncManager,
    private val metaRepo: MetaRepo,
    private val metaSettings: MetaSettings,
    private val moshi: Moshi,
) {

    private val adapter by lazy { moshi.adapter<MetaInfo>() }

    fun start() {
        log(TAG) { "start()" }

        // Read
        metaSettings.isSyncEnabled.flow
            .flatMapLatest { isEnabled ->
                log(TAG) { "SyncRead: isEnabled=$isEnabled" }
                if (!isEnabled) return@flatMapLatest emptyFlow()
                else syncManager.data
            }
            .map { reads ->
                reads
                    .map { it.devices }.flatten()
                    .filter { it.deviceId != syncOptions.deviceId }
                    .map { device ->
                        device.modules
                            .filter { it.moduleId == MODULE_ID }
                            .map { device to it.toMetaInfo() }
                    }
                    .flatten()
            }
            .onEach { infos ->
                log(TAG, VERBOSE) { "SyncRead: Processing new data: $infos" }
                val others = infos.map { (device, info) ->
                    SyncDataContainer(
                        deviceId = device.deviceId,
                        data = info,
                    )
                }
                metaRepo.updateOthers(others)
            }
            .setupCommonEventHandlers(TAG) { "syncRead" }
            .launchIn(scope + dispatcherProvider.IO)

        // Write
        metaSettings.isSyncEnabled.flow
            .flatMapLatest { isEnabled ->
                log(TAG) { "SyncWrite: isEnabled=$isEnabled" }
                if (!isEnabled) return@flatMapLatest emptyFlow()
                else metaRepo.state.map { it.self }
            }
            .onEach {
                log(TAG, VERBOSE) { "SyncWrite: Processing new data: $it" }
                syncManager.write(it.data.toWriteModule())
            }
            .setupCommonEventHandlers(TAG) { "syncWrite" }
            .launchIn(scope + dispatcherProvider.IO)
    }

    private fun MetaInfo.toWriteModule(): Sync.Write.Module {
        val serialized = try {
            adapter.toByteString(this)
        } catch (e: Exception) {
            throw IOException("Failed to serialize $this", e)
        }
        return object : Sync.Write.Module {
            override val moduleId: ModuleId = MODULE_ID
            override val payload: ByteString = serialized.toByteArray().toByteString()
            override fun toString(): String = this@toWriteModule.toString()
        }
    }

    private fun Sync.Read.Module.toMetaInfo(): MetaInfo {
        if (moduleId != MODULE_ID) {
            throw IllegalArgumentException("Wrong moduleId: ${moduleId}\n$this")
        }
        return try {
            adapter.fromJson(payload)!!
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to deserialize $payload", e)
        }
    }

    companion object {
        val MODULE_ID = ModuleId("${BuildConfigWrap.APPLICATION_ID}.module.core.time")
        val TAG = logTag("Module", "Time", "Sync")
    }
}