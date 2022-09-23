package eu.darken.octi.modules

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.*
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import eu.darken.octi.sync.core.errors.PayloadDecodingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.time.Instant


abstract class BaseModuleSync<T : Any> constructor(
    private val tag: String,
    private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val syncManager: SyncManager,
    private val moduleRepo: ModuleRepo<T>,
    private val infoSource: ModuleInfoSource<T>,
) : ModuleSync<T> {

    abstract override val moduleId: ModuleId
    abstract val isEnabled: Flow<Boolean>

    abstract fun onSerialize(item: T): ByteString
    abstract fun onDeserialize(raw: ByteString): T

    override fun start() {
        log(tag) { "start()" }

        // Read
        isEnabled
            .flatMapLatest { isEnabled ->
                if (!isEnabled) emptyFlow()
                else syncManager.data
            }
            .map { reads ->
                reads
                    .filter { it.deviceId != syncSettings.deviceId }
                    .mapNotNull { device ->
                        val rawModule = device.modules.singleOrNull {
                            it.moduleId == moduleId
                        }
                        if (rawModule == null) {
                            log(tag, WARN) { "syncRead(): Missing meta module on $device" }
                            return@mapNotNull null
                        }

                        try {
                            ModuleData(
                                modifiedAt = rawModule.modifiedAt,
                                deviceId = device.deviceId,
                                moduleId = moduleId,
                                data = deserialize(rawModule),
                            )
                        } catch (e: Exception) {
                            log(tag, ERROR) { "syncRead(): Failed to decode $rawModule:\n${e.asLog()}" }
                            Bugs.report(PayloadDecodingException(rawModule))
                            null
                        }
                    }
            }
            .onEach { infos ->
                log(tag, VERBOSE) { "syncRead(): Processing updating others: $infos" }
                moduleRepo.updateOthers(infos)
            }
            .setupCommonEventHandlers(tag) { "syncRead" }
            .launchIn(scope + dispatcherProvider.IO)

        // Write
        isEnabled
            .flatMapLatest {
                if (!it) emptyFlow()
                else infoSource.info
            }
            .distinctUntilChanged()
            .onEach {
                log(tag, VERBOSE) { "syncWrite(): Processing updating self: $it" }
                val container = ModuleData(
                    modifiedAt = Instant.now(),
                    deviceId = syncSettings.deviceId,
                    moduleId = moduleId,
                    data = it
                )
                moduleRepo.updateSelf(container)
                syncManager.write(serialize(it))
                syncManager.sync()
            }
            .setupCommonEventHandlers(tag) { "syncWrite" }
            .launchIn(scope + dispatcherProvider.IO)
    }

    private fun serialize(item: T): SyncWrite.Device.Module {
        val serialized = try {
            onSerialize(item)
        } catch (e: Exception) {
            throw IOException("Failed to serialize $this", e)
        }
        return object : SyncWrite.Device.Module {
            override val moduleId: ModuleId = this@BaseModuleSync.moduleId
            override val payload: ByteString = serialized.toByteArray().toByteString()
            override fun toString(): String = item.toString()
        }
    }

    private fun deserialize(raw: SyncRead.Device.Module): T {
        if (raw.moduleId != this@BaseModuleSync.moduleId) {
            throw IllegalArgumentException("Wrong moduleId: ${moduleId}\n$this")
        }
        return try {
            onDeserialize(raw.payload)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to deserialize ${raw.payload}", e)
        }
    }
}