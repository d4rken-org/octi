package eu.darken.octi.syncs.gdrive.core

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import eu.darken.octi.syncs.gdrive.core.GDriveEnvironment.Companion.APPDATAFOLDER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException
import java.time.Instant
import com.google.api.services.drive.model.File as GDriveFile


class GDriveAppDataConnector @AssistedInject constructor(
    @Assisted private val client: GoogleClient,
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val networkStateProvider: NetworkStateProvider,
    private val supportedModuleIds: Set<@JvmSuppressWildcards ModuleId>,
    private val syncSettings: SyncSettings,
) : GDriveBaseConnector(dispatcherProvider, context, client), SyncConnector {

    data class State(
        override val activeActions: Int = 0,
        override val lastActionAt: Instant? = null,
        override val lastError: Exception? = null,
        override val quota: SyncConnectorState.Quota? = null,
        override val devices: Collection<DeviceId>? = null,
        override val isAvailable: Boolean = true,
        val isDead: Boolean = false,
    ) : SyncConnectorState

    private val _state = DynamicStateFlow(
        parentScope = scope + dispatcherProvider.IO,
        loggingTag = TAG,
    ) {
        State()
    }

    override val state: Flow<State> = _state.flow
    private val _data = MutableStateFlow<SyncRead?>(null)
    override val data: Flow<SyncRead?> = _data
    private val driveLock = Mutex()
    private val writeQueue = MutableSharedFlow<SyncWrite>()

    override val identifier: ConnectorId = ConnectorId(
        type = "gdrive",
        subtype = if (client.account.isAppDataScope) "appdatascope" else "",
        account = account.id.id,
    )

    init {
        writeQueue
            .onEach { toWrite ->
                runDriveAction("write-queue: $toWrite") {
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

    private suspend fun isInternetAvailable() = networkStateProvider.networkState.first().isInternetAvailable

    override suspend fun write(toWrite: SyncWrite) {
        log(TAG) { "write(toWrite=$toWrite)" }
        writeQueue.emit(toWrite)
    }

    override suspend fun resetData(): Unit = withContext(NonCancellable) {
        log(TAG, INFO) { "resetData()" }
        runDriveAction("reset-data") {
            appDataRoot.child(DEVICE_DATA_DIR_NAME)
                ?.listFiles()
                ?.forEach { file: GDriveFile ->
                    log(TAG, INFO) { "resetData(): Deleting $file" }
                    file.deleteAll()
                }
        }
    }

    override suspend fun deleteDevice(deviceId: DeviceId): Unit = withContext(NonCancellable) {
        log(TAG, INFO) { "deleteDevice(deviceId=$deviceId)" }
        runDriveAction("delete-device: $deviceId") {
            appDataRoot.child(DEVICE_DATA_DIR_NAME)
                ?.listFiles()
                ?.onEach { log(TAG, DEBUG) { "deleteDevice(): Checking device dir ${it.name}" } }
                ?.singleOrNull { file ->
                    (file.name == deviceId.id).also {
                        if (it) log(TAG) { "deleteDevice(): Deleting device dir $file" }
                    }
                }
                ?.deleteAll()
            if (deviceId == syncSettings.deviceId) {
                log(TAG, WARN) { "We just deleted ourselves, this connector is dead now" }
                _state.updateBlocking { copy(isDead = true) }
            }
        }
    }

    private suspend fun GDriveEnvironment.readDrive(): GDriveData {
        log(TAG, DEBUG) { "readDrive(): Starting..." }
        val start = System.currentTimeMillis()

        val deviceDataDir = appDataRoot.child(DEVICE_DATA_DIR_NAME)
        log(TAG, VERBOSE) { "readDrive(): userDir=$deviceDataDir" }

        if (deviceDataDir?.isDirectory != true) {
            log(TAG, WARN) { "No device data dir found ($deviceDataDir)" }
            return GDriveData(
                connectorId = identifier,
                devices = emptySet(),
            )
        }

        val validDeviceDirs = deviceDataDir.listFiles().filter {
            val isDir = it.isDirectory
            if (!isDir) log(TAG, WARN) { "Unexpected file in userDir: $it" }
            isDir
        }

        val deviceFetchJobs = validDeviceDirs.map { deviceDir ->
            scope.async(dispatcherProvider.IO) deviceFetch@{
                log(TAG, DEBUG) { "readDrive(): Reading module data for device: $deviceDir" }
                val moduleDirs = deviceDir.listFiles().filter { supportedModuleIds.contains(ModuleId(it.name)) }
                val moduleFetchJobs = moduleDirs.map { moduleFile ->
                    scope.async(dispatcherProvider.IO) moduleFetch@{
                        log(TAG, VERBOSE) { "readDrive(): Reading ${moduleFile.name} for ${deviceDir.name}" }
                        val payload = moduleFile.readData()

                        if (payload == null) {
                            log(TAG, WARN) { "readDrive(): Module file is empty: ${moduleFile.name}" }
                            return@moduleFetch null
                        }

                        GDriveModuleData(
                            connectorId = identifier,
                            deviceId = DeviceId(deviceDir.name),
                            moduleId = ModuleId(moduleFile.name),
                            modifiedAt = Instant.ofEpochMilli(moduleFile.modifiedTime.value),
                            payload = payload,
                        ).also { log(TAG, VERBOSE) { "readDrive(): Got module data: $it" } }
                    }
                }

                val moduleData = moduleFetchJobs.awaitAll().filterNotNull()
                log(TAG, DEBUG) { "readDrive(): Finished ${deviceDir.name}" }

                GDriveDeviceData(
                    deviceId = DeviceId(deviceDir.name),
                    modules = moduleData,
                )
            }
        }

        val devices = deviceFetchJobs.awaitAll()
        log(TAG) { "readDrive() took ${System.currentTimeMillis() - start}ms" }

        return GDriveData(
            connectorId = identifier,
            devices = devices,
        )
    }

    private val syncLock = Mutex()
    private var isSyncing = false

    override suspend fun sync(options: SyncOptions) {
        log(TAG) { "sync(options=$options)" }
        val start = System.currentTimeMillis()

        if (!isInternetAvailable()) {
            log(TAG, WARN) { "sync(): Skipping, we are offline." }
            return
        }

        syncLock.withLock {
            if (isSyncing) {
                log(TAG, WARN) { "sync(): Already syncing, skipping" }
                return
            } else {
                isSyncing = true
                log(TAG, VERBOSE) { "sync(): Starting sync, acquiring" }
            }
        }

        runDriveAction("sync-sync") {
            val jobs = mutableSetOf<Deferred<*>>()


            scope.async(dispatcherProvider.IO) {
                if (!options.stats) return@async

                try {
                    val deviceDirs = appDataRoot.child(DEVICE_DATA_DIR_NAME)
                        ?.listFiles()
                        ?.filter { it.isDirectory }
                        ?.map { DeviceId(id = it.name) }

                    _state.updateBlocking { copy(devices = deviceDirs) }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "sync(): Failed to list of known devices: ${e.asLog()}" }
                }
                val stop = System.currentTimeMillis()
                log(TAG, VERBOSE) { "sync(...): devices finished (took ${stop - start}ms)" }
            }.run { jobs.add(this) }

            scope.async(dispatcherProvider.IO) {
                if (!options.stats) return@async

                try {
                    val newQuota = getStorageQuota()
                    _state.updateBlocking { copy(quota = newQuota) }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "sync(): Failed to update storage quota: ${e.asLog()}" }
                }
                val stop = System.currentTimeMillis()
                log(TAG, VERBOSE) { "sync(...): quota finished (took ${stop - start}ms)" }
            }.run { jobs.add(this) }


            if (options.writeData) {
                // TODO Attempt to write data if we were offline and are now online?
            }


            scope.async(dispatcherProvider.IO) {
                if (!options.readData) return@async

                try {
                    _data.value = readDrive()
                } catch (e: Exception) {
                    log(TAG, ERROR) { "sync(): Failed to read: ${e.asLog()}" }
                }
                val stop = System.currentTimeMillis()
                log(TAG, VERBOSE) { "sync(...): readData finished (took ${stop - start}ms)" }
            }.run { jobs.add(this) }


            log(TAG) { "sync(...): Waiting for jobs to finish..." }
            jobs.awaitAll()
            log(TAG) { "sync(...): ... jobs finished." }
        }


        syncLock.withLock {
            isSyncing = false
            val stop = System.currentTimeMillis()
            log(TAG, VERBOSE) { "sync(): Sync done, releasing (took ${stop - start}ms)" }
        }
    }

    private suspend fun GDriveEnvironment.writeDrive(data: SyncWrite) = withContext(NonCancellable) {
        log(TAG, DEBUG) { "writeDrive(): $data" }

        // TODO cache write data for when we are online again?
        if (!isInternetAvailable()) {
            log(TAG, WARN) { "writeDrive(): Skipping, we are offline." }
            return@withContext
        }

        val userDir = appDataRoot.child(DEVICE_DATA_DIR_NAME)
            ?.also { if (!it.isDirectory) throw IllegalStateException("devices is not a directory: $it") }
            ?: run {
                appDataRoot.createDir(folderName = DEVICE_DATA_DIR_NAME).also {
                    log(TAG, INFO) { "write(): Created devices dir $it" }
                }
            }

        val deviceIdRaw = data.deviceId.id
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

    private suspend fun GDriveEnvironment.getStorageQuota(): SyncConnectorState.Quota {
        log(TAG, VERBOSE) { "getStorageStats()" }
        val allItems = drive.files()
            .list().apply {
                spaces = APPDATAFOLDER
                fields = "files(id,name,mimeType,createdTime,modifiedTime,size)"
            }
            .execute().files

        val storageTotal = drive.about()
            .get().setFields("storageQuota")
            .execute().storageQuota
            .limit

        return SyncConnectorState.Quota(
            updatedAt = Instant.now(),
            storageUsed = allItems.sumOf { it.quotaBytesUsed ?: 0 },
            storageTotal = storageTotal
        )
    }

    private suspend fun <R> runDriveAction(
        tag: String,
        block: suspend GDriveEnvironment.() -> R,
    ): R {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "runDriveAction($tag)" }

        if (_state.value().isDead) {
            log(TAG, WARN) { "Connector is DEAD" }
            throw IOException("Connector is dead")
        }

        return try {
            _state.updateBlocking { copy(activeActions = activeActions + 1) }

            withDrive {
                driveLock.withLock {
                    block()
                }
            }.also {
                _state.updateBlocking { copy(lastError = null) }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "runDriveAction($tag) failed: ${e.asLog()}" }
            _state.updateBlocking { copy(lastError = e) }
            throw e
        } finally {
            _state.updateBlocking {
                log(TAG, VERBOSE) { "runDriveAction($tag) finished" }
                copy(
                    activeActions = activeActions - 1,
                    lastActionAt = Instant.now(),
                )
            }
            log(TAG, VERBOSE) { "runDriveAction($tag) finished after ${System.currentTimeMillis() - start}ms" }
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