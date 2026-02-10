package eu.darken.octi.modules.connectivity.core

import com.squareup.moshi.Moshi
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.network.PublicIpEndpointProvider
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Timeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.IOException
import java.util.concurrent.TimeUnit

class PublicIpProviderTest : BaseTest() {

    @MockK lateinit var httpClient: OkHttpClient
    @MockK lateinit var networkStateProvider: NetworkStateProvider
    @MockK lateinit var publicIpEndpointProvider: PublicIpEndpointProvider

    private lateinit var networkState: MutableStateFlow<NetworkStateProvider.State>
    private lateinit var endpointUrls: MutableStateFlow<List<String>>

    private val moshi = Moshi.Builder().build()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        networkState = MutableStateFlow(
            NetworkStateProvider.State.LegacyAPI21(
                isMeteredConnection = false,
                isInternetAvailable = true,
            )
        )
        endpointUrls = MutableStateFlow(listOf("https://prod.kserver.octi.darken.eu:443/v1/myip"))

        every { networkStateProvider.networkState } returns networkState
        every { publicIpEndpointProvider.endpointUrls } returns endpointUrls
    }

    private fun createInstance() = PublicIpProvider(
        httpClient = httpClient,
        networkStateProvider = networkStateProvider,
        dispatcherProvider = TestDispatcherProvider(),
        publicIpEndpointProvider = publicIpEndpointProvider,
        moshi = moshi,
    )

    private suspend fun TestScope.awaitFirstPublicIp(instance: PublicIpProvider): String? {
        val deferred = async { instance.publicIp.first() }
        advanceTimeBy(SETTLE_DELAY_MS)
        advanceUntilIdle()
        return deferred.await()
    }

    @Test
    fun `returns null and does not fetch when internet is unavailable`() = runTest2 {
        networkState.value = NetworkStateProvider.State.LegacyAPI21(
            isMeteredConnection = false,
            isInternetAvailable = false,
        )

        val result = createInstance().publicIp.first()

        result shouldBe null
        verify { httpClient wasNot Called }
    }

    @Test
    fun `falls back to next endpoint when first fails and closes both responses`() = runTest2 {
        endpointUrls.value = listOf(
            "https://first.example/v1/myip",
            "https://second.example/v1/myip",
        )

        val call1 = mockk<Call>()
        val timeout1 = mockk<Timeout>()
        val response1 = mockk<Response>()
        every { call1.timeout() } returns timeout1
        every { timeout1.timeout(any(), any()) } returns timeout1
        every { call1.execute() } returns response1
        every { response1.isSuccessful } returns false
        every { response1.code } returns 500
        every { response1.close() } just Runs

        val call2 = mockk<Call>()
        val timeout2 = mockk<Timeout>()
        val response2 = mockk<Response>()
        val body2 = mockk<ResponseBody>()
        every { call2.timeout() } returns timeout2
        every { timeout2.timeout(any(), any()) } returns timeout2
        every { call2.execute() } returns response2
        every { response2.isSuccessful } returns true
        every { response2.body } returns body2
        every { body2.string() } returns """{"ip":"203.0.113.42"}"""
        every { response2.close() } just Runs

        every { httpClient.newCall(any()) } returnsMany listOf(call1, call2)

        val result = awaitFirstPublicIp(createInstance())

        result shouldBe "203.0.113.42"
        verify(exactly = 2) { httpClient.newCall(any()) }
        verify { timeout1.timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        verify { timeout2.timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        verify { response1.close() }
        verify { response2.close() }
    }

    @Test
    fun `returns null for malformed json body and closes response`() = runTest2 {
        val call = mockk<Call>()
        val timeout = mockk<Timeout>()
        val response = mockk<Response>()
        val body = mockk<ResponseBody>()
        every { call.timeout() } returns timeout
        every { timeout.timeout(any(), any()) } returns timeout
        every { call.execute() } returns response
        every { response.isSuccessful } returns true
        every { response.body } returns body
        every { body.string() } returns "not-json"
        every { response.close() } just Runs

        every { httpClient.newCall(any()) } returns call

        val result = awaitFirstPublicIp(createInstance())

        result shouldBe null
        verify { timeout.timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        verify { response.close() }
    }

    @Test
    fun `falls back when first endpoint throws exception`() = runTest2 {
        endpointUrls.value = listOf(
            "https://first.example/v1/myip",
            "https://second.example/v1/myip",
        )

        val call1 = mockk<Call>()
        val timeout1 = mockk<Timeout>()
        every { call1.timeout() } returns timeout1
        every { timeout1.timeout(any(), any()) } returns timeout1
        every { call1.execute() } throws IOException("boom")

        val call2 = mockk<Call>()
        val timeout2 = mockk<Timeout>()
        val response2 = mockk<Response>()
        val body2 = mockk<ResponseBody>()
        every { call2.timeout() } returns timeout2
        every { timeout2.timeout(any(), any()) } returns timeout2
        every { call2.execute() } returns response2
        every { response2.isSuccessful } returns true
        every { response2.body } returns body2
        every { body2.string() } returns """{"ip":"198.51.100.7"}"""
        every { response2.close() } just Runs

        every { httpClient.newCall(any()) } returnsMany listOf(call1, call2)

        val result = awaitFirstPublicIp(createInstance())

        result shouldBe "198.51.100.7"
        verify(exactly = 2) { httpClient.newCall(any()) }
        verify { timeout1.timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        verify { timeout2.timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        verify { response2.close() }
    }

    companion object {
        private const val SETTLE_DELAY_MS = 500L
        private const val TIMEOUT_MS = 5_000L
    }
}
