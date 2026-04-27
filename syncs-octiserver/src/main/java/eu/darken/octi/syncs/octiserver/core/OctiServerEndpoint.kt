package eu.darken.octi.syncs.octiserver.core

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.RetrofitJson
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import kotlin.time.Clock
import kotlin.time.Instant

class OctiServerEndpoint @AssistedInject constructor(
    @Assisted private val serverAdress: OctiServer.Address,
    private val dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val baseHttpClient: OkHttpClient,
    @RetrofitJson private val retrofitJson: Json,
    private val basicAuthInterceptor: BasicAuthInterceptor,
    private val deviceHeaderInterceptor: DeviceHeaderInterceptor,
) {

    private val httpClient by lazy {
        baseHttpClient.newBuilder().apply {
            addInterceptor(basicAuthInterceptor)
            addInterceptor(deviceHeaderInterceptor)
        }.build()
    }

    private val api: OctiServerApi by lazy {
        Retrofit.Builder().apply {
            baseUrl("${serverAdress.address}/v1/")
            client(httpClient)
            addConverterFactory(retrofitJson.asConverterFactory("application/json".toMediaType()))
        }.build().create(OctiServerApi::class.java)
    }

    private val ourDeviceIdString: String
        get() = syncSettings.deviceId.id

    private var credentials: OctiServer.Credentials? = null
    fun setCredentials(credentials: OctiServer.Credentials?) {
        log(TAG) { "setCredentials(server=${credentials?.serverAdress?.domain}, account=${credentials?.accountId})" }
        basicAuthInterceptor.setCredentials(credentials)
        this.credentials = credentials
    }

    suspend fun createNewAccount(
        useLegacyEncryption: Boolean = false,
    ): OctiServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "createNewAccount(useLegacyEncryption=$useLegacyEncryption)" }
        val response = try {
            api.register(deviceID = ourDeviceIdString)
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }

        OctiServer.Credentials(
            createdAt = Clock.System.now(),
            serverAdress = serverAdress,
            accountId = OctiServer.Credentials.AccountId(response.accountID),
            devicePassword = OctiServer.Credentials.DevicePassword(response.password),
            encryptionKeyset = PayloadEncryption(useLegacyEncryption = useLegacyEncryption).exportKeyset()
        )
    }

    data class LinkedAccount(
        val accountId: OctiServer.Credentials.AccountId,
        val devicePassword: OctiServer.Credentials.DevicePassword,
    )

    suspend fun linkToExistingAccount(
        linkCode: OctiServer.Credentials.LinkCode,
    ): LinkedAccount = withContext(dispatcherProvider.IO) {
        log(TAG) { "linkToExistingAccount(linkCode=$linkCode)" }
        val response = try {
            api.register(
                deviceID = ourDeviceIdString,
                shareCode = linkCode.code,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }

        LinkedAccount(
            accountId = OctiServer.Credentials.AccountId(response.accountID),
            devicePassword = OctiServer.Credentials.DevicePassword(response.password),
        )
    }

    suspend fun createLinkCode(): OctiServer.Credentials.LinkCode = withContext(dispatcherProvider.IO) {
        log(TAG) { "createLinkCode(account=$credentials)" }
        val response = try {
            api.createShareCode(deviceID = ourDeviceIdString)
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
        return@withContext OctiServer.Credentials.LinkCode(code = response.shareCode)
    }

    data class LinkedDevice(
        val deviceId: DeviceId,
        val version: String?,
        val platform: String?,
        val label: String?,
        val addedAt: Instant?,
        val lastSeen: Instant?,
    )

    suspend fun listDevices(): Collection<LinkedDevice> = withContext(dispatcherProvider.IO) {
        log(TAG) { "listDevices()" }
        val response = try {
            api.getDeviceList(
                deviceID = ourDeviceIdString,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
        response.devices.map {
            LinkedDevice(
                deviceId = DeviceId(it.id),
                version = it.version,
                platform = it.platform,
                label = it.label,
                addedAt = it.addedAt,
                lastSeen = it.lastSeen,
            )
        }
    }

    suspend fun resetDevices(deviceIds: Set<DeviceId> = emptySet()): Unit = withContext(dispatcherProvider.IO) {
        log(TAG) { "resetDevices(deviceIds=$deviceIds)" }
        try {
            api.resetDevices(
                deviceId = ourDeviceIdString,
                targets = OctiServerApi.ResetRequest(targets = deviceIds)
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    suspend fun deleteDevice(deviceId: DeviceId): Unit = withContext(dispatcherProvider.IO) {
        log(TAG) { "deleteDevice($deviceId)" }
        try {
            api.deleteDevice(
                callerDeviceId = ourDeviceIdString,
                target = deviceId.id,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    data class ReadData(
        val modifiedAt: Instant,
        val payload: ByteString,
        val serverTime: Instant? = null,
        val localTime: Instant,
    )

    suspend fun readModule(deviceId: DeviceId, moduleId: ModuleId): ReadData? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "readModule(deviceId=${deviceId.logLabel}, moduleId=${moduleId.logLabel})" }
        val response = try {
            api.readModule(
                callerDeviceId = ourDeviceIdString,
                moduleId = moduleId.id,
                targetDeviceId = deviceId.id,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }

        if (!response.isSuccessful) throw OctiServerHttpException(HttpException(response))

        val localTime = Clock.System.now()

        val lastModifiedAt = response.headers()["X-Modified-At"]
            ?.parseRfc1123ToInstant()
            ?: return@withContext null

        val serverTime = response.headers()["Date"]
            ?.parseRfc1123ToInstant()

        val body = response.body()?.byteString()?.takeIf { it != NULL_BODY } ?: ByteString.EMPTY

        ReadData(
            modifiedAt = lastModifiedAt,
            payload = body,
            serverTime = serverTime,
            localTime = localTime,
        )
    }

    suspend fun writeModule(
        deviceId: DeviceId,
        moduleId: ModuleId,
        payload: ByteString,
    ) = withContext(dispatcherProvider.IO) {
        log(TAG) { "writeModule(device=${deviceId.logLabel}, moduleId=$moduleId, payload=$payload)" }
        try {
            api.writeModule(
                callerDeviceId = ourDeviceIdString,
                moduleId = moduleId.id,
                targetDeviceId = deviceId.id,
                payload = payload.toRequestBody(),
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    // --- Blob operations ---

    // --- Resumable upload sessions ---

    suspend fun createBlobSession(
        deviceId: DeviceId,
        moduleId: ModuleId,
        sizeBytes: Long,
        checksum: String?,
    ): OctiServerApi.CreateSessionResponse = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "createBlobSession(module=${moduleId.logLabel}, size=$sizeBytes)" }
        try {
            api.createBlobSession(
                moduleId = moduleId.id,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
                request = OctiServerApi.CreateSessionRequest(
                    sizeBytes = sizeBytes,
                    hashAlgorithm = if (checksum != null) "sha256" else null,
                    hashHex = checksum,
                ),
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    /**
     * Append chunk to upload session.
     * @return new Upload-Offset from the response header
     */
    suspend fun appendBlobSession(
        deviceId: DeviceId,
        moduleId: ModuleId,
        sessionId: String,
        offset: Long,
        body: RequestBody,
    ): Long = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "appendBlobSession(session=$sessionId, offset=$offset)" }
        val response = try {
            api.appendBlobSession(
                moduleId = moduleId.id,
                sessionId = sessionId,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
                offset = offset,
                body = body,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
        if (!response.isSuccessful) throw OctiServerHttpException(HttpException(response))
        response.headers()["Upload-Offset"]?.toLongOrNull()
            ?: throw IllegalStateException("Server did not return Upload-Offset header")
    }

    suspend fun finalizeBlobSession(
        deviceId: DeviceId,
        moduleId: ModuleId,
        sessionId: String,
        checksum: String,
    ): OctiServerApi.FinalizeSessionResponse = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "finalizeBlobSession(session=$sessionId)" }
        try {
            api.finalizeBlobSession(
                moduleId = moduleId.id,
                sessionId = sessionId,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
                request = OctiServerApi.FinalizeSessionRequest(
                    hashAlgorithm = "sha256",
                    hashHex = checksum,
                ),
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    suspend fun abortBlobSession(
        deviceId: DeviceId,
        moduleId: ModuleId,
        sessionId: String,
    ) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "abortBlobSession(session=$sessionId)" }
        try {
            api.abortBlobSession(
                moduleId = moduleId.id,
                sessionId = sessionId,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    // --- Blob download + list ---

    suspend fun getBlob(
        deviceId: DeviceId,
        moduleId: ModuleId,
        serverBlobId: String,
    ): okhttp3.ResponseBody? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "getBlob(blobId=$serverBlobId, device=${deviceId.logLabel}, module=${moduleId.logLabel})" }
        try {
            val response = api.getBlob(
                moduleId = moduleId.id,
                blobId = serverBlobId,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
            )
            if (!response.isSuccessful) throw OctiServerHttpException(HttpException(response))
            response.body()
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    suspend fun listBlobs(
        deviceId: DeviceId,
        moduleId: ModuleId,
    ): OctiServerApi.BlobListResponse = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "listBlobs(device=${deviceId.logLabel}, module=${moduleId.logLabel})" }
        try {
            api.listBlobs(
                moduleId = moduleId.id,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    /**
     * Delete a blob explicitly.
     *
     * The server requires an `If-Match` header with the current module ETag, since deleting
     * a blob mutates the module document. We read the ETag first and short-circuit as
     * idempotent success if the module doesn't exist. On a stale ETag (412), we refresh
     * once and retry.
     *
     * Returns normally on:
     * - 2xx (delete succeeded; response carries the new module ETag which is discarded —
     *   [OctiServerConnector]'s own 412-retry-on-commit path handles cache invalidation)
     * - 404 (blob already gone / module gone / cross-account target — idempotent)
     * - 405/501 (endpoint not implemented on older servers — falls back to implicit GC)
     *
     * Throws [OctiServerHttpException] on 400 (client-side If-Match bug), persistent 412
     * (2nd retry also stale — indicates concurrent-write churn), or other non-2xx codes.
     */
    suspend fun deleteBlob(
        deviceId: DeviceId,
        moduleId: ModuleId,
        serverBlobId: String,
    ): Unit = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "deleteBlob(blobId=$serverBlobId, device=${deviceId.logLabel}, module=${moduleId.logLabel})" }

        val initialEtag = when (val result = readModuleEtag(deviceId, moduleId)) {
            is ModuleEtagResult.Absent -> {
                log(TAG, VERBOSE) { "deleteBlob(): module absent, treating as idempotent no-op" }
                return@withContext
            }
            is ModuleEtagResult.Present -> result.etag
        }

        try {
            val firstResponse = callDeleteBlob(deviceId, moduleId, serverBlobId, initialEtag)
            if (firstResponse.code() == 412) {
                log(TAG, WARN) { "deleteBlob(): stale ETag, refreshing and retrying once" }
                val freshEtag = when (val refreshed = readModuleEtag(deviceId, moduleId)) {
                    is ModuleEtagResult.Absent -> return@withContext
                    is ModuleEtagResult.Present -> refreshed.etag
                }
                val retry = callDeleteBlob(deviceId, moduleId, serverBlobId, freshEtag)
                handleDeleteBlobResponse(retry, allowRetry = false)
            } else {
                handleDeleteBlobResponse(firstResponse, allowRetry = true)
            }
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    private suspend fun callDeleteBlob(
        deviceId: DeviceId,
        moduleId: ModuleId,
        serverBlobId: String,
        etag: String,
    ) = api.deleteBlob(
        moduleId = moduleId.id,
        blobId = serverBlobId,
        callerDeviceId = ourDeviceIdString,
        targetDeviceId = deviceId.id,
        ifMatch = etag,
    )

    private fun handleDeleteBlobResponse(response: Response<Unit>, allowRetry: Boolean) {
        when {
            response.isSuccessful -> {
                val newEtag = response.headers()["ETag"]
                log(TAG, VERBOSE) { "deleteBlob(): success (HTTP ${response.code()}, newEtag=$newEtag)" }
            }
            response.code() == 404 -> log(TAG, VERBOSE) { "deleteBlob(): 404 — idempotent no-op" }
            response.code() == 405 || response.code() == 501 -> {
                log(TAG, WARN) {
                    "deleteBlob(): server does not support explicit delete (HTTP ${response.code()}); " +
                        "falling back to implicit GC"
                }
            }
            response.code() == 400 -> {
                log(TAG, ERROR) { "deleteBlob(): server rejected If-Match as malformed (HTTP 400) — client bug" }
                throw OctiServerHttpException(HttpException(response))
            }
            response.code() == 412 && !allowRetry -> {
                log(TAG, ERROR) { "deleteBlob(): ETag still stale after refresh, giving up" }
                throw OctiServerHttpException(HttpException(response))
            }
            else -> throw OctiServerHttpException(HttpException(response))
        }
    }

    // --- Module commit ---

    /**
     * Commit a module with blob references using If-Match/If-None-Match preconditions.
     * @param etag raw ETag header from [readModuleEtag]; null = module doesn't exist (If-None-Match: *)
     * @return raw ETag header from response
     */
    suspend fun commitModule(
        deviceId: DeviceId,
        moduleId: ModuleId,
        etag: String?,
        documentBase64: String,
        serverBlobIds: List<String>,
    ): String = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "commitModule(module=${moduleId.logLabel}, etag=$etag, blobs=${serverBlobIds.size})" }
        val response = try {
            api.commitModule(
                moduleId = moduleId.id,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
                ifMatch = etag,
                ifNoneMatch = if (etag == null) "*" else null,
                request = OctiServerApi.ModuleCommitRequest(
                    documentBase64 = documentBase64,
                    blobRefs = serverBlobIds.map { OctiServerApi.ModuleCommitRequest.BlobRef(it) },
                ),
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
        if (!response.isSuccessful) throw OctiServerHttpException(HttpException(response))
        response.headers()["ETag"] ?: ""
    }

    sealed class ModuleEtagResult {
        data object Absent : ModuleEtagResult()
        data class Present(val etag: String) : ModuleEtagResult()
    }

    suspend fun readModuleEtag(
        deviceId: DeviceId,
        moduleId: ModuleId,
    ): ModuleEtagResult = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "readModuleEtag(device=${deviceId.logLabel}, module=${moduleId.logLabel})" }
        val response = try {
            api.readModule(
                moduleId = moduleId.id,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
        when {
            response.code() == 204 -> ModuleEtagResult.Absent
            response.isSuccessful -> {
                val etag = response.headers()["ETag"]
                if (etag != null) ModuleEtagResult.Present(etag) else ModuleEtagResult.Absent
            }
            else -> throw OctiServerHttpException(HttpException(response))
        }
    }

    // --- Account storage ---

    suspend fun getAccountStorage(): OctiServerApi.AccountStorageResponse = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "getAccountStorage()" }
        try {
            api.getAccountStorage(callerDeviceId = ourDeviceIdString)
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    suspend fun resolveCapabilities(): OctiServerCapabilities = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "resolveCapabilities()" }
        try {
            val storage = getAccountStorage()
            OctiServerCapabilities(
                blobSupport = if (storage.storageApiVersion >= 1) {
                    OctiServerCapabilities.BlobSupport.SUPPORTED
                } else {
                    OctiServerCapabilities.BlobSupport.LEGACY
                },
                storageApiVersion = storage.storageApiVersion,
            )
        } catch (e: OctiServerHttpException) {
            when (e.httpCode) {
                404, 405 -> OctiServerCapabilities(blobSupport = OctiServerCapabilities.BlobSupport.LEGACY)
                else -> OctiServerCapabilities(blobSupport = OctiServerCapabilities.BlobSupport.UNKNOWN)
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(account: OctiServer.Address): OctiServerEndpoint
    }

    companion object {
        private val NULL_BODY = "null".toByteString()
        private val TAG = logTag("Sync", "OctiServer", "Connector", "Endpoint")
    }
}
