package eu.darken.octi.syncs.gdrive.core

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.datastore.value
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
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnector.EventMode
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncEvent
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import eu.darken.octi.sync.core.SyncWriteContainer
import eu.darken.octi.syncs.gdrive.core.GDriveEnvironment.Companion.APPDATAFOLDER
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import com.google.api.services.drive.model.File as GDriveFile


class GDriveAppDataConnector @AssistedInject constructor(
    @Assisted private val client: GoogleClient,
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val networkStateProvider: NetworkStateProvider,
    private val supportedModuleIds: Set<@JvmSuppressWildcards ModuleId>,
    private val syncSettings: SyncSettings,
    private val json: Json,
) : GDriveBaseConnector(dispatcherProvider, context, client), SyncConnector {

    data class State(
        override val activeActions: Int = 0,
        override val lastActionAt: Instant? = null,
        override val lastError: Exception? = null,
        override val quota: SyncConnectorState.Quota? = null,
        override val isAvailable: Boolean = true,
        override val issues: List<ConnectorIssue> = emptyList(),
        override val deviceMetadata: List<DeviceMetadata> = emptyList(),
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
    // TODO: Consider removing lock — concurrent syncs may be safe since each module is an independent endpoint
    private val driveLock = Mutex()

    override val accountLabel: String get() = account.email

    override val identifier: ConnectorId = ConnectorId(
        type = ConnectorType.GDRIVE,
        subtype = if (client.account.isAppDataScope) "appdatascope" else "",
        account = account.id.id,
    )

    private val pollToken = syncSettings.dataStore.createValue(
        "gdrive.poll_token.${account.id.id}",
        null as String?,
    )

    private val syncToken = syncSettings.dataStore.createValue(
        "gdrive.sync_token.${account.id.id}",
        null as String?,
    )

    private val parentCache = mutableMapOf<String, String>()
    private val fileIdCache = ConcurrentHashMap<Pair<DeviceId, ModuleId>, String>()
    private val fileIdReverseCache = ConcurrentHashMap<String, Pair<DeviceId, ModuleId>>()

    private val _syncEventMode = MutableStateFlow(EventMode.NONE)
    override val syncEventMode: StateFlow<EventMode> = _syncEventMode.asStateFlow()

    override val syncEvents: Flow<SyncEvent> = flow {
        while (true) {
            delay(POLL_INTERVAL_MS)
            try {
                val changes = withDrive { checkForChanges() }
                changes.forEach { emit(it) }
            } catch (e: Exception) {
                log(TAG, WARN) { "syncEvents poll failed: ${e.message}" }
            }
        }
    }
        .onStart { _syncEventMode.value = EventMode.POLLING }
        .onCompletion { _syncEventMode.value = EventMode.NONE }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    private suspend fun GDriveEnvironment.checkForChanges(): List<SyncEvent> {
        val token = pollToken.value()
        if (token == null) {
            val startToken = drive.changes().getStartPageToken()
                .setSupportsAllDrives(false)
                .execute().startPageToken
            pollToken.value(startToken)
            log(TAG) { "checkForChanges(): Initialized token=$startToken" }
            return emptyList()
        }

        return try {
            val allChanges = mutableListOf<com.google.api.services.drive.model.Change>()
            var pageToken: String? = token
            var finalToken: String? = null

            while (pageToken != null) {
                val changeList = drive.changes().list(pageToken)
                    .setSpaces(APPDATAFOLDER)
                    .setFields("nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,parents))")
                    .execute()
                allChanges.addAll(changeList.changes ?: emptyList())
                finalToken = changeList.newStartPageToken
                pageToken = changeList.nextPageToken
            }

            finalToken?.let { pollToken.value(it) }

            if (allChanges.isEmpty()) return emptyList()

            log(TAG) { "checkForChanges(): ${allChanges.size} changes detected" }

            allChanges.mapNotNull { change ->
                if (change.removed) {
                    fileIdReverseCache.remove(change.fileId)?.let { fileIdCache.remove(it) }
                    return@mapNotNull null
                }
                val file = change.file ?: return@mapNotNull null
                val parentId = file.parents?.firstOrNull() ?: return@mapNotNull null

                val parentName = parentCache.getOrPut(parentId) {
                    try {
                        drive.files().get(parentId).setFields("name").execute().name
                    } catch (e: Exception) {
                        log(TAG, WARN) { "checkForChanges(): Failed to resolve parent $parentId" }
                        return@mapNotNull null
                    }
                }

                val moduleId = ModuleId(file.name)
                if (moduleId in supportedModuleIds) {
                    val cacheKey = DeviceId(parentName) to moduleId
                    fileIdCache[cacheKey] = file.id
                    fileIdReverseCache[file.id] = cacheKey
                }

                SyncEvent.ModuleChanged(
                    connectorId = identifier,
                    deviceId = DeviceId(parentName),
                    moduleId = moduleId,
                    modifiedAt = Instant.now(),
                    action = SyncEvent.ModuleChanged.Action.UPDATED,
                )
            }
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            if (e.statusCode == 410) {
                log(TAG, WARN) { "checkForChanges(): Token invalidated, resetting" }
                parentCache.clear()
                clearFileIdCache()
                pollToken.value(null)
            } else {
                log(TAG, ERROR) { "checkForChanges(): API error: ${e.message}" }
            }
            emptyList()
        }
    }

    private suspend fun isInternetAvailable() = networkStateProvider.networkState.first().isInternetAvailable

    override suspend fun resetData(): Unit = withContext(NonCancellable) {
        log(TAG, INFO) { "resetData()" }
        clearFileIdCache()
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
        evictFileIdCache(deviceId)
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
                clearFileIdCache()
                _state.updateBlocking { copy(isDead = true) }
            }
        }
    }

    private suspend fun GDriveEnvironment.readDrive(
        moduleFilter: Set<ModuleId>? = null,
        deviceFilter: Set<DeviceId>? = null,
    ): GDriveData {
        log(TAG, DEBUG) { "readDrive(moduleFilter=$moduleFilter, deviceFilter=$deviceFilter): Starting..." }
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

        val allDeviceDirs = deviceDataDir.listFiles().filter {
            val isDir = it.isDirectory
            if (!isDir) log(TAG, WARN) { "Unexpected file in userDir: $it" }
            isDir
        }

        val validDeviceDirs = if (deviceFilter != null) {
            allDeviceDirs.filter { deviceFilter.contains(DeviceId(it.name)) }
        } else {
            allDeviceDirs
        }

        val targetModules = moduleFilter?.let { supportedModuleIds.intersect(it) } ?: supportedModuleIds

        val deviceFetchJobs = validDeviceDirs.map { deviceDir ->
            scope.async(dispatcherProvider.IO) deviceFetch@{
                log(TAG, DEBUG) { "readDrive(): Reading module data for device: $deviceDir" }
                val moduleDirs = deviceDir.listFiles().filter { targetModules.contains(ModuleId(it.name)) }
                val moduleFetchJobs = moduleDirs.map { moduleFile ->
                    scope.async(dispatcherProvider.IO) moduleFetch@{
                        log(TAG, VERBOSE) { "readDrive(): Reading ${moduleFile.name} for ${deviceDir.name}" }
                        val payload = moduleFile.readData()

                        if (payload == null) {
                            log(TAG, WARN) { "readDrive(): Module file is empty: ${moduleFile.name}" }
                            return@moduleFetch null
                        }

                        val deviceId = DeviceId(deviceDir.name)
                        val moduleId = ModuleId(moduleFile.name)
                        val cacheKey = deviceId to moduleId
                        fileIdCache[cacheKey] = moduleFile.id
                        fileIdReverseCache[moduleFile.id] = cacheKey

                        GDriveModuleData(
                            connectorId = identifier,
                            deviceId = deviceId,
                            moduleId = moduleId,
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

    private suspend fun GDriveEnvironment.readDriveByFileIds(
        cachedIds: Map<Pair<DeviceId, ModuleId>, String>,
    ): GDriveData {
        log(TAG, DEBUG) { "readDriveByFileIds(): Fetching ${cachedIds.size} files directly by ID" }
        val start = System.currentTimeMillis()

        val fetchJobs = cachedIds.map { (key, fileId) ->
            val (deviceId, moduleId) = key
            scope.async(dispatcherProvider.IO) {
                val file = getFileMetadata(fileId)

                if (file.name != moduleId.id) {
                    log(TAG, WARN) { "readDriveByFileIds(): Name mismatch for $fileId: expected=${moduleId.id}, got=${file.name}" }
                    fileIdCache.remove(key)
                    fileIdReverseCache.remove(fileId)
                    return@async null
                }

                val payload = file.readData()
                if (payload == null) {
                    log(TAG, WARN) { "readDriveByFileIds(): Empty payload for $fileId" }
                    return@async null
                }

                GDriveModuleData(
                    connectorId = identifier,
                    deviceId = deviceId,
                    moduleId = moduleId,
                    modifiedAt = Instant.ofEpochMilli(file.modifiedTime.value),
                    payload = payload,
                )
            }
        }

        val modules = fetchJobs.awaitAll().filterNotNull()
        log(TAG) { "readDriveByFileIds() took ${System.currentTimeMillis() - start}ms" }

        val deviceGroups = modules.groupBy { it.deviceId }
        return GDriveData(
            connectorId = identifier,
            devices = deviceGroups.map { (deviceId, mods) ->
                GDriveDeviceData(deviceId = deviceId, modules = mods)
            },
        )
    }

    internal sealed interface SyncChangeResult {
        data class HasChanges(val newToken: String) : SyncChangeResult
        data object NoChanges : SyncChangeResult
        data object ForceFullSync : SyncChangeResult
    }

    private suspend fun GDriveEnvironment.checkSyncChanges(): SyncChangeResult {
        val token = syncToken.value()
        if (token == null) {
            val startToken = drive.changes().getStartPageToken()
                .setSupportsAllDrives(false)
                .execute().startPageToken
            syncToken.value(startToken)
            log(TAG) { "checkSyncChanges(): No token, initialized to $startToken, forcing full sync" }
            return SyncChangeResult.ForceFullSync
        }

        return try {
            val allChanges = mutableListOf<com.google.api.services.drive.model.Change>()
            var pageToken: String? = token
            var finalToken: String? = null

            while (pageToken != null) {
                val changeList = drive.changes().list(pageToken)
                    .setSpaces(APPDATAFOLDER)
                    .setFields("nextPageToken,newStartPageToken,changes(fileId)")
                    .execute()
                allChanges.addAll(changeList.changes ?: emptyList())
                finalToken = changeList.newStartPageToken
                pageToken = changeList.nextPageToken
            }

            if (allChanges.isNotEmpty()) {
                val newToken = finalToken ?: token
                log(TAG) { "checkSyncChanges(): ${allChanges.size} changes, newToken=$newToken" }
                SyncChangeResult.HasChanges(newToken)
            } else {
                finalToken?.let { syncToken.value(it) }
                log(TAG) { "checkSyncChanges(): No changes" }
                SyncChangeResult.NoChanges
            }
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            if (e.statusCode == 410) {
                log(TAG, WARN) { "checkSyncChanges(): Token invalidated, resetting" }
                clearFileIdCache()
                syncToken.value(null)
            }
            SyncChangeResult.ForceFullSync
        }
    }

    internal fun mergeData(
        existing: SyncRead,
        update: SyncRead,
        moduleFilter: Set<ModuleId>?,
    ): GDriveData {
        val updatedDeviceMap = update.devices.associateBy { it.deviceId }
        val existingDeviceIds = existing.devices.map { it.deviceId }.toSet()

        val mergedDevices = existing.devices.map { existingDevice ->
            val updatedDevice = updatedDeviceMap[existingDevice.deviceId]
            if (updatedDevice == null) {
                existingDevice
            } else {
                val keptModules = if (moduleFilter != null) {
                    existingDevice.modules.filter { it.moduleId !in moduleFilter }
                } else {
                    emptyList()
                }
                GDriveDeviceData(
                    deviceId = existingDevice.deviceId,
                    modules = keptModules + updatedDevice.modules,
                )
            }
        }

        val newDevices = update.devices
            .filter { it.deviceId !in existingDeviceIds }
            .map { GDriveDeviceData(deviceId = it.deviceId, modules = it.modules.toList()) }

        return GDriveData(connectorId = existing.connectorId, devices = mergedDevices + newDevices)
    }

    private fun clearFileIdCache() {
        fileIdCache.clear()
        fileIdReverseCache.clear()
    }

    private fun evictFileIdCache(deviceId: DeviceId) {
        fileIdCache.keys.filter { it.first == deviceId }.forEach { key ->
            fileIdCache.remove(key)?.let { fileIdReverseCache.remove(it) }
        }
    }

    private suspend fun GDriveEnvironment.readTargeted(
        options: SyncOptions,
        existing: SyncRead,
    ): GDriveData {
        val mFilter = options.moduleFilter
        val dFilter = options.deviceFilter

        if (mFilter != null && dFilter != null) {
            val targetModules = mFilter.intersect(supportedModuleIds)
            val requested = dFilter.flatMap { deviceId ->
                targetModules.map { moduleId -> deviceId to moduleId }
            }.toSet()
            val cached = requested.mapNotNull { key -> fileIdCache[key]?.let { key to it } }.toMap()

            if (cached.size == requested.size) {
                log(TAG) { "readTargeted(): All ${cached.size} fileIds cached, using direct fetch" }
                try {
                    return readDriveByFileIds(cached)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "readTargeted(): Direct fetch failed, falling back: ${e.asLog()}" }
                }
            } else {
                log(TAG) { "readTargeted(): Cache miss (${cached.size}/${requested.size}), using readDrive" }
            }
        }

        return readDrive(mFilter, dFilter)
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

        try {
            runDriveAction("sync-sync") {
                val jobs = mutableSetOf<Deferred<*>>()


                scope.async(dispatcherProvider.IO) {
                    if (!options.stats) return@async

                    try {
                        val deviceDirs = appDataRoot.child(DEVICE_DATA_DIR_NAME)
                            ?.listFiles()
                            ?.filter { it.isDirectory }
                            ?: emptyList()

                        val metadata = deviceDirs.map { dir ->
                            val deviceId = DeviceId(id = dir.name)
                            val info = try {
                                dir.child(DEVICE_INFO_FILE)?.readData()?.let {
                                    json.decodeFromString<GDriveDeviceInfo>(it.utf8())
                                }
                            } catch (e: Exception) {
                                log(TAG, WARN) { "sync(): Failed to read $DEVICE_INFO_FILE for ${dir.name}: ${e.message}" }
                                null
                            }
                            val lastSeen = dir.listFiles()
                                .filter { it.name != DEVICE_INFO_FILE && !it.isDirectory }
                                .maxOfOrNull { Instant.ofEpochMilli(it.modifiedTime.value) }
                            DeviceMetadata(
                                deviceId = deviceId,
                                version = info?.version,
                                platform = info?.platform,
                                label = info?.label,
                                lastSeen = lastSeen,
                            )
                        }

                        _state.updateBlocking { copy(deviceMetadata = metadata) }
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


                if (options.writeData && options.writePayload.isNotEmpty()) {
                    log(TAG) { "sync(): Writing ${options.writePayload.size} cached modules (batched)" }
                    writeDrive(SyncWriteContainer(
                        deviceId = syncSettings.deviceId,
                        modules = options.writePayload,
                    ))
                }


                scope.async(dispatcherProvider.IO) {
                    if (!options.readData) return@async

                    try {
                        val isTargeted = options.moduleFilter != null || options.deviceFilter != null

                        if (isTargeted) {
                            val existing = _data.value
                            if (existing == null) {
                                log(TAG) { "sync(): First sync, forcing full read despite filters" }
                                _data.value = readDrive()
                                val startToken = drive.changes().getStartPageToken()
                                    .setSupportsAllDrives(false)
                                    .execute().startPageToken
                                syncToken.value(startToken)
                            } else {
                                val newData = readTargeted(options, existing)
                                _data.value = mergeData(existing, newData, options.moduleFilter)
                            }
                        } else {
                            when (val result = checkSyncChanges()) {
                                is SyncChangeResult.HasChanges -> {
                                    _data.value = readDrive()
                                    syncToken.value(result.newToken)
                                }

                                is SyncChangeResult.ForceFullSync -> {
                                    _data.value = readDrive()
                                    val startToken = drive.changes().getStartPageToken()
                                        .setSupportsAllDrives(false)
                                        .execute().startPageToken
                                    syncToken.value(startToken)
                                }

                                is SyncChangeResult.NoChanges -> {
                                    log(TAG) { "sync(): No changes detected, skipping readDrive()" }
                                }
                            }
                        }
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
        } finally {
            syncLock.withLock {
                isSyncing = false
                val stop = System.currentTimeMillis()
                log(TAG, VERBOSE) { "sync(): Sync done, releasing (took ${stop - start}ms)" }
            }
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

        // Write device info metadata
        try {
            val deviceInfo = GDriveDeviceInfo(
                version = BuildConfigWrap.VERSION_NAME,
                platform = "android",
                label = syncSettings.deviceLabel.value(),
            )
            val infoPayload = json.encodeToString(deviceInfo).encodeToByteArray().toByteString()
            val infoFile = deviceDir.child(DEVICE_INFO_FILE) ?: deviceDir.createFile(DEVICE_INFO_FILE).also {
                log(TAG, VERBOSE) { "writeDrive(): Created device info file $it" }
            }
            infoFile.writeData(infoPayload)
        } catch (e: Exception) {
            log(TAG, WARN) { "writeDrive(): Failed to write $DEVICE_INFO_FILE: ${e.message}" }
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
                _state.updateBlocking {
                    copy(
                        lastError = null,
                        lastActionAt = Instant.now(),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "runDriveAction($tag) failed: ${e.asLog()}" }
            _state.updateBlocking { copy(lastError = e) }
            throw e
        } finally {
            _state.updateBlocking {
                log(TAG, VERBOSE) { "runDriveAction($tag) finished" }
                copy(activeActions = activeActions - 1)
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
        private const val DEVICE_INFO_FILE = "_device.json"
        private const val POLL_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes
        private val TAG = logTag("Sync", "GDrive", "Connector")
    }
}
