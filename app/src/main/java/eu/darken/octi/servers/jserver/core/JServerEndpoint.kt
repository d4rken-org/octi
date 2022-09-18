package eu.darken.octi.servers.jserver.core

import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.SyncModuleId
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.ByteString
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

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
            baseUrl(serverAdress.baseUrl)
            client(httpClient)
            addConverterFactory(ScalarsConverterFactory.create())
            addConverterFactory(MoshiConverterFactory.create(baseMoshi).asLenient())
        }.build().create(JServerApi::class.java)
    }

    suspend fun createNewAccount(): JServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "createNewAccount()" }
        TODO()
    }

    suspend fun linkToAccount(
        accountId: JServer.Credentials.AccountId,
        shareCode: JServer.Credentials.ShareCode,
    ): JServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "linkToAccount(accountId=$accountId, shareCode=$shareCode)" }
        TODO()
    }

    suspend fun createShareCode(credentials: JServer.Credentials): JServer.Credentials.ShareCode =
        withContext(dispatcherProvider.IO) {
            log(TAG) { "createShareCode(account=$credentials)" }
            TODO()
        }

    suspend fun unregisterDevice(credentials: JServer.Credentials, toRemove: JServer.Credentials.DeviceId) {
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

