package eu.darken.octi.syncs.jserver.core

import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.SyncDeviceId
import eu.darken.octi.sync.core.SyncModuleId
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.ByteString
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant

class JServerEndpoint @AssistedInject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val baseHttpClient: OkHttpClient,
    private val baseMoshi: Moshi,
    @Assisted private val serverAdress: JServer.Address,
) {

    private val httpClient by lazy {
        baseHttpClient.newBuilder().apply {

        }.build()
    }

    private val api: JServerApi by lazy {
        Retrofit.Builder().apply {
            baseUrl(serverAdress.httpUrl)
            client(httpClient)
            addConverterFactory(MoshiConverterFactory.create(baseMoshi).asLenient())
        }.build().create(JServerApi::class.java)
    }

    suspend fun createNewAccount(ourDeviceId: SyncDeviceId): JServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "createNewAccount()" }
        val response = api.register(
            accountIDHeader = null,
            deviceIDHeader = ourDeviceId.id.toString(),
            shareCode = null,
        )
        require(ourDeviceId.id.toString() == response.deviceID)
        JServer.Credentials(
            createdAt = Instant.now(),
            serverAdress = serverAdress,
            accountId = JServer.Credentials.AccountId(response.accountID),
            deviceId = ourDeviceId,
            devicePassword = JServer.Credentials.DevicePassword(response.password)
        )
    }

    suspend fun linkToAccount(
        accountId: JServer.Credentials.AccountId,
        linkCode: JServer.Credentials.LinkCode,
    ): JServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "linkToAccount(accountId=$accountId, shareCode=$linkCode)" }
        TODO()
    }

    suspend fun createLinkCode(
        credentials: JServer.Credentials
    ): JServer.Credentials.LinkCode = withContext(dispatcherProvider.IO) {
        log(TAG) { "createLinkCode(account=$credentials)" }
        JServer.Credentials.LinkCode(code = "testcode")
//        api.shareAccount(credentials.accountId.id)
//        TODO()
    }

    suspend fun unregisterDevice(credentials: JServer.Credentials, toRemove: SyncDeviceId) {
        log(TAG) { "unregisterDevice(account=$credentials, toRemove=$toRemove)" }
        TODO()
    }

    suspend fun readModule(credentials: JServer.Credentials, moduleId: SyncModuleId) {
        log(TAG) { "readModule(account=$credentials, moduleId=$moduleId)" }
        TODO()
    }

    suspend fun writeModule(credentials: JServer.Credentials, moduleId: SyncModuleId, payload: ByteString) {
        log(TAG) { "writeModule(account=$credentials, moduleId=$moduleId, payload=$payload)" }
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

