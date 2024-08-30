package eu.darken.octi.syncs.kserver.core

import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class KServerEndpoint @AssistedInject constructor(
    @Assisted private val serverAdress: KServer.Address,
    private val dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val baseHttpClient: OkHttpClient,
    private val baseMoshi: Moshi,
    private val basicAuthInterceptor: BasicAuthInterceptor,
) {

    private val httpClient by lazy {
        baseHttpClient.newBuilder().apply {
            addInterceptor(basicAuthInterceptor)
        }.build()
    }

    private val api: KServerApi by lazy {
        Retrofit.Builder().apply {
            baseUrl(serverAdress.httpUrl)
            client(httpClient)
            addConverterFactory(MoshiConverterFactory.create(baseMoshi).asLenient())
        }.build().create(KServerApi::class.java)
    }

    private val ourDeviceIdString: String
        get() = syncSettings.deviceId.id

    private var credentials: KServer.Credentials? = null
    fun setCredentials(credentials: KServer.Credentials?) {
        log(TAG) { "setCredentials(credentials=$credentials)" }
        basicAuthInterceptor.setCredentials(credentials)
        this.credentials = credentials
    }

    suspend fun createNewAccount(): KServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "createNewAccount()" }
        val response = try {
            api.register(deviceID = ourDeviceIdString)
        } catch (e: HttpException) {
            throw KServerHttpException(e)
        }

        KServer.Credentials(
            createdAt = Instant.now(),
            serverAdress = serverAdress,
            accountId = KServer.Credentials.AccountId(response.accountID),
            devicePassword = KServer.Credentials.DevicePassword(response.password),
            encryptionKeyset = PayloadEncryption().exportKeyset()
        )
    }

    data class LinkedAccount(
        val accountId: KServer.Credentials.AccountId,
        val devicePassword: KServer.Credentials.DevicePassword,
    )

    suspend fun linkToExistingAccount(
        linkCode: KServer.Credentials.LinkCode,
    ): LinkedAccount = withContext(dispatcherProvider.IO) {
        log(TAG) { "linkToExistingAccount(linkCode=$linkCode)" }
        val response = try {
            api.register(
                deviceID = ourDeviceIdString,
                shareCode = linkCode.code,
            )
        } catch (e: HttpException) {
            throw KServerHttpException(e)
        }

        LinkedAccount(
            accountId = KServer.Credentials.AccountId(response.accountID),
            devicePassword = KServer.Credentials.DevicePassword(response.password),
        )
    }

    suspend fun createLinkCode(): KServer.Credentials.LinkCode = withContext(dispatcherProvider.IO) {
        log(TAG) { "createLinkCode(account=$credentials)" }
        val response = try {
            api.createShareCode(deviceID = ourDeviceIdString)
        } catch (e: HttpException) {
            throw KServerHttpException(e)
        }
        return@withContext KServer.Credentials.LinkCode(code = response.shareCode)
    }

    suspend fun listDevices(
        linkCode: KServer.Credentials.LinkCode? = null
    ): Collection<DeviceId> = withContext(dispatcherProvider.IO) {
        log(TAG) { "listDevices(linkCode=$linkCode)" }
        val response = try {
            api.getDeviceList(
                deviceID = ourDeviceIdString,
            )
        } catch (e: HttpException) {
            throw KServerHttpException(e)
        }
        response.devices.map { DeviceId(it.id) }
    }

    suspend fun resetDevices(deviceIds: Set<DeviceId> = emptySet()): Unit = withContext(dispatcherProvider.IO) {
        log(TAG) { "resetDevices(deviceIds=$deviceIds)" }
        try {
            api.resetDevices(
                deviceId = ourDeviceIdString,
                targets = KServerApi.ResetRequest(targets = deviceIds)
            )
        } catch (e: HttpException) {
            throw KServerHttpException(e)
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
            throw KServerHttpException(e)
        }
    }

    data class ReadData(
        val modifiedAt: Instant,
        val payload: ByteString,
    )

    suspend fun readModule(deviceId: DeviceId, moduleId: ModuleId): ReadData? = withContext(dispatcherProvider.IO) {
        log(TAG) { "readModule(deviceId=$deviceId, moduleId=$moduleId)" }
        val response = try {
            api.readModule(
                callerDeviceId = ourDeviceIdString,
                moduleId = moduleId.id,
                targetDeviceId = deviceId.id,
            )
        } catch (e: HttpException) {
            throw KServerHttpException(e)
        }

        if (!response.isSuccessful) throw HttpException(response)

        val lastModifiedAt = response.headers()["X-Modified-At"]
            ?.let { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME) }?.toInstant()
            ?: return@withContext null

        val body = response.body()?.byteString()?.takeIf { it != NULL_BODY } ?: ByteString.EMPTY

        ReadData(
            modifiedAt = lastModifiedAt,
            payload = body,
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
            throw KServerHttpException(e)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(account: KServer.Address): KServerEndpoint
    }

    companion object {
        private val NULL_BODY = "null".toByteString()
        private val TAG = logTag("Sync", "KServer", "Connector", "Endpoint")
    }
}

