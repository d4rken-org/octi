package eu.darken.octi.common.http

import dagger.Reusable
import eu.darken.octi.common.BuildConfigWrap
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@Reusable
class UserAgentInterceptor @Inject constructor() : Interceptor {

    private val userAgent = "octi/${BuildConfigWrap.VERSION_NAME}/${BuildConfigWrap.FLAVOR}"

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder().apply {
            header("User-Agent", userAgent)
        }.build()
        return chain.proceed(newRequest)
    }
}