package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.CapabilitiesCodec
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.encryption.CryptoCapabilities
import eu.darken.octi.sync.core.encryption.EncryptionMode
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

/**
 * Verifies the AES-GCM-SIV ↔ legacy-SIV fall-back in [OctiServerEndpoint.createNewAccount].
 * Required because creating a GCM-SIV keyset on a device whose JCE can't actually do
 * AES-GCM-SIV would produce an account whose ciphertext can never be decrypted on this
 * device. See d4rken-org/octi#285 and [eu.darken.octi.sync.core.encryption.PayloadEncryption].
 */
class OctiServerEndpointCreateAccountTest : BaseTest() {

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
        every { deviceHeaderInterceptor.intercept(any()) } answers {
            val chain = firstArg<Interceptor.Chain>()
            chain.proceed(chain.request())
        }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun newEndpoint(cryptoCapabilities: CryptoCapabilities): OctiServerEndpoint {
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
            cryptoCapabilities = cryptoCapabilities,
            capabilitiesCodec = CapabilitiesCodec(retrofitJson),
        )
    }

    private fun enqueueRegisterResponse() {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"account":"acc-id-001","password":"pw"}"""
            )
        )
    }

    @Test
    fun `createNewAccount produces GCM-SIV keyset when gcmSivAvailable=true`() = runTest2 {
        enqueueRegisterResponse()

        val endpoint = newEndpoint(cryptoCapabilities = object : CryptoCapabilities {
            override val gcmSivAvailable: Boolean = true
        })

        val credentials = endpoint.createNewAccount()
        credentials.encryptionKeyset.type shouldBe EncryptionMode.AES256_GCM_SIV.typeString
    }

    @Test
    fun `createNewAccount falls back to legacy SIV keyset when gcmSivAvailable=false`() = runTest2 {
        enqueueRegisterResponse()

        val endpoint = newEndpoint(cryptoCapabilities = object : CryptoCapabilities {
            override val gcmSivAvailable: Boolean = false
        })

        val credentials = endpoint.createNewAccount()
        credentials.encryptionKeyset.type shouldBe EncryptionMode.AES256_SIV.typeString
    }
}
