package eu.darken.octi.sync.core.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.*
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.hashing.Hash
import eu.darken.octi.common.hashing.toHash
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.SyncRead
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
class SyncCache @Inject constructor(
    private val json: Json,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
) {
    private val cacheLock = Mutex()
    private val cacheDir by lazy { File(context.cacheDir, "sync_caches").also { it.mkdirs() } }
    private val deviceMetadataCacheDir by lazy { File(cacheDir, "device_metadata").also { it.mkdirs() } }

    private suspend inline fun <reified T> guard(crossinline block: () -> T) = withContext(dispatcherProvider.IO) {
        cacheLock.withLock {
            block()
        }
    }

    private fun ConnectorId.toCacheFile() = File(cacheDir, "$type-${idString.toHash(Hash.Algo.SHA256)}")
    private fun ConnectorId.toDeviceMetadataCacheFile() =
        File(deviceMetadataCacheDir, "$type-${idString.toHash(Hash.Algo.SHA256)}.json")

    suspend fun load(id: ConnectorId): SyncRead? = guard {
        log(TAG, VERBOSE) { "load(id=$id)" }
        val cacheFile = id.toCacheFile()
        try {
            if (!cacheFile.exists()) return@guard null

            json.decodeFromString<CachedSyncRead>(cacheFile.readText()).also {
                log(TAG, VERBOSE) { "load(id=$id): ${it.devices.size} devices" }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to load cache sync data: ${e.asLog()}" }
            Bugs.report(e)
            null
        }
    }

    suspend fun save(id: ConnectorId, read: SyncRead): Unit = guard {
        log(TAG, VERBOSE) { "save(id=$id, read=$read)" }
        try {
            val cachedRead = read.devices
                .map { device ->
                    val mappedModules = device.modules.map { it.toCached() }
                    device.toCached(mappedModules)
                }
                .let { read.toCached(it) }

            val cacheFile = id.toCacheFile()

            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.createNewFile()
            }
            cacheFile.writeText(json.encodeToString(cachedRead))
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to cache sync data: ${e.asLog()}" }
            Bugs.report(e)
        }
    }

    suspend fun loadDeviceMetadata(id: ConnectorId): List<DeviceMetadata>? = guard {
        log(TAG, VERBOSE) { "loadDeviceMetadata(id=$id)" }
        val cacheFile = id.toDeviceMetadataCacheFile()
        try {
            if (!cacheFile.exists()) return@guard null

            val cached = json.decodeFromString<CachedConnectorDeviceMetadata>(cacheFile.readText())
            if (cached.schemaVersion != CachedConnectorDeviceMetadata.SCHEMA_VERSION) {
                discardDeviceMetadataCache(cacheFile, "unsupported schema ${cached.schemaVersion}")
                return@guard null
            }
            if (cached.connectorId != id) {
                discardDeviceMetadataCache(cacheFile, "connector mismatch")
                return@guard null
            }
            cached.devices.also {
                log(TAG, VERBOSE) { "loadDeviceMetadata(id=$id): ${it.size} devices" }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to load cached device metadata: ${e.asLog()}" }
            discardDeviceMetadataCache(cacheFile, "decode failed")
            null
        }
    }

    suspend fun saveDeviceMetadata(id: ConnectorId, devices: List<DeviceMetadata>): Unit = guard {
        log(TAG, VERBOSE) { "saveDeviceMetadata(id=$id, devices=${devices.size})" }
        try {
            val cached = CachedConnectorDeviceMetadata(
                connectorId = id,
                cachedAt = Clock.System.now(),
                devices = devices,
            )
            val cacheFile = id.toDeviceMetadataCacheFile()
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeTextAtomic(json.encodeToString(cached))
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to cache device metadata: ${e.asLog()}" }
        }
    }

    private fun discardDeviceMetadataCache(cacheFile: File, reason: String) {
        log(TAG, WARN) { "Discarding device metadata cache ($reason): $cacheFile" }
        if (cacheFile.exists() && !cacheFile.delete()) {
            log(TAG, WARN) { "Failed to delete invalid device metadata cache: $cacheFile" }
        }
    }

    private fun File.writeTextAtomic(text: String) {
        val parent = parentFile ?: throw IOException("Cache file has no parent: $this")
        parent.mkdirs()
        val tmpFile = File(parent, "$name.tmp")

        try {
            if (tmpFile.exists() && !tmpFile.delete()) {
                throw IOException("Failed to delete stale temp cache file: $tmpFile")
            }

            tmpFile.writeText(text)

            if (!tmpFile.renameTo(this)) {
                if (exists() && !delete()) {
                    throw IOException("Failed to delete old cache file: $this")
                }
                if (!tmpFile.renameTo(this)) {
                    throw IOException("Failed to rename temp cache file $tmpFile to $this")
                }
            }
        } finally {
            if (tmpFile.exists() && !tmpFile.delete()) {
                log(TAG, WARN) { "Failed to delete temp cache file: $tmpFile" }
            }
        }
    }

    private fun SyncRead.toCached(mappedDevices: List<CachedSyncRead.Device>) = CachedSyncRead(
        connectorId = connectorId,
        devices = mappedDevices,
    )

    private fun SyncRead.Device.toCached(mappedModules: List<CachedSyncRead.Device.Module>) =
        CachedSyncRead.Device(
            deviceId = deviceId,
            modules = mappedModules,
        )

    private fun SyncRead.Device.Module.toCached() = CachedSyncRead.Device.Module(
        moduleId = moduleId,
        connectorId = connectorId,
        deviceId = deviceId,
        modifiedAt = modifiedAt,
        payload = payload,
    )

    suspend fun remove(id: ConnectorId) = guard {
        log(TAG) { "remove(id=$id)" }
        val cacheFile = id.toCacheFile()
        val success = cacheFile.delete()
        if (!success) log(TAG, WARN) { "Failed to delete $cacheFile" }
    }

    suspend fun removeDeviceMetadata(id: ConnectorId) = guard {
        log(TAG) { "removeDeviceMetadata(id=$id)" }
        val cacheFile = id.toDeviceMetadataCacheFile()
        val success = cacheFile.delete()
        if (!success && cacheFile.exists()) log(TAG, WARN) { "Failed to delete $cacheFile" }
    }

    companion object {
        private val TAG = logTag("Sync", "Cache")
    }
}
