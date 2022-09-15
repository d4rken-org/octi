package eu.darken.octi.sync.core.provider.gdrive

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.*
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.ModuleId
import eu.darken.octi.sync.core.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import com.google.api.services.drive.model.File as GDriveFile


@Suppress("BlockingMethodInNonBlockingContext")
class GDriveAppDataConnector @AssistedInject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    @Assisted private val client: GoogleClient,
) : GDriveBaseConnector(context, client), Sync.Connector {

    data class State(
        override val isReading: Boolean = false,
        override val isWriting: Boolean = false,
        override val lastReadAt: Instant? = null,
        override val lastWriteAt: Instant? = null,
        override val lastError: Exception? = null,
        override val stats: Sync.Connector.State.Stats? = null,
    ) : Sync.Connector.State

    private val _state = DynamicStateFlow(
        parentScope = scope + dispatcherProvider.IO,
        loggingTag = TAG,
    ) {
        State()
    }

    override val state: Flow<State> = _state.flow
    private val _data = MutableStateFlow<Sync.Read?>(null)
    override val data: Flow<Sync.Read?> = _data

    private val writeQueue = MutableSharedFlow<Sync.Write>()

    init {
        writeQueue
            .onEach { toWrite ->
                var alreadyWriting = false
                _state.updateBlocking {
                    alreadyWriting = isReading
                    copy(isWriting = true)
                }
                if (alreadyWriting) throw IllegalStateException("Concurrent write attempt!")

                var writeError: Exception? = null
                try {
                    writeDrive(toWrite)
                } catch (e: Exception) {
                    writeError = e
                    log(TAG, ERROR) { "writeDrive() failed: ${e.asLog()}" }
                }

                _state.updateBlocking {
                    log(TAG) { "writeDrive() finished" }
                    copy(
                        isWriting = false,
                        lastWriteAt = Instant.now(),
                        lastError = writeError,
                    )
                }
                if (writeError != null) throw writeError
            }
            .retry {
                delay(5000)
                true
            }
            .setupCommonEventHandlers(TAG) { "writeQueue" }
            .launchIn(scope + dispatcherProvider.IO)
    }

    override suspend fun read() {
        log(TAG) { "read()" }
        var alreadyReading = false
        _state.updateBlocking {
            alreadyReading = isReading
            copy(isReading = true)
        }
        if (alreadyReading) {
            log(TAG, WARN) { "Read already in progress, skipping." }
            return
        }

        var readError: Exception? = null

        var newStorageStats: Sync.Connector.State.Stats? = null

        try {
            _data.value = readDrive()

            val lastStats = _state.value().stats?.timestamp
            if (lastStats == null || Duration.between(lastStats, Instant.now()) > Duration.ofSeconds(60)) {
                log(TAG) { "read(): Updating storage stats" }
                newStorageStats = getStorageStats()
            }
        } catch (e: Exception) {
            readError = e
            log(TAG, ERROR) { "readDrive() failed: ${e.asLog()}" }
        }

        _state.updateBlocking {
            log(TAG) { "sync() finished" }
            copy(
                isReading = false,
                stats = newStorageStats ?: stats,
                lastReadAt = Instant.now(),
                lastError = readError,
            )
        }
    }

    override suspend fun write(toWrite: Sync.Write) {
        log(TAG) { "write(toWrite=$toWrite)" }
        writeQueue.emit(toWrite)
    }

    private suspend fun readDrive(): GDriveData = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "readDrive(): Starting..." }

        val userDir = getOrCreateDeviceDir()
        log(TAG, VERBOSE) { "readDrive(): userDir=$userDir" }

        val deviceDirs = userDir.listFiles()

        val modulesPerDevice = deviceDirs
            .filter {
                val isDir = it.isDirectory
                if (!isDir) log(TAG, WARN) { "Unexpected file in userDir: $it" }
                isDir
            }
            .map { deviceDir -> deviceDir to deviceDir.listFiles() }

        val devices = modulesPerDevice.mapNotNull { (deviceDir, moduleFiles) ->
            log(TAG, VERBOSE) { "readDrive(): Reading module data for device: $deviceDir" }
            val moduleData = moduleFiles.map { moduleFile ->
                val payload = moduleFile.readData()
                if (payload == null) {
                    log(TAG, WARN) { "readDrive(): Device file is empty: ${moduleFile.name}" }
                    return@mapNotNull null
                }

                GDriveModuleData(
                    moduleId = ModuleId(moduleFile.name),
                    createdAt = Instant.ofEpochMilli(moduleFile.createdTime.value),
                    modifiedAt = Instant.ofEpochMilli(moduleFile.modifiedTime.value),
                    payload = payload,
                ).also {
                    log(TAG, VERBOSE) { "readDrive(): Module data: $it" }
                }
            }

            GDriveDeviceData(
                deviceId = DeviceId(deviceDir.name),
                modules = moduleData
            )
        }
        GDriveData(
            devices = devices
        )
    }

    private suspend fun writeDrive(data: Sync.Write) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "writeDrive(): $data)" }

        val userDir = getOrCreateDeviceDir()
        val deviceIdRaw = data.deviceId.id.toString()
        val deviceDir = userDir.child(deviceIdRaw) ?: userDir.createDir(deviceIdRaw).also {
            log(TAG) { "writeDrive(): Created device dir $it" }
        }

        data.modules.forEach { module ->
            log(TAG, VERBOSE) { "writeDrive(): Writing module $module" }
            val moduleFile = deviceDir.child(module.moduleId.id) ?: deviceDir.createFile(module.moduleId.id).also {
                log(TAG, VERBOSE) { "writeDrive(): Created module file $it" }
            }
            moduleFile.writeData(module.payload)
        }

        log(TAG, VERBOSE) { "writeDrive(): Done" }
    }

    private fun getStorageStats(): Sync.Connector.State.Stats {
        log(TAG, VERBOSE) { "getStorageStats()" }
        val allItems = gdrive.files()
            .list().apply {
                spaces = APPDATAFOLDER
                fields = "files(id,name,mimeType,createdTime,modifiedTime,size)"
            }
            .execute().files


        val storageTotal = gdrive.about()
            .get().setFields("storageQuota")
            .execute().storageQuota
            .limit

        return Sync.Connector.State.Stats(
            timestamp = Instant.now(),
            storageUsed = allItems.sumOf { it.quotaBytesUsed ?: 0 },
            storageTotal = storageTotal
        )
    }

    private fun getOrCreateDeviceDir(): GDriveFile {
        val userDir = appDataRoot().child(DEVICE_DATA_DIR_NAME)
        if (userDir?.isDirectory == false) throw IllegalStateException("devices is not a directory: $userDir")
        if (userDir != null) return userDir
        return appDataRoot().createDir(folderName = DEVICE_DATA_DIR_NAME).also {
            log(TAG) { "write(): Created devices dir $it" }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(client: GoogleClient): GDriveAppDataConnector
    }

    companion object {
        private const val DEVICE_DATA_DIR_NAME = "devices"
        private val TAG = logTag("Sync", "GDrive", "Connector")
    }
}