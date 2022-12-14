package eu.darken.octi.syncs.jserver.core

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

class JServerEndpoint @AssistedInject constructor(
    @Assisted private val serverAdress: JServer.Address,
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

    private val api: JServerApi by lazy {
        Retrofit.Builder().apply {
            baseUrl(serverAdress.httpUrl)
            client(httpClient)
            addConverterFactory(MoshiConverterFactory.create(baseMoshi).asLenient())
        }.build().create(JServerApi::class.java)
    }

    private val ourDeviceIdString: String
        get() = syncSettings.deviceId.id

    private var credentials: JServer.Credentials? = null
    fun setCredentials(credentials: JServer.Credentials?) {
        log(TAG) { "setCredentials(credentials=$credentials)" }
        basicAuthInterceptor.setCredentials(credentials)
        this.credentials = credentials
    }

    suspend fun createNewAccount(): JServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "createNewAccount()" }
        val response = api.register(deviceID = ourDeviceIdString)

        JServer.Credentials(
            createdAt = Instant.now(),
            serverAdress = serverAdress,
            accountId = JServer.Credentials.AccountId(response.accountID),
            devicePassword = JServer.Credentials.DevicePassword(response.password),
            encryptionKeyset = PayloadEncryption().exportKeyset()
        )
    }

    data class LinkedAccount(
        val accountId: JServer.Credentials.AccountId,
        val devicePassword: JServer.Credentials.DevicePassword,
    )

    suspend fun linkToExistingAccount(
        linkCode: JServer.Credentials.LinkCode,
    ): LinkedAccount = withContext(dispatcherProvider.IO) {
        log(TAG) { "linkToExistingAccount(linkCode=$linkCode)" }
        val response = api.register(
            deviceID = ourDeviceIdString,
            shareCode = linkCode.code,
        )

        LinkedAccount(
            accountId = JServer.Credentials.AccountId(response.accountID),
            devicePassword = JServer.Credentials.DevicePassword(response.password),
        )
    }

    suspend fun createLinkCode(): JServer.Credentials.LinkCode = withContext(dispatcherProvider.IO) {
        log(TAG) { "createLinkCode(account=$credentials)" }
        val response = api.createShareCode(deviceID = ourDeviceIdString)
        return@withContext JServer.Credentials.LinkCode(code = response.shareCode)
    }

    suspend fun listDevices(linkCode: JServer.Credentials.LinkCode? = null): Collection<DeviceId> =
        withContext(dispatcherProvider.IO) {
            log(TAG) { "listDevices(linkCode=$linkCode)" }
            val response = api.getDeviceList(
                deviceID = ourDeviceIdString,
            )
            response.items.map { DeviceId(it.id) }
        }

    data class ReadData(
        val modifiedAt: Instant,
        val payload: ByteString,
    )

    suspend fun readModule(deviceId: DeviceId, moduleId: ModuleId): ReadData? = withContext(dispatcherProvider.IO) {
        log(TAG) { "readModule(deviceId=$deviceId, moduleId=$moduleId)" }
        val response = api.readModule(
            callerDeviceId = ourDeviceIdString,
            moduleId = moduleId.id,
            targetDeviceId = deviceId.id.takeIf { it != ourDeviceIdString }
        )

        if (!response.isSuccessful) throw HttpException(response)

        val lastModifiedAt = response.headers()["X-Modified-At"]?.let { Instant.parse(it) } ?: return@withContext null

        val body = response.body()?.byteString()?.takeIf { it != NULL_BODY } ?: ByteString.EMPTY

        ReadData(
            modifiedAt = lastModifiedAt,
            payload = body,
        )
    }

    suspend fun writeModule(moduleId: ModuleId, payload: ByteString) = withContext(dispatcherProvider.IO) {
        log(TAG) { "writeModule(moduleId=$moduleId, payload=$payload)" }
        api.writeModule(
            deviceId = ourDeviceIdString,
            moduleId = moduleId.id,
            payload = payload.toRequestBody(),
        )
    }

    suspend fun deleteModules(deviceId: DeviceId) = withContext(dispatcherProvider.IO) {
        api.deleteModules(
            callerDeviceId = ourDeviceIdString,
            targetDeviceId = deviceId.id.takeIf { it != ourDeviceIdString }
        )
    }

    suspend fun getHealth(): JServerApi.Health = withContext(dispatcherProvider.IO) {
        log(TAG) { "getHealth()" }
        api.getHealth()
    }

    @AssistedFactory
    interface Factory {
        fun create(account: JServer.Address): JServerEndpoint
    }

    companion object {
        private val NULL_BODY = "null".toByteString()
        private val TAG = logTag("Sync", "JServer", "Connector", "Endpoint")
    }
}

