package eu.darken.octi.common.http

import dagger.Reusable
import eu.darken.octi.common.BuildConfigWrap
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@Reusable
class UserAgentInterceptor @Inject constructor() : Interceptor {

    private val userAgent = buildString {
        append("octi/${BuildConfigWrap.VERSION_NAME}/${BuildConfigWrap.FLAVOR}")
        if (BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV) {
            val sha = BuildConfigWrap.GIT_SHA.takeIf { it.isNotBlank() }
            append(if (sha != null) "/dev-$sha" else "/dev")
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder().apply {
            header("User-Agent", userAgent)
        }.build()
        return chain.proceed(newRequest)
    }
}