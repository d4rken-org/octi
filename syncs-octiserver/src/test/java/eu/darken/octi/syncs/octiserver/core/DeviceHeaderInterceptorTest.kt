package eu.darken.octi.syncs.octiserver.core

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DeviceHeaderInterceptorTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fakeValuesProvider(
        version: String = "1.2.3",
        platform: String = "android",
        label: String = "Test Device",
        capabilities: Set<String> = setOf("encryption:_reported", "encryption:AES256_SIV"),
    ): DeviceHeaderValuesProvider = mockk(relaxed = true) {
        every { version() } returns version
        every { platform() } returns platform
        every { label() } returns label
        every { capabilitiesHeader() } returns json.encodeToString(capabilities.sorted())
    }

    private fun runInterceptor(interceptor: DeviceHeaderInterceptor): Request {
        val original = Request.Builder().url("https://example.com/").build()
        val captured = slot<Request>()
        val chain = mockk<Interceptor.Chain>().also {
            every { it.request() } returns original
            every { it.proceed(capture(captured)) } returns Response.Builder()
                .request(original)
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").build()
        }
        interceptor.intercept(chain)
        return captured.captured
    }

    @Test
    fun `adds expected headers`() {
        val interceptor = DeviceHeaderInterceptor(fakeValuesProvider())
        val request = runInterceptor(interceptor)
        request.header("Octi-Device-Version") shouldBe "1.2.3"
        request.header("Octi-Device-Platform") shouldBe "android"
        request.header("Octi-Device-Label") shouldBe "Test Device"
    }

    @Test
    fun `capabilities header decodes to the provided set regardless of element order`() {
        val caps = setOf(
            "encryption:_reported",
            "encryption:AES256_SIV",
            "encryption:AES256_GCM_SIV",
        )
        val interceptor = DeviceHeaderInterceptor(fakeValuesProvider(capabilities = caps))
        val request = runInterceptor(interceptor)
        val headerValue = request.header("Octi-Device-Capabilities")!!
        val decoded = json.parseToJsonElement(headerValue).jsonArray.map { it.toString().trim('"') }
        decoded shouldContainExactlyInAnyOrder caps.toList()
    }
}
