package eu.darken.octi.syncs.gdrive.core

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
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant


@Suppress("BlockingMethodInNonBlockingContext")
class GDriveAppDataConnector @AssistedInject constructor(
    @Assisted private val client: GoogleClient,
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val supportedModuleIds: Set<@JvmSuppressWildcards ModuleId>,
) : GDriveBaseConnector(context, client), eu.darken.octi.sync.core.SyncConnector {

    data class State(
        override val readActions: Int = 0,
        override val writeActions: Int = 0,
        override val lastReadAt: Instant? = null,
        override val lastWriteAt: Instant? = null,
        override val lastError: Exception? = null,
        override val quota: eu.darken.octi.sync.core.SyncConnectorState.Quota? = null,
        override val devices: Collection<eu.darken.octi.sync.core.DeviceId>? = null,
        override val isAvailable: Boolean = true,
    ) : eu.darken.octi.sync.core.SyncConnectorState

    private val _state = DynamicStateFlow(
        parentScope = scope + dispatcherProvider.IO,
        loggingTag = TAG,
    ) {
        State()
    }

    override val state: Flow<State> = _state.flow
    private val _data = MutableStateFlow<eu.darken.octi.sync.core.SyncRead?>(null)
    override val data: Flow<eu.darken.octi.sync.core.SyncRead?> = _data

    private val writeQueue = MutableSharedFlow<eu.darken.octi.sync.core.SyncWrite>()
    private val writeLock = Mutex()
    private val readLock = Mutex()

    override val identifier: eu.darken.octi.sync.core.ConnectorId = eu.darken.octi.sync.core.ConnectorId(
        type = "gdrive",
        subtype = if (client.account.isAppDataScope) "appdatascope" else "",
        account = "account.id.id",
    )

    init {
        writeQueue
            .onEach { toWrite ->
                writeAction {
                    writeDrive(toWrite)
                }
            }
            .retry {
                delay(5000)
                true
            }
            .setupCommonEventHandlers(TAG) { "writeQueue" }
            .launchIn(scope)
    }

    override suspend fun write(toWrite: eu.darken.octi.sync.core.SyncWrite) {
        log(TAG) { "write(toWrite=$toWrite)" }
        writeQueue.emit(toWrite)
    }

    override suspend fun deleteAll() {
        log(TAG, INFO) { "wipe()" }
        writeAction {
            appDataRoot()
                .listFiles()
                .forEach { it.deleteAll() }
        }
    }

    override suspend fun deleteDevice(deviceId: eu.darken.octi.sync.core.DeviceId) {
        log(TAG, INFO) { "deleteDevice(deviceId=$deviceId)" }
        writeAction {
            appDataRoot()
                .listFiles()
                .singleOrNull { it.name == deviceId.id }
                ?.deleteAll()
        }
    }

    private suspend fun readDrive(): GDriveData {
        log(TAG, DEBUG) { "readDrive(): Starting..." }

        val deviceDataDir = appDataRoot().child(DEVICE_DATA_DIR_NAME)
        log(TAG, VERBOSE) { "readDrive(): userDir=$deviceDataDir" }

        if (deviceDataDir?.isDirectory != true) {
            log(TAG, WARN) { "No device data dir found ($deviceDataDir)" }
            return GDriveData(
                connectorId = identifier,
                devices = emptySet(),
            )
        }

        val deviceDirs = deviceDataDir.listFiles()

        val modulesPerDevice = deviceDirs
            .filter {
                val isDir = it.isDirectory
                if (!isDir) log(TAG, WARN) { "Unexpected file in userDir: $it" }
                isDir
            }
            .map { deviceDir ->
                val moduleDirs = deviceDir.listFiles().filter {
                    supportedModuleIds.contains(
                        ModuleId(
                            it.name
                        )
                    )
                }
                deviceDir to moduleDirs
            }

        val devices = modulesPerDevice.mapNotNull { (deviceDir, moduleFiles) ->
            log(TAG, VERBOSE) { "readDrive(): Reading module data for device: $deviceDir" }
            val moduleData = moduleFiles.map { moduleFile ->
                val payload = moduleFile.readData()
                if (payload == null) {
                    log(TAG, WARN) { "readDrive(): Module file is empty: ${moduleFile.name}" }
                    return@mapNotNull null
                }

                GDriveModuleData(
                    connectorId = identifier,
                    deviceId = DeviceId(deviceDir.name),
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

        return GDriveData(
            connectorId = identifier,
            devices = devices,
        )
    }

    override suspend fun sync(options: eu.darken.octi.sync.core.SyncOptions) {
        log(TAG) { "sync(options=$options)" }

        if (options.writeData) {
            // TODO
        }

        if (options.readData) {
            try {
                readAction {
                    _data.value = readDrive()
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to read: ${e.asLog()}" }
                _state.updateBlocking { copy(lastError = e) }
            }
        }

        if (options.stats) {
            try {
                val deviceDirs = appDataRoot().child(DEVICE_DATA_DIR_NAME)
                    ?.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { eu.darken.octi.sync.core.DeviceId(id = it.name) }

                _state.updateBlocking { copy(devices = deviceDirs) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to list of known devices: ${e.asLog()}" }
            }
            try {
                val newQuota = getStorageQuota()
                _state.updateBlocking { copy(quota = newQuota) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to update storage quota: ${e.asLog()}" }
            }
        }
    }

    private suspend fun writeDrive(data: eu.darken.octi.sync.core.SyncWrite) {
        log(TAG, DEBUG) { "writeDrive(): $data)" }

        val userDir = appDataRoot().child(DEVICE_DATA_DIR_NAME)
            ?.also { if (!it.isDirectory) throw IllegalStateException("devices is not a directory: $it") }
            ?: run {
                appDataRoot().createDir(folderName = DEVICE_DATA_DIR_NAME)
                    .also { log(TAG, INFO) { "write(): Created devices dir $it" } }
            }

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

    private suspend fun getStorageQuota(): eu.darken.octi.sync.core.SyncConnectorState.Quota =
        withContext(dispatcherProvider.IO) {
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

            eu.darken.octi.sync.core.SyncConnectorState.Quota(
                updatedAt = Instant.now(),
                storageUsed = allItems.sumOf { it.quotaBytesUsed ?: 0 },
                storageTotal = storageTotal
            )
    }

    private suspend fun readAction(block: suspend () -> Unit) = withContext(dispatcherProvider.IO) {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "readAction(block=$block)" }

        _state.updateBlocking { copy(readActions = readActions + 1) }

        try {
            block()
        } catch (e: Exception) {
            log(TAG, ERROR) { "readAction(block=$block) failed: ${e.asLog()}" }
            throw e
        } finally {
            _state.updateBlocking {
                copy(
                    readActions = readActions - 1,
                    lastReadAt = Instant.now(),
                )
            }
        }

        log(TAG, VERBOSE) { "readAction(block=$block) finished after ${System.currentTimeMillis() - start}ms" }
    }

    private suspend fun writeAction(block: suspend () -> Unit) = withContext(dispatcherProvider.IO + NonCancellable) {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "writeAction(block=$block)" }

        _state.updateBlocking { copy(writeActions = writeActions + 1) }

        try {
            writeLock.withLock {
                try {
                    block()
                } catch (e: Exception) {
                    log(TAG, ERROR) { "writeAction(block=$block) failed: ${e.asLog()}" }
                    throw e
                }
            }
        } finally {
            _state.updateBlocking {
                log(TAG, VERBOSE) { "writeAction(block=$block) finished" }
                copy(
                    writeActions = writeActions - 1,
                    lastWriteAt = Instant.now(),
                )
            }
            log(TAG, VERBOSE) { "writeAction(block=$block) finished after ${System.currentTimeMillis() - start}ms" }
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