package eu.darken.octi.module.core

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) : ModuleSync<T> {

    abstract override val moduleId: ModuleId

    abstract fun onSerialize(item: T): ByteString
    abstract fun onDeserialize(raw: ByteString): T

    override val others: Flow<List<ModuleData<T>>> = syncManager.data
        .map { reads ->
            reads
                .filter { it.deviceId != syncSettings.deviceId }
                .mapNotNull { device ->
                    val rawModule = device.modules.singleOrNull {
                        it.moduleId == moduleId
                    }
                    if (rawModule == null) {
                        log(tag, WARN) { "syncRead(): Missing module $moduleId on ${device.deviceId}" }
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
        .setupCommonEventHandlers(tag) { "others" }

    override suspend fun sync(self: T): ModuleData<T> {
        log(tag, VERBOSE) { "sync(self=$self)" }
        val container = ModuleData(
            modifiedAt = Instant.now(),
            deviceId = syncSettings.deviceId,
            moduleId = moduleId,
            data = self
        )

        syncManager.write(serialize(self))

        return container
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