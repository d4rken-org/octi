package eu.darken.octi.sync.core.cache

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.hashing.Hash
import eu.darken.octi.common.hashing.toHash
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncRead
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCache @Inject constructor(
    private val moshi: Moshi,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
) {
    private val adapter by lazy {
        moshi.adapter<CachedSyncRead>()
    }
    private val cacheLock = Mutex()
    private val cacheDir by lazy {
        File(context.cacheDir, "sync_caches").also { it.mkdirs() }
    }

    private suspend inline fun <reified T> guard(crossinline block: () -> T) = withContext(dispatcherProvider.IO) {
        cacheLock.withLock {
            block()
        }
    }

    private fun ConnectorId.toCacheFile() = File(cacheDir, "$type-${idString.toHash(Hash.Algo.SHA256)}")

    suspend fun load(id: ConnectorId): SyncRead? = guard {
        log(TAG, VERBOSE) { "load(id=$id)" }
        val cacheFile = id.toCacheFile()
        try {
            if (!cacheFile.exists()) return@guard null

            adapter.fromJson(cacheFile.readText())!!.also {
                log(TAG, VERBOSE) { "load(id=$id): $it" }
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

            if (!cacheFile.exists()) cacheFile.createNewFile()
            cacheFile.writeText(adapter.toJson(cachedRead))
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to cache sync data: ${e.asLog()}" }
            Bugs.report(e)
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


    companion object {
        private val TAG = logTag("Sync", "Cache")
    }
}