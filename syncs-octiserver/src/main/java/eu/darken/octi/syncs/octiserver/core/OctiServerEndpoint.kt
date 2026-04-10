package eu.darken.octi.syncs.octiserver.core

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.RetrofitJson
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobFileTooLargeException
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.BlobQuotaExceededException
import eu.darken.octi.sync.core.blob.BlobStoreConstraints
import eu.darken.octi.sync.core.blob.BlobStoreQuota
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import retrofit2.HttpException
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

    suspend fun writeModule(moduleId: ModuleId, payload: ByteString) = withContext(dispatcherProvider.IO) {
        log(TAG) { "writeModule(moduleId=$moduleId, payload=$payload)" }
        try {
            api.writeModule(
                deviceId = ourDeviceIdString,
                moduleId = moduleId.id,
                targetDeviceId = ourDeviceIdString,
                payload = payload.toRequestBody(),
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    // --- Blob operations ---

    suspend fun putBlob(
        connectorId: ConnectorId,
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
        body: RequestBody,
        sizeBytes: Long,
        checksum: String,
    ) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "putBlob(key=${blobKey.id}, device=${deviceId.logLabel}, module=${moduleId.logLabel})" }
        try {
            api.putBlob(
                blobKey = blobKey.id,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
                moduleId = moduleId.id,
                sizeBytes = sizeBytes,
                checksum = checksum,
                body = body,
            )
        } catch (e: HttpException) {
            when (e.code()) {
                413 -> throw BlobFileTooLargeException(
                    connectorId = connectorId,
                    constraints = BlobStoreConstraints(maxFileBytes = sizeBytes),
                    requestedBytes = sizeBytes,
                )

                507 -> throw BlobQuotaExceededException(
                    quota = BlobStoreQuota(connectorId = connectorId, usedBytes = 0, totalBytes = 0),
                    requestedBytes = sizeBytes,
                )

                else -> throw OctiServerHttpException(e)
            }
        }
    }

    suspend fun getBlob(
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
    ): okhttp3.ResponseBody? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "getBlob(key=${blobKey.id}, device=${deviceId.logLabel}, module=${moduleId.logLabel})" }
        try {
            val response = api.getBlob(
                blobKey = blobKey.id,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
                moduleId = moduleId.id,
            )
            if (!response.isSuccessful) throw OctiServerHttpException(HttpException(response))
            response.body()
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    suspend fun deleteBlob(
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
    ) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "deleteBlob(key=${blobKey.id})" }
        try {
            api.deleteBlob(
                blobKey = blobKey.id,
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
                moduleId = moduleId.id,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    suspend fun listBlobs(
        deviceId: DeviceId,
        moduleId: ModuleId,
    ): List<OctiServerApi.BlobListResponse.BlobEntry> = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "listBlobs(device=${deviceId.logLabel}, module=${moduleId.logLabel})" }
        try {
            api.listBlobs(
                callerDeviceId = ourDeviceIdString,
                targetDeviceId = deviceId.id,
                moduleId = moduleId.id,
            ).blobs
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
        }
    }

    suspend fun getBlobQuota(): BlobStoreQuota? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "getBlobQuota()" }
        try {
            val response = api.getBlobQuota(callerDeviceId = ourDeviceIdString)
            BlobStoreQuota(
                connectorId = ConnectorId(
                    type = eu.darken.octi.sync.core.ConnectorType.OCTISERVER,
                    subtype = credentials?.serverAdress?.domain ?: "unknown",
                    account = credentials?.accountId?.id ?: "unknown",
                ),
                usedBytes = response.usedBytes,
                totalBytes = response.totalBytes,
            )
        } catch (e: HttpException) {
            throw OctiServerHttpException(e)
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
