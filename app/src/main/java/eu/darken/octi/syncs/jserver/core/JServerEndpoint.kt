package eu.darken.octi.syncs.jserver.core

import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.ModuleId
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.ByteString
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant

class JServerEndpoint @AssistedInject constructor(
    @Assisted private val serverAdress: JServer.Address,
    private val dispatcherProvider: DispatcherProvider,
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

    private var credentials: JServer.Credentials? = null
    fun setCredentials(credentials: JServer.Credentials?) {
        log(TAG) { "setCredentials(credentials=$credentials)" }
        basicAuthInterceptor.setCredentials(credentials)
        this.credentials = credentials
    }

    suspend fun createAccount(
        deviceId: DeviceId,
    ): JServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "createAccount(deviceId=$deviceId)" }
        val response = api.register(deviceID = deviceId.id)
        require(deviceId.id == response.deviceID)
        JServer.Credentials(
            createdAt = Instant.now(),
            serverAdress = serverAdress,
            accountId = JServer.Credentials.AccountId(response.accountID),
            deviceId = deviceId,
            devicePassword = JServer.Credentials.DevicePassword(response.password)
        )
    }

    suspend fun createLinkCode(): JServer.Credentials.LinkCode = withContext(dispatcherProvider.IO) {
        log(TAG) { "createLinkCode(account=$credentials)" }
        val rawCode = api.createLinkCode(deviceID = credentials!!.deviceId.id)
        return@withContext JServer.Credentials.LinkCode(code = rawCode)
    }

    suspend fun listDevices(): Collection<DeviceId> {
        log(TAG) { "listDevices()" }
        TODO()
    }

    suspend fun readModule(moduleId: ModuleId) {
        log(TAG) { "readModule(account=$credentials, moduleId=$moduleId)" }
        api.readModule(
            moduleId = moduleId.id,
            deviceId = credentials!!.deviceId.id
        )
        TODO()
    }

    suspend fun writeModule(moduleId: ModuleId, payload: ByteString) {
        log(TAG) { "writeModule(account=$credentials, moduleId=$moduleId, payload=$payload)" }
        api.writeModule(
            moduleId = moduleId.id,
            deviceId = credentials!!.deviceId.id
        )
        TODO()
    }

    @AssistedFactory
    interface Factory {
        fun create(account: JServer.Address): JServerEndpoint
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Connector", "Endpoint")
    }
}

