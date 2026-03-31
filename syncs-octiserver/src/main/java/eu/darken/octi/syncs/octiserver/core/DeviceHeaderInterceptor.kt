package eu.darken.octi.syncs.octiserver.core

import android.os.Build
import dagger.Reusable
import eu.darken.octi.common.BuildConfigWrap
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@Reusable
class DeviceHeaderInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().apply {
            header("Octi-Device-Version", BuildConfigWrap.VERSION_NAME)
            header("Octi-Device-Platform", "android")
            header("Octi-Device-Label", Build.MODEL.replace(Regex("[^\\x20-\\x7E]"), "").take(128))
        }.build()
        return chain.proceed(request)
    }
}
