package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

/**
 * Verifies the on-the-wire shape of [OctiServerEndpoint.writeModule] — specifically that the
 * `device-id` query param carries the *target* device passed by the caller, not the caller's
 * own id (the historical hardcoded value before the deviceId-plumbing fix). A call-site grep
 * cannot catch a regression that hardcodes the target inside the wrapper body — this does.
 */
class OctiServerEndpointWriteModuleTest : BaseTest() {

    private lateinit var server: MockWebServer
    private val syncSettings: SyncSettings = mockk()
    private val basicAuthInterceptor = BasicAuthInterceptor()
    private val deviceHeaderInterceptor: DeviceHeaderInterceptor = mockk()

    private val retrofitJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        every { syncSettings.deviceId } returns DeviceId("caller-uuid")
        // Pass-through — DeviceHeaderInterceptor reads android.os.Build which is unavailable
        // here; we don't care about its headers for this test.
        every { deviceHeaderInterceptor.intercept(any()) } answers {
            val chain = firstArg<Interceptor.Chain>()
            chain.proceed(chain.request())
        }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun newEndpoint(): OctiServerEndpoint {
        val url = server.url("/")
        val address = OctiServer.Address(
            domain = url.host,
            protocol = url.scheme,
            port = url.port,
        )
        return OctiServerEndpoint(
            serverAdress = address,
            dispatcherProvider = TestDispatcherProvider(),
            syncSettings = syncSettings,
            baseHttpClient = OkHttpClient(),
            retrofitJson = retrofitJson,
            basicAuthInterceptor = basicAuthInterceptor,
            deviceHeaderInterceptor = deviceHeaderInterceptor,
        )
    }

    @Test
    fun `writeModule sends device-id query param with target id, X-Device-ID header with caller id`() = runTest2 {
        server.enqueue(MockResponse().setResponseCode(200))

        newEndpoint().writeModule(
            deviceId = DeviceId("target-uuid"),
            moduleId = ModuleId("eu.darken.octi.module.test"),
            payload = "hello".encodeUtf8(),
        )

        val recorded = server.takeRequest()
        recorded.method shouldBe "POST"
        recorded.path shouldContain "/v1/module/eu.darken.octi.module.test"
        recorded.path shouldContain "device-id=target-uuid"
        recorded.getHeader("X-Device-ID") shouldBe "caller-uuid"
    }

    @Test
    fun `writeModule when target equals caller still sends both fields verbatim`() = runTest2 {
        server.enqueue(MockResponse().setResponseCode(200))

        newEndpoint().writeModule(
            deviceId = DeviceId("caller-uuid"),
            moduleId = ModuleId("eu.darken.octi.module.test"),
            payload = "hello".encodeUtf8(),
        )

        val recorded = server.takeRequest()
        recorded.path shouldContain "device-id=caller-uuid"
        recorded.getHeader("X-Device-ID") shouldBe "caller-uuid"
    }
}
