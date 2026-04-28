package eu.darken.octi.syncs.octiserver.regression

import eu.darken.octi.common.serialization.SerializationModule
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.syncs.octiserver.core.BasicAuthInterceptor
import eu.darken.octi.syncs.octiserver.core.DeviceHeaderInterceptor
import eu.darken.octi.syncs.octiserver.core.OctiServer
import eu.darken.octi.syncs.octiserver.core.OctiServerEndpoint
import eu.darken.octi.syncs.octiserver.core.OctiServerHttpException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Cross-version regression: pins on-the-wire facts the new client depends on when it talks
 * to the legacy v0.8.1 octi-server image. ~700 production clients are still on the v0.8.1
 * server release at the cutover; this test guards the new client against accidentally
 * breaking that compatibility surface.
 *
 * Pure JVM — no Robolectric, no emulator. Boots the legacy server out-of-process via the
 * `regression-cross-version-client` GitHub Actions job (or locally via the docker-compose
 * scaffolding under `sync-server/regression/`) and points the test at it via
 * `CROSS_VERSION_TEST_SERVER`.
 *
 * Skipped locally when the env var is missing. When `CI=true` it is a hard error so a
 * misconfigured runner cannot silently bypass the regression check.
 */
class CrossVersionLegacyServerTest : BaseTest() {

    private lateinit var serverBaseUrl: String
    private lateinit var endpoint: OctiServerEndpoint
    private lateinit var basicAuthInterceptor: BasicAuthInterceptor
    private val syncSettings: SyncSettings = mockk()
    private val deviceHeaderInterceptor: DeviceHeaderInterceptor = mockk()

    private lateinit var testDeviceId: String

    @BeforeEach
    fun setUp() {
        val target = System.getenv("CROSS_VERSION_TEST_SERVER")
        if (target.isNullOrBlank()) {
            check(System.getenv("CI") != "true") {
                "CROSS_VERSION_TEST_SERVER missing in CI — the legacy-server service container did not boot or the env var is not wired up."
            }
            Assumptions.assumeTrue(false, "CROSS_VERSION_TEST_SERVER not set — skipping legacy v0.8.1 cross-version test")
        }
        serverBaseUrl = target!!.trimEnd('/')

        testDeviceId = UUID.randomUUID().toString()
        every { syncSettings.deviceId } returns DeviceId(testDeviceId)
        // X-Device-ID is added per-call via Retrofit @Header annotations on OctiServerApi.
        // The production DeviceHeaderInterceptor only adds Octi-Device-* headers and reads
        // android.os.Build (unavailable in JVM tests). v0.8.1 doesn't enforce those headers
        // for the routes we test, so a pass-through mock is sufficient.
        every { deviceHeaderInterceptor.intercept(any()) } answers {
            val chain = firstArg<Interceptor.Chain>()
            chain.proceed(chain.request())
        }

        basicAuthInterceptor = BasicAuthInterceptor()

        val url = serverBaseUrl.toHttpUrl()
        endpoint = OctiServerEndpoint(
            serverAdress = OctiServer.Address(domain = url.host, protocol = url.scheme, port = url.port),
            dispatcherProvider = TestDispatcherProvider(),
            syncSettings = syncSettings,
            baseHttpClient = OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .build(),
            // Reuse the production retrofit Json so wire-format drift in SerializationModule
            // is caught. SerializationModule is a regular `class` with @Provides — directly
            // instantiable without Hilt.
            retrofitJson = SerializationModule().retrofitJson(),
            basicAuthInterceptor = basicAuthInterceptor,
            deviceHeaderInterceptor = deviceHeaderInterceptor,
        )
    }

    @Test
    fun `createNewAccount succeeds on v0_8_1 server`() = runTest2 {
        val creds = endpoint.createNewAccount()
        creds.accountId.id shouldNotBe ""
        creds.devicePassword.password shouldNotBe ""
        creds.serverAdress.domain shouldBe serverBaseUrl.toHttpUrl().host
    }

    @Test
    fun `legacy POST writeModule + GET readModule round-trip works`() = runTest2 {
        val creds = endpoint.createNewAccount()
        endpoint.setCredentials(creds)

        val payload = "cross-version-payload".encodeUtf8()
        val moduleId = ModuleId("eu.darken.octi.module.test")
        val deviceId = DeviceId(testDeviceId)

        endpoint.writeModule(deviceId = deviceId, moduleId = moduleId, payload = payload)
        val read = endpoint.readModule(deviceId = deviceId, moduleId = moduleId)

        read shouldNotBe null
        read!!.payload shouldBe payload
    }

    @Test
    fun `legacy server does not expose v1 account storage endpoint`() = runTest2 {
        // Wire fact only: v0.8.1 returns 404 for the capability-probe route the new client
        // uses. The actual demote-to-LEGACY logic is unit-tested in OctiServerBlobStoreHubTest
        // against a mocked endpoint — this test pins the upstream wire fact that the demote
        // logic relies on, sent with the same auth + device-id headers the real client uses.
        val creds = endpoint.createNewAccount()

        val request = Request.Builder()
            .url("$serverBaseUrl/v1/account/storage")
            .header(
                "Authorization",
                Credentials.basic(creds.accountId.id, creds.devicePassword.password),
            )
            .header("X-Device-ID", testDeviceId)
            .get()
            .build()

        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
                response.code shouldBe 404
            }
    }

    @Test
    fun `createBlobSession on v0_8_1 throws OctiServerHttpException with 404 or 405`() = runTest2 {
        // Pins the wire fact that OctiServerBlobStore.put's 404/405 branch reacts to. The
        // demote-to-LEGACY mapping (and the typed BlobConnectorUnsupportedException) is
        // unit-tested in OctiServerBlobStoreTest; this test just guarantees that v0.8.1
        // really does reject the new endpoint with one of those codes.
        val creds = endpoint.createNewAccount()
        endpoint.setCredentials(creds)

        val ex = try {
            endpoint.createBlobSession(
                deviceId = DeviceId(testDeviceId),
                moduleId = ModuleId("eu.darken.octi.module.test"),
                sizeBytes = 1024L,
                checksum = null,
            )
            error("expected legacy server to reject createBlobSession but the call returned successfully")
        } catch (e: OctiServerHttpException) {
            e
        }
        (ex.httpCode in listOf(404, 405)) shouldBe true
    }
}
