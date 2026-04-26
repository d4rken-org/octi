package eu.darken.octi.syncs.octiserver.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.common.collections.toGzip
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
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorCapabilities
import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorOperation
import eu.darken.octi.sync.core.ConnectorProcessor
import eu.darken.octi.sync.core.ConnectorSyncState
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceRemovalPolicy
import eu.darken.octi.sync.core.OperationId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncEvent
import eu.darken.octi.sync.core.SyncConnector.EventMode
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.CommonIssue
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import eu.darken.octi.sync.core.SyncWriteContainer
import eu.darken.octi.sync.core.encryption.EncryptionMode
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource


@Suppress("BlockingMethodInNonBlockingContext")
class OctiServerConnector @AssistedInject constructor(
    @Assisted val credentials: OctiServer.Credentials,
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val endpointFactory: OctiServerEndpoint.Factory,
    private val blobStoreHub: OctiServerBlobStoreHub,
    private val networkStateProvider: NetworkStateProvider,
    private val syncSettings: SyncSettings,
    private val syncState: ConnectorSyncState,
    private val supportedModuleIds: Set<@JvmSuppressWildcards ModuleId>,
    private val baseHttpClient: OkHttpClient,
    private val json: Json,
) : SyncConnector {

    private val endpoint by lazy {
        endpointFactory.create(credentials.serverAdress).also {
            it.setCredentials(credentials)
        }
    }

    private val crypti by lazy { PayloadEncryption(credentials.encryptionKeyset) }

    private fun buildAssociatedData(deviceId: DeviceId, moduleId: ModuleId): ByteArray =
        "${deviceId.id}:${moduleId.id}".toByteArray()

    data class State(
        override val lastActionAt: Instant? = null,
        override val lastError: Exception? = null,
        override val quota: SyncConnectorState.Quota? = null,
        override val isAvailable: Boolean = true,
        override val clockOffsets: List<SyncConnectorState.ClockOffset> = emptyList(),
        override val issues: List<ConnectorIssue> = emptyList(),
        override val deviceMetadata: List<DeviceMetadata> = emptyList(),
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

    /** Set when [writeServer] sees HTTP 507 with `X-Octi-Reason: server_disk_low` from any
     *  commit attempt; cleared on the next successful commit. Surfaces in [_state.issues] via
     *  [computeIssues]. Blob-side rejections are handled separately by [BlobManager]. */
    private val _commitServerStorageLow = MutableStateFlow(false)
    private val _commitAccountQuotaFull = MutableStateFlow(false)

    // TODO: Consider removing lock — concurrent syncs may be safe since each module is an independent endpoint
    private val serverLock = Mutex()

    override val accountLabel: String get() = credentials.serverAdress.domain

    override val capabilities: ConnectorCapabilities = ConnectorCapabilities(
        deviceRemovalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE,
    )

    override val identifier: ConnectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = credentials.serverAdress.domain,
        account = credentials.accountId.id,
    )

    private val processor = ConnectorProcessor(
        connectorId = identifier,
        syncSettings = syncSettings,
    ) { command -> executeCommand(command) }

    override val operations: StateFlow<List<ConnectorOperation>> get() = processor.operations
    override val completions: SharedFlow<ConnectorOperation.Terminal> get() = processor.completions
    override fun submit(command: ConnectorCommand): OperationId = processor.submit(command)
    override suspend fun await(id: OperationId): ConnectorOperation.Terminal = processor.await(id)
    override fun dismiss(id: OperationId) = processor.dismiss(id)

    /** Start the processor loop. Called by the hub after construction with a connector-lifetime scope. */
    fun start(processorScope: CoroutineScope): Job = processor.start(processorScope)

    /**
     * In-memory (deviceId, moduleId) → ETag cache populated on successful commits. Callers can
     * skip a HEAD lookup before the next commit to the same module. Invalidated on 412 (server
     * saw a different ETag than we had) and cleared when the connector is deleted.
     *
     * Not persisted — process death forces a fresh HEAD on the next commit, which is correct.
     */
    private val etagCache = ConcurrentHashMap<Pair<DeviceId, ModuleId>, OctiServerEndpoint.ModuleEtagResult>()

    private suspend fun resolveCachedEtag(deviceId: DeviceId, moduleId: ModuleId): String? {
        etagCache[deviceId to moduleId]?.let {
            return (it as? OctiServerEndpoint.ModuleEtagResult.Present)?.etag
        }
        return try {
            fetchEtagFromServer(deviceId, moduleId)
        } catch (e: Exception) {
            log(TAG, WARN) { "resolveCachedEtag(): Failed for ${moduleId.logLabel}: ${e.message}" }
            null
        }
    }

    private suspend fun fetchEtagFromServer(deviceId: DeviceId, moduleId: ModuleId): String? {
        val result = endpoint.readModuleEtag(deviceId, moduleId)
        etagCache[deviceId to moduleId] = result
        return when (result) {
            is OctiServerEndpoint.ModuleEtagResult.Absent -> null
            is OctiServerEndpoint.ModuleEtagResult.Present -> result.etag
        }
    }

    private fun cacheEtag(deviceId: DeviceId, moduleId: ModuleId, etag: String) {
        if (etag.isEmpty()) {
            etagCache.remove(deviceId to moduleId)
            return
        }
        etagCache[deviceId to moduleId] = OctiServerEndpoint.ModuleEtagResult.Present(etag)
    }

    private val _syncEventMode = MutableStateFlow(EventMode.NONE)
    override val syncEventMode: StateFlow<EventMode> = _syncEventMode.asStateFlow()

    override val syncEvents: Flow<SyncEvent> = networkStateProvider.networkState
        .map { it.isInternetAvailable }
        .distinctUntilChanged()
        .flatMapLatest { online ->
            if (online) {
                log(TAG, INFO) { "Network available, connecting WebSocket" }
                OctiServerWebSocket(
                    credentials = credentials,
                    connectorId = identifier,
                    syncSettings = syncSettings,
                    baseHttpClient = baseHttpClient,
                    json = json,
                    onConnectionChanged = { connected ->
                        _syncEventMode.value = if (connected) EventMode.LIVE else EventMode.NONE
                    },
                ).connect()
            } else {
                log(TAG, INFO) { "Network lost, WebSocket inactive" }
                _syncEventMode.value = EventMode.NONE
                emptyFlow()
            }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    private suspend fun isInternetAvailable() = networkStateProvider.networkState.first().isInternetAvailable

    private suspend fun executeCommand(command: ConnectorCommand) {
        when (command) {
            is ConnectorCommand.Sync -> handleSync(command.options)
            is ConnectorCommand.DeleteDevice -> handleDeleteDevice(command.deviceId)
            ConnectorCommand.Reset -> handleReset()
            ConnectorCommand.Pause -> syncSettings.pausedConnectors.update { it + identifier }
            ConnectorCommand.Resume -> syncSettings.pausedConnectors.update { it - identifier }
        }
    }

    private suspend fun handleReset() {
        log(TAG, INFO) { "handleReset()" }
        runServerAction("reset-devices") {
            endpoint.resetDevices()
            etagCache.clear()
        }
        syncState.clearConnector(identifier)
    }

    private suspend fun handleDeleteDevice(deviceId: DeviceId) {
        log(TAG, INFO) { "handleDeleteDevice(deviceId=$deviceId)" }
        runServerAction("delete-device-$deviceId") {
            endpoint.deleteDevice(deviceId)
            etagCache.keys.removeAll { it.first == deviceId }
        }
        // Eager prune: survives VM teardown and is visible immediately to all observers.
        _state.updateBlocking {
            copy(deviceMetadata = deviceMetadata.filterNot { it.deviceId == deviceId })
        }
        _data.value = _data.value?.let { read ->
            OctiServerData(
                connectorId = read.connectorId,
                devices = read.devices.filterNot { it.deviceId == deviceId },
            )
        }
    }

    suspend fun createLinkCode(): LinkingData {
        log(TAG) { "createLinkCode()" }
        val linkCode = endpoint.createLinkCode()

        return LinkingData(
            serverAdress = credentials.serverAdress,
            linkCode = linkCode,
            encryptionKeyset = credentials.encryptionKeyset,
        )
    }

    private suspend fun handleSync(options: SyncOptions) {
        log(TAG) { "handleSync(${options.logLabel})" }

        if (!isInternetAvailable()) {
            log(TAG, WARN) { "handleSync(): Skipping, we are offline." }
            return
        }

        if (options.stats) {
            try {
                val linked = runServerAction("read-devicelist") {
                    endpoint.listDevices()
                }
                val metadata = linked.map {
                    DeviceMetadata(
                        deviceId = it.deviceId,
                        version = it.version,
                        platform = it.platform,
                        label = it.label,
                        lastSeen = it.lastSeen,
                        addedAt = it.addedAt,
                    )
                }
                _state.updateBlocking { copy(deviceMetadata = metadata) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (handleDeviceUnknown(e, "list known devices")) return
                log(TAG, ERROR) { "Failed to list known devices: ${e.asLog()}" }
            }

            try {
                val storage = runServerAction("read-account-storage") {
                    endpoint.getAccountStorage()
                }
                val fetchedAt = Clock.System.now()
                _state.updateBlocking {
                    copy(
                        quota = SyncConnectorState.Quota(
                            updatedAt = fetchedAt,
                            storageUsed = storage.usedBytes,
                            storageTotal = storage.accountQuotaBytes,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Quota is supplemental — do not abort the sync via handleDeviceUnknown.
                log(TAG, ERROR) { "Failed to read account storage: ${e.asLog()}" }
            }
        }

        if (options.writeData && options.writePayload.isNotEmpty()) {
            log(TAG) { "handleSync(): Writing ${options.writePayload.size} cached modules" }
            options.writePayload.forEach { mw ->
                try {
                    runServerAction("write-cached-${mw.module.moduleId}") {
                        writeServer(SyncWriteContainer(deviceId = syncSettings.deviceId, modules = listOf(mw.module)))
                    }
                    // Succeeded — record hash for this specific module.
                    syncState.setHash(identifier, mw.module.moduleId, mw.expectedHash)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (handleDeviceUnknown(e, "write cached ${mw.module.moduleId}")) return
                    throw e
                }
            }
        }

        if (options.readData) {
            val moduleFilter = options.moduleFilter
            val deviceFilter = options.deviceFilter
            val isTargeted = moduleFilter != null || deviceFilter != null
            log(TAG) { "read(modules=${moduleFilter?.size}, devices=${deviceFilter?.size})" }
            try {
                runServerAction("read-server") {
                    val newData = readServer(moduleFilter, deviceFilter)
                    val existing = _data.value
                    _data.value = if (isTargeted && existing != null) {
                        mergeData(existing, newData, moduleFilter)
                    } else {
                        newData
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (handleDeviceUnknown(e, "read")) return
                log(TAG, ERROR) { "Failed to read: ${e.asLog()}" }
            }
        }

        computeIssues()
    }

    /**
     * Wrap a single commit-shaped endpoint call (`commitModule` or the legacy `writeModule`).
     * Translates HTTP 507 with a known `X-Octi-Reason` header into the corresponding
     * connector-level commit flag and re-throws so the existing error path runs unchanged.
     * On success, transitions any sticky flag from set→unset and refreshes [_state.issues].
     */
    private suspend fun commitWithReasonHandling(block: suspend () -> Unit) {
        try {
            block()
            // Use bitwise `or` so both compareAndSet calls always run.
            val cleared = _commitServerStorageLow.compareAndSet(true, false) or
                _commitAccountQuotaFull.compareAndSet(true, false)
            if (cleared) computeIssues()
        } catch (e: OctiServerHttpException) {
            if (e.httpCode == 507) {
                when (e.octiReason) {
                    OctiServerHttpException.REASON_SERVER_DISK_LOW -> {
                        log(TAG, WARN) { "commit: 507 server_disk_low" }
                        _commitServerStorageLow.value = true
                    }
                    OctiServerHttpException.REASON_ACCOUNT_QUOTA_EXCEEDED -> {
                        log(TAG, WARN) { "commit: 507 account_quota_exceeded" }
                        _commitAccountQuotaFull.value = true
                    }
                    else -> log(TAG, WARN) { "commit: 507 with no/unknown reason header (${e.octiReason})" }
                }
                computeIssues()
            }
            throw e
        }
    }

    private suspend fun computeIssues() {
        val currentState = _state.value()
        val metadata = currentState.deviceMetadata
        val data = _data.value
        val dataDeviceIds = data?.devices?.filter { it.modules.isNotEmpty() }?.map { it.deviceId }?.toSet() ?: emptySet()
        val encType = credentials.encryptionKeyset.type
        val isGcmSiv = EncryptionMode.fromTypeString(encType) == EncryptionMode.AES256_GCM_SIV

        val encIssues = if (!isGcmSiv) emptyList() else metadata.mapNotNull { device ->
            if (device.deviceId == syncSettings.deviceId) return@mapNotNull null
            if (device.deviceId in dataDeviceIds) return@mapNotNull null
            val addedAt = device.addedAt ?: return@mapNotNull null
            if ((Clock.System.now() - addedAt) < SyncSettings.FIRST_SYNC_GRACE_PERIOD) return@mapNotNull null
            OctiServerIssue.EncryptionIncompatible(
                connectorId = identifier,
                deviceId = device.deviceId,
                deviceLabel = device.label,
            )
        }

        val blobIssues = if (!isGcmSiv) {
            listOf(
                OctiServerIssue.BlobEncryptionUnsupported(
                    connectorId = identifier,
                    deviceId = syncSettings.deviceId,
                )
            )
        } else {
            emptyList()
        }

        val commitIssues = buildList<ConnectorIssue> {
            if (_commitServerStorageLow.value) {
                add(CommonIssue.ServerStorageLow(connectorId = identifier, deviceId = syncSettings.deviceId))
            }
            if (_commitAccountQuotaFull.value) {
                add(CommonIssue.AccountQuotaFull(connectorId = identifier, deviceId = syncSettings.deviceId))
            }
        }

        val issues = encIssues + blobIssues + commitIssues
        log(TAG) { "computeIssues(): ${issues.size} issues" }
        _state.updateBlocking { copy(issues = issues) }
    }

    private suspend fun fetchModule(deviceId: DeviceId, moduleId: ModuleId): OctiServerModuleData? {
        val readData = endpoint.readModule(deviceId = deviceId, moduleId = moduleId) ?: return null

        if (readData.serverTime != null) {
            val serverTime = readData.serverTime
            val offset = readData.localTime - serverTime
            log(TAG, VERBOSE) { "fetchModule(${deviceId.logLabel}:${moduleId.logLabel}): serverTime=$serverTime, offset=${offset.inWholeSeconds}s" }
            val clockOffset = SyncConnectorState.ClockOffset(offset = offset, measuredAt = readData.localTime)
            _state.updateBlocking { copy(clockOffsets = clockOffsets + clockOffset) }
        } else {
            log(TAG, VERBOSE) { "fetchModule(${deviceId.logLabel}:${moduleId.logLabel}): no serverTime in response" }
        }

        val payload = if (readData.payload != ByteString.EMPTY) {
            crypti.decrypt(readData.payload, buildAssociatedData(deviceId, moduleId)).fromGzip()
        } else {
            ByteString.EMPTY
        }

        return OctiServerModuleData(
            connectorId = identifier,
            deviceId = deviceId,
            moduleId = moduleId,
            modifiedAt = readData.modifiedAt,
            payload = payload,
        ).also { log(TAG, VERBOSE) { "readServer(): Module data: $it" } }
    }

    private suspend fun readServer(
        moduleFilter: Set<ModuleId>? = null,
        deviceFilter: Set<DeviceId>? = null,
    ): OctiServerData {
        val start = TimeSource.Monotonic.markNow()
        log(TAG, DEBUG) { "readServer(modules=${moduleFilter?.size}, devices=${deviceFilter?.size}): Starting..." }
        _state.updateBlocking { copy(clockOffsets = emptyList()) }
        val allLinkedDevices = endpoint.listDevices()
        val allDeviceIds = allLinkedDevices.map { it.deviceId }
        val deviceIds = if (deviceFilter != null) {
            allDeviceIds.filter { it in deviceFilter }
        } else {
            allDeviceIds
        }
        log(TAG, VERBOSE) { "readServer(): Found devices: $deviceIds (${allDeviceIds.size} total)" }

        val targetModuleIds = moduleFilter ?: supportedModuleIds
        val isTargeted = moduleFilter != null

        val devices = deviceIds.map { deviceId ->
            scope.async moduleFetch@{
                val moduleFetchJobs = targetModuleIds.map { moduleId ->
                    val fetchResult = try {
                        fetchModule(deviceId, moduleId)
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Failed to fetch: $deviceId:$moduleId:\n${e.asLog()}" }
                        null
                    }
                    log(TAG, VERBOSE) { "Module fetched: $fetchResult" }
                    if (!isTargeted) delay(1.seconds)
                    fetchResult
                }

                val modules = moduleFetchJobs.filterNotNull()

                OctiServerDeviceData(
                    deviceId = deviceId,
                    modules = modules,
                )
            }
        }.awaitAll()

        val result = OctiServerData(
            connectorId = identifier,
            devices = devices
        )
        log(TAG) { "readServer() took ${start.elapsedNow().inWholeMilliseconds}ms (${devices.size} devices)" }
        return result
    }

    private suspend fun writeServer(data: SyncWrite) {
        log(TAG, DEBUG) { "writeServer(): ${data.modules.size} modules" }

        // TODO cache write data for when we are online again?
        if (!isInternetAvailable()) {
            log(TAG, WARN) { "writeServer(): Skipping, we are offline." }
            return
        }

        data.modules.forEach { module ->
            val encryptedPayload = crypti.encrypt(
                module.payload.toGzip(),
                buildAssociatedData(data.deviceId, module.moduleId),
            )

            val attachments = module.blobs
            if (attachments != null) {
                val capabilities = blobStoreHub.getCapabilities(identifier)
                    ?: endpoint.resolveCapabilities().also { blobStoreHub.memoizeCapabilities(identifier, it) }

                when (capabilities.blobSupport) {
                    OctiServerCapabilities.BlobSupport.LEGACY -> {
                        log(TAG, WARN) { "writeServer(): Server is legacy, stripping blobs from ${module.moduleId}" }
                        commitWithReasonHandling {
                            endpoint.writeModule(moduleId = module.moduleId, payload = encryptedPayload)
                        }
                    }
                    OctiServerCapabilities.BlobSupport.SUPPORTED,
                    OctiServerCapabilities.BlobSupport.UNKNOWN -> {
                        val serverBlobIds = filterResidentBlobs(attachments, identifier.idString)
                        val missing = attachments.size - serverBlobIds.size
                        if (missing > 0) {
                            log(TAG) { "writeServer(): ${module.moduleId} — committing ${serverBlobIds.size}/${attachments.size} blobs ($missing not hosted here)" }
                        }

                        val etag = resolveCachedEtag(data.deviceId, module.moduleId)

                        try {
                            commitWithReasonHandling {
                                val newEtag = endpoint.commitModule(
                                    deviceId = data.deviceId,
                                    moduleId = module.moduleId,
                                    etag = etag,
                                    documentBase64 = encryptedPayload.base64(),
                                    serverBlobIds = serverBlobIds,
                                )
                                cacheEtag(data.deviceId, module.moduleId, newEtag)
                                log(TAG) { "writeServer(): Committed ${module.moduleId} via PUT (${serverBlobIds.size} blob refs)" }
                            }
                        } catch (e: OctiServerHttpException) {
                            when (e.httpCode) {
                                404, 405 -> {
                                    log(TAG, WARN) { "writeServer(): PUT commit rejected (${e.httpCode}), falling back to POST" }
                                    blobStoreHub.memoizeCapabilities(
                                        identifier,
                                        OctiServerCapabilities(blobSupport = OctiServerCapabilities.BlobSupport.LEGACY),
                                    )
                                    etagCache.remove(data.deviceId to module.moduleId)
                                    commitWithReasonHandling {
                                        endpoint.writeModule(moduleId = module.moduleId, payload = encryptedPayload)
                                    }
                                }
                                412 -> {
                                    log(TAG, WARN) { "writeServer(): 412 conflict on ${module.moduleId}, refreshing ETag and retrying" }
                                    etagCache.remove(data.deviceId to module.moduleId)
                                    val freshEtag = try {
                                        fetchEtagFromServer(data.deviceId, module.moduleId)
                                    } catch (readE: Exception) {
                                        log(TAG, ERROR) { "writeServer(): ETag refresh failed: ${readE.message}" }
                                        throw e
                                    }
                                    commitWithReasonHandling {
                                        val newEtag = endpoint.commitModule(
                                            deviceId = data.deviceId,
                                            moduleId = module.moduleId,
                                            etag = freshEtag,
                                            documentBase64 = encryptedPayload.base64(),
                                            serverBlobIds = serverBlobIds,
                                        )
                                        cacheEtag(data.deviceId, module.moduleId, newEtag)
                                        log(TAG) { "writeServer(): Retry succeeded for ${module.moduleId}" }
                                    }
                                }
                                else -> {
                                    etagCache.remove(data.deviceId to module.moduleId)
                                    throw e
                                }
                            }
                        }
                    }
                }
            } else {
                // Legacy module → POST (unchanged)
                commitWithReasonHandling {
                    endpoint.writeModule(
                        moduleId = module.moduleId,
                        payload = encryptedPayload,
                    )
                }
            }
        }
        log(TAG, VERBOSE) { "writeServer(): Done" }
    }

    private fun handleDeviceUnknown(e: Exception, operation: String): Boolean {
        if ((e as? OctiServerHttpException)?.isDeviceUnknown != true) return false
        log(TAG, WARN) { "$operation: device no longer registered, pausing connector" }
        // Route through the queue so pause is serialized with the rest of this connector's work.
        submit(ConnectorCommand.Pause)
        return true
    }

    private suspend fun <R> runServerAction(
        tag: String,
        block: suspend () -> R,
    ): R {
        val start = TimeSource.Monotonic.markNow()
        log(TAG, VERBOSE) { "runServerAction($tag)" }

        return try {
            serverLock.withLock {
                withContext(NonCancellable) { block() }
            }.also {
                _state.updateBlocking {
                    copy(
                        lastError = null,
                        lastActionAt = Clock.System.now(),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "runServerAction($tag) failed: ${e.asLog()}" }
            _state.updateBlocking { copy(lastError = e) }
            throw e
        } finally {
            log(TAG, VERBOSE) { "runServerAction($tag) finished after ${start.elapsedNow().inWholeMilliseconds}ms" }
        }
    }

    internal fun mergeData(
        existing: SyncRead,
        update: SyncRead,
        moduleFilter: Set<ModuleId>?,
    ): OctiServerData {
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
                OctiServerDeviceData(
                    deviceId = existingDevice.deviceId,
                    modules = keptModules + updatedDevice.modules,
                )
            }
        }

        val newDevices = update.devices
            .filter { it.deviceId !in existingDeviceIds }
            .map { OctiServerDeviceData(deviceId = it.deviceId, modules = it.modules.toList()) }

        return OctiServerData(connectorId = existing.connectorId, devices = mergedDevices + newDevices)
    }

    @AssistedFactory
    interface Factory {
        fun create(account: OctiServer.Credentials): OctiServerConnector
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "Connector")
    }
}

/**
 * Residency filter: picks the server-scoped blob IDs this connector should commit.
 *
 * Only commit blobs this connector actually hosts — a blob mirrored only to GDrive
 * (absent from `connectorRefs[connectorIdString]`) is silently omitted from this commit's
 * `blobRefs`; the module metadata still syncs.
 *
 * When [SyncWrite.BlobAttachment.availableOn] is populated, also filter out refs whose
 * connector has been removed from `availableOn` — defense-in-depth against stale
 * `connectorRefs` entries where the two fields drifted out of sync. Empty `availableOn`
 * means "no filter" (legacy attachments from producers that don't populate it).
 */
internal fun filterResidentBlobs(
    attachments: List<SyncWrite.BlobAttachment>,
    connectorIdString: String,
): List<String> = attachments.mapNotNull { att ->
    if (att.availableOn.isNotEmpty() && connectorIdString !in att.availableOn) return@mapNotNull null
    att.connectorRefs[connectorIdString]?.value
}
