package eu.darken.octi.syncs.octiserver.core

import dagger.Reusable
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@Reusable
class DeviceHeaderInterceptor @Inject constructor(
    private val values: DeviceHeaderValuesProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().apply {
            header("Octi-Device-Version", values.version())
            header("Octi-Device-Platform", values.platform())
            header("Octi-Device-Label", values.label())
            header("Octi-Device-Capabilities", values.capabilitiesHeader())
        }.build()
        return chain.proceed(request)
    }
}
