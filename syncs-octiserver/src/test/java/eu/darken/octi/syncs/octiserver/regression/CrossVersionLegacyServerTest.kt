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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
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
 * Tagged `cross-version` and excluded from the regular `:syncs-octiserver:testDebugUnitTest`
 * task by default — the regression CI job opts in with `-PincludeCrossVersionTests`. Run
 * locally with the same flag against a docker-compose'd legacy server, e.g.
 *   `CROSS_VERSION_TEST_SERVER=http://127.0.0.1:18080 ./gradlew :syncs-octiserver:testDebugUnitTest \
 *     --tests "*CrossVersionLegacyServerTest" -PincludeCrossVersionTests`
 */
@Tag("cross-version")
class CrossVersionLegacyServerTest : BaseTest() {

    private lateinit var serverBaseUrl: String
    private lateinit var endpoint: OctiServerEndpoint
    private lateinit var testDeviceId: String

    @BeforeEach
    fun setUp() {
        // The @Tag("cross-version") gate already prevents this test from running unless the
        // regression CI job (or a developer) opts in. If it ran, the env var must be set —
        // a missing/blank value here means the runner is misconfigured and we want to fail
        // loudly rather than silently skip.
        serverBaseUrl = checkNotNull(System.getenv("CROSS_VERSION_TEST_SERVER")?.takeIf { it.isNotBlank() }) {
            "CROSS_VERSION_TEST_SERVER must be set when running cross-version tests"
        }.trimEnd('/')

        testDeviceId = UUID.randomUUID().toString()
        endpoint = newEndpoint(testDeviceId)
    }

    /**
     * Build a fresh [OctiServerEndpoint] pointed at the legacy server, with a stubbed
     * [SyncSettings] returning [deviceId] and a pass-through [DeviceHeaderInterceptor].
     *
     * X-Device-ID is added per-call via Retrofit @Header annotations on OctiServerApi, so the
     * pass-through interceptor is sufficient. Production [DeviceHeaderInterceptor] reads
     * `android.os.Build` (unavailable in JVM tests) and v0.8.1 doesn't enforce its headers
     * for the routes covered here.
     */
    private fun newEndpoint(deviceId: String): OctiServerEndpoint {
        val syncSettings = mockk<SyncSettings>().also {
            every { it.deviceId } returns DeviceId(deviceId)
        }
        val deviceHeaderInterceptor = mockk<DeviceHeaderInterceptor>().also {
            every { it.intercept(any()) } answers {
                val chain = firstArg<Interceptor.Chain>()
                chain.proceed(chain.request())
            }
        }
        val url = serverBaseUrl.toHttpUrl()
        return OctiServerEndpoint(
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
            basicAuthInterceptor = BasicAuthInterceptor(),
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
    fun `legacy getDeviceList parses cleanly against v0_8_1 response shape`() = runTest2 {
        // Catches DTO drift: v0.8.1 may not populate `version`/`platform`/`label`/`addedAt`/
        // `lastSeen`. The new client's DevicesResponse.Device has nullable defaults for those;
        // this test verifies kotlinx.serialization actually accepts the v0.8.1 response shape
        // end-to-end. Existing OctiServerSerializationTest only exercises hand-crafted JSON.
        val creds = endpoint.createNewAccount()
        endpoint.setCredentials(creds)

        val devices = endpoint.listDevices()
        devices.size shouldBe 1
        devices.single().deviceId.id shouldBe testDeviceId
    }

    @Test
    fun `legacy account linking flow round-trips on v0_8_1 (createLinkCode + linkToExistingAccount)`() = runTest2 {
        // Pre-cutover users will pair new clients into existing v0.8.1-server accounts on day
        // one; this is the wire flow that has to stay working. createShareCode + register?share
        // are the routes; new client wraps them as createLinkCode + linkToExistingAccount.
        val credsA = endpoint.createNewAccount()
        endpoint.setCredentials(credsA)

        val linkCode = endpoint.createLinkCode()
        linkCode.code shouldNotBe ""

        val deviceBId = UUID.randomUUID().toString()
        val endpointB = newEndpoint(deviceBId)
        val linked = endpointB.linkToExistingAccount(linkCode)

        linked.accountId.id shouldBe credsA.accountId.id
        linked.devicePassword.password shouldNotBe ""
        // Both devices should now be members of the same account.
        endpointB.setCredentials(
            credsA.copy(
                devicePassword = OctiServer.Credentials.DevicePassword(linked.devicePassword.password),
            ),
        )
        val devicesFromB = endpointB.listDevices()
        devicesFromB.map { it.deviceId.id }.toSet() shouldBe setOf(testDeviceId, deviceBId)
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
