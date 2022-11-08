package eu.darken.octi.module.core

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.*
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString
import java.io.File
import java.io.IOException
import java.time.Instant


abstract class BaseModuleCache<T : Any> constructor(
    @ApplicationContext private val context: Context,
    override val moduleId: ModuleId,
    private val tag: String,
    private val dispatcherProvider: DispatcherProvider,
    private val moshi: Moshi,
    private val moduleSerializer: ModuleSerializer<T>,
) : ModuleCache<T> {

    private val adapter by lazy { moshi.adapter<CachedModuleData>() }
    private val cacheLock = Mutex()
    private val cacheDir by lazy {
        val moduleCacheBaseDir = File(context.cacheDir, "module_cache")
        File(moduleCacheBaseDir, moduleId.id).also { it.mkdirs() }
    }

    private suspend inline fun <reified T> guard(crossinline block: () -> T) = withContext(dispatcherProvider.IO) {
        cacheLock.withLock { block() }
    }

    private fun DeviceId.getCacheFile(): File = File(cacheDir, this.id)

    @JsonClass(generateAdapter = true)
    data class CachedModuleData(
        @Json(name = "modifiedAt") val modifiedAt: Instant,
        @Json(name = "deviceId") val deviceId: DeviceId,
        @Json(name = "moduleId") val moduleId: ModuleId,
        @Json(name = "data") val data: ByteString,
    )

    private fun ModuleData<T>.toCachedModuleData() = CachedModuleData(
        modifiedAt = modifiedAt,
        deviceId = deviceId,
        moduleId = moduleId,
        data = moduleSerializer.serialize(data)
    )

    private fun CachedModuleData.toModuleData(): ModuleData<T> = ModuleData(
        modifiedAt = modifiedAt,
        deviceId = deviceId,
        moduleId = moduleId,
        data = moduleSerializer.deserialize(data)
    )

    override suspend fun set(deviceId: DeviceId, data: ModuleData<T>?) = guard {
        log(tag, VERBOSE) { "set(id=$deviceId, data=$data)" }

        if (data == null) {
            val cacheFile = deviceId.getCacheFile()
            val success = cacheFile.delete()
            if (!success) log(tag, WARN) { "Failed to delete $cacheFile" }
            return@guard
        }

        try {
            val cacheFile = deviceId.getCacheFile().also {
                if (!it.exists()) it.createNewFile()
            }

            cacheFile.writeText(adapter.toJson(data.toCachedModuleData()))
        } catch (e: Exception) {
            log(tag, ERROR) { "Failed to cache sync data: ${e.asLog()}" }
            Bugs.report(e)
        }
    }

    override suspend fun get(deviceId: DeviceId): ModuleData<T>? = guard {
        val cacheFile = deviceId.getCacheFile()
        if (!cacheFile.exists()) return@guard null

        val uncachedData = try {
            adapter.fromJson(cacheFile.readText())!!.also {
                log(tag, VERBOSE) { "get(id=$deviceId): $it" }
            }
        } catch (e: Exception) {
            log(tag, ERROR) { "Failed to load cache sync data: ${e.asLog()}" }
            Bugs.report(e)
            null
        }

        try {
            uncachedData?.toModuleData().also {
                log(tag, VERBOSE) { "get(id=$deviceId): $it" }
            }
        } catch (e: Exception) {
            log(tag, ERROR) { "Failed to deserialize: ${e.asLog()}\nraw=$uncachedData" }
            null
        }
    }

    override suspend fun cachedDevices(): Collection<DeviceId> = guard {
        try {
            cacheDir.listFiles()?.map { DeviceId(it.name) } ?: emptyList()
        } catch (e: IOException) {
            log(tag, ERROR) { "all() failed to read all cached devices." }
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            emptyList()
        }
    }
}