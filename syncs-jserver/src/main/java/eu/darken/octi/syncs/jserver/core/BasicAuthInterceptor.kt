package eu.darken.octi.syncs.jserver.core

import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class BasicAuthInterceptor @Inject constructor() : Interceptor {

    private var jServerCredentials: JServer.Credentials? = null
    private val okHttpCredentials: String?
        get() = jServerCredentials?.let { Credentials.basic(it.accountId.id, it.devicePassword.password) }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authenticatedRequest = request.newBuilder().apply {
            okHttpCredentials?.let {
                header("Authorization", it)
            }
        }.build()
        return chain.proceed(authenticatedRequest)
    }

    fun setCredentials(credentials: JServer.Credentials?) {
        log(TAG, VERBOSE) { "setCredentials(credentials=$credentials)" }
        jServerCredentials = credentials
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Endpoint", "BasicAuth")
    }
}