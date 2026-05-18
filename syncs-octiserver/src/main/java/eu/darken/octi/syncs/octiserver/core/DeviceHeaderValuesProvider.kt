package eu.darken.octi.syncs.octiserver.core

import android.os.Build
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.sync.core.CapabilitiesCodec
import eu.darken.octi.sync.core.DeviceCapabilitiesProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes the values for the `Octi-Device-*` HTTP headers sent by [DeviceHeaderInterceptor].
 *
 * Pulled out of the interceptor so the values can be unit-tested on the JVM (the interceptor
 * itself reads [Build.MODEL] which is unmocked off-device). Tests inject a fake instance of
 * this provider; the interceptor logic is then trivially correct.
 */
@Singleton
class DeviceHeaderValuesProvider @Inject constructor(
    private val capabilitiesProvider: DeviceCapabilitiesProvider,
    private val capabilitiesCodec: CapabilitiesCodec,
) {

    fun version(): String = BuildConfigWrap.VERSION_NAME

    fun platform(): String = PLATFORM_ANDROID

    fun label(): String = Build.MODEL.replace(NON_PRINTABLE_ASCII, "").take(128)

    fun capabilitiesHeader(): String = capabilitiesCodec.encodeToHeader(capabilitiesProvider.current())

    companion object {
        const val PLATFORM_ANDROID = "android"
        private val NON_PRINTABLE_ASCII = Regex("[^\\x20-\\x7E]")
    }
}
