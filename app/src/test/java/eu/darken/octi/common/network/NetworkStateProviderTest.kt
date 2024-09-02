@file:Suppress("DEPRECATION")

package eu.darken.octi.common.network

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.ConnectivityManager.TYPE_WIFI
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import eu.darken.octi.common.BuildWrap
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import testhelpers.flow.test

class NetworkStateProviderTest : BaseTest() {

    @MockK lateinit var connectivityManager: ConnectivityManager

    @MockK lateinit var network: Network
    @MockK lateinit var networkInfo: NetworkInfo
    @MockK lateinit var networkRequest: NetworkRequest
    @MockK lateinit var networkRequestBuilder: NetworkRequest.Builder
    @MockK lateinit var networkRequestBuilderProvider: NetworkRequestBuilderProvider
    @MockK lateinit var capabilities: NetworkCapabilities
    @MockK lateinit var linkProperties: LinkProperties

    private var lastRequest: NetworkRequest? = null
    private var lastCallback: NetworkCallback? = null

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(BuildWrap.VersionWrap)
        every { BuildWrap.VersionWrap.SDK_INT } returns 24

        every { networkRequestBuilderProvider.get() } returns networkRequestBuilder
        networkRequestBuilder.apply {
            every { addCapability(any()) } returns networkRequestBuilder
            every { build() } returns networkRequest
        }

        connectivityManager.apply {
            every { activeNetwork } returns network
            every { activeNetworkInfo } answers { networkInfo }
            every { unregisterNetworkCallback(any<NetworkCallback>()) } just Runs

            every { getNetworkCapabilities(network) } answers { capabilities }
            every { getLinkProperties(network) } answers { linkProperties }

            every {
                registerNetworkCallback(any<NetworkRequest>(), any<NetworkCallback>())
            } answers {
                lastRequest = arg(0)
                lastCallback = arg(1)
                mockk()
            }
        }

        networkInfo.apply {
            every { type } returns TYPE_WIFI
            every { isConnected } returns true
        }

        capabilities.apply {
            // The happy path is an unmetered internet connection being available
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        }
    }

    private fun createInstance(scope: CoroutineScope) = NetworkStateProvider(
        appScope = scope,
        networkRequestBuilderProvider = networkRequestBuilderProvider,
        manager = connectivityManager,
    )

    @Test
    fun `init is sideeffect free and lazy`() {
        shouldNotThrowAny {
            createInstance(TestCoroutineScope())
        }
        verify { connectivityManager wasNot Called }
    }

    @Test
    fun `initial state is emitted correctly without callback`() = runTest2(autoCancel = true) {
        val instance = createInstance(this)

        instance.networkState.first().apply {
            isMeteredConnection shouldBe false
            isInternetAvailable shouldBe true
        }

        advanceUntilIdle()

        verifySequence {
            connectivityManager.activeNetwork
            connectivityManager.getNetworkCapabilities(network)
            connectivityManager.registerNetworkCallback(networkRequest, any<NetworkCallback>())
            connectivityManager.unregisterNetworkCallback(lastCallback!!)
        }
    }

    @Test
    fun `we can handle null networks`() = runTest2(autoCancel = true) {
        every { connectivityManager.activeNetwork } returns null
        val instance = createInstance(this)

        instance.networkState.first().apply {
            isInternetAvailable shouldBe false
            isMeteredConnection shouldBe true
        }
        verify { connectivityManager.activeNetwork }
    }

    @Test
    fun `system callbacks lead to new emissions with an updated state`() = runTest2(autoCancel = true) {
        val instance = createInstance(this)

        val testCollector = instance.networkState.test(scope = this)
        advanceUntilIdle()

        lastCallback!!.onAvailable(mockk())
        advanceUntilIdle()

        every { connectivityManager.activeNetwork } returns null
        lastCallback!!.onUnavailable()
        advanceUntilIdle()

        every { connectivityManager.activeNetwork } returns network
        lastCallback!!.onAvailable(mockk())
        advanceUntilIdle()

        // 3 not 4 as first onAvailable call doesn't change the value (stateIn behavior)
        testCollector.latestValues.size shouldBe 3

        testCollector.awaitFinal(cancel = true)
        advanceUntilIdle()

        verifySequence {
            // Start value
            connectivityManager.activeNetwork
            connectivityManager.getNetworkCapabilities(network)
            connectivityManager.registerNetworkCallback(networkRequest, any<NetworkCallback>())

            // onAvailable
            connectivityManager.activeNetwork
            connectivityManager.getNetworkCapabilities(network)

            // onUnavailable
            connectivityManager.activeNetwork

            // onAvailable
            connectivityManager.activeNetwork
            connectivityManager.getNetworkCapabilities(network)
            connectivityManager.unregisterNetworkCallback(lastCallback!!)
        }
    }

    @Test
    fun `metered connection state checks capabilities`() = runTest2(autoCancel = true) {
        createInstance(this).apply {
            networkState.first().isMeteredConnection shouldBe false

            every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns false
            networkState.first().isMeteredConnection shouldBe true

            every { connectivityManager.getNetworkCapabilities(any()) } returns null
            networkState.first().isMeteredConnection shouldBe true
        }
    }

    @Test
    fun `Android 6 not metered on wifi`() = runTest2(autoCancel = true) {
        every { BuildWrap.VERSION.SDK_INT } returns 23
        val instance = createInstance(this)

        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        instance.networkState.first().isMeteredConnection shouldBe true
        advanceUntilIdle()

        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        instance.networkState.first().isMeteredConnection shouldBe false
        advanceUntilIdle()

        every { connectivityManager.getNetworkCapabilities(any()) } returns null
        instance.networkState.first().isMeteredConnection shouldBe true
    }

    @Test
    fun `do not try to unregister the callback if it was never registered`() = runTest2(autoCancel = true) {
        every {
            connectivityManager.registerNetworkCallback(
                any(),
                any<NetworkCallback>()
            )
        } throws SecurityException()
        val testScope2 = TestScope()
        val testObs = createInstance(this).networkState.test(scope = this).start(testScope2)

        advanceUntilIdle()

        verifySequence {
            connectivityManager.activeNetwork
            connectivityManager.getNetworkCapabilities(network)
            connectivityManager.registerNetworkCallback(networkRequest, any<NetworkCallback>())
        }
        verify(exactly = 0) {
            connectivityManager.unregisterNetworkCallback(any<NetworkCallback>())
        }
    }

    @Test
    fun `send the fallback state on exceptions`() = runTest2(autoCancel = true) {
        every {
            connectivityManager.registerNetworkCallback(
                any(),
                any<NetworkCallback>()
            )
        } throws SecurityException()

        val instance = createInstance(this)

        val testObs = instance.networkState.test(scope = this).start(this)

        advanceUntilIdle()

        testObs.latestValues[0].apply {
            isInternetAvailable shouldBe true
            isMeteredConnection shouldBe false
        }

        testObs.latestValues[1].apply {
            isInternetAvailable shouldBe true
            isMeteredConnection shouldBe true
        }
        testObs.cancelAndJoin()
    }
}

