package eu.darken.octi.sync.core.cache

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
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
        val list = Types.newParameterizedType(Collection::class.java, CachedSyncRead::class.java)
        moshi.adapter<Collection<CachedSyncRead>>(list)
    }
    private val cacheLock = Mutex()
    private val cacheFile = File(context.cacheDir, "sync_cache")

    private suspend inline fun <reified T> guard(crossinline block: () -> T) = withContext(dispatcherProvider.IO) {
        cacheLock.withLock {
            block()
        }
    }

    suspend fun load(): Collection<SyncRead> = guard {
        log(TAG, VERBOSE) { "load()" }
        try {
            if (!cacheFile.exists()) return@guard emptyList()
            adapter.fromJson(cacheFile.readText())!!.also {
                log(TAG, VERBOSE) { "loaded(): $it" }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to load cache sync data: ${e.asLog()}" }
            Bugs.report(e)
            emptyList()
        }
    }

    suspend fun save(reads: Collection<SyncRead>) = guard {
        log(TAG, VERBOSE) { "save(reads=$reads)" }
        try {
            val mapped = reads.map { read ->
                val mappedDevices = read.devices.map { device ->
                    val mappedModules = device.modules.map { it.toCached() }
                    device.toCached(mappedModules)
                }
                read.toCached(mappedDevices)
            }
            if (!cacheFile.exists()) cacheFile.createNewFile()
            cacheFile.writeText(adapter.toJson(mapped))
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to cache sync data: ${e.asLog()}" }
            Bugs.report(e)
        }
    }

    private fun SyncRead.toCached(mappedDevices: List<CachedSyncRead.Device>) = CachedSyncRead(
        connectorId = connectorId,
        devices = mappedDevices,
    )

    private fun SyncRead.Device.toCached(mappedModules: List<CachedSyncRead.Device.Module>) = CachedSyncRead.Device(
        deviceId = deviceId,
        modules = mappedModules,
    )

    private fun SyncRead.Device.Module.toCached() = CachedSyncRead.Device.Module(
        moduleId = moduleId,
        accountId = accountId,
        deviceId = deviceId,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        payload = payload,
    )


    companion object {
        private val TAG = logTag("Sync", "Cache")
    }
}