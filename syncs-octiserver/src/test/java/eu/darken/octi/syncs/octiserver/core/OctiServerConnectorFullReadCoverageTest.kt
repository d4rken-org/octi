package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.ConnectorPauseReason
import eu.darken.octi.sync.core.ConnectorSyncState
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.cache.SyncCache
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import eu.darken.octi.sync.core.execute
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.ByteString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import kotlin.time.Clock

/**
 * `lastFullReadAt` is the dashboard's proof that a peer without payload data genuinely has none.
 * These tests pin the four situations where that proof must (not) be granted.
 */
class OctiServerConnectorFullReadCoverageTest : BaseTest() {

    private val testDispatcher = TestDispatcherProvider()
    private val power = ModuleId("eu.darken.octi.module.core.power")
    private val wifi = ModuleId("eu.darken.octi.module.core.wifi")
    private val peerId = DeviceId("peer-device")

    private lateinit var endpoint: OctiServerEndpoint
    private lateinit var syncSettings: SyncSettings
    private lateinit var syncCache: SyncCache

    @BeforeEach
    fun setup() {
        endpoint = mockk(relaxed = true)
        syncCache = mockk(relaxed = true)
        syncSettings = mockk(relaxed = true)
        every { syncSettings.deviceId } returns DeviceId("test-device")
        coEvery { syncSettings.isPaused(any()) } returns false
    }

    private fun linkedDevice(deviceId: DeviceId) = OctiServerEndpoint.LinkedDevice(
        deviceId = deviceId,
        version = "1.2.3",
        platform = "android",
        label = "Peer",
        addedAt = null,
        lastSeen = null,
        capabilities = null,
    )

    /** Empty payload → [OctiServerConnector.fetchModule] skips decryption, no crypto needed. */
    private fun readData() = OctiServerEndpoint.ReadData(
        modifiedAt = Clock.System.now(),
        payload = ByteString.EMPTY,
        serverTime = null,
        localTime = Clock.System.now(),
    )

    private fun TestScope.createConnector(): OctiServerConnector {
        val endpointFactory = mockk<OctiServerEndpoint.Factory>()
        every { endpointFactory.create(any()) } returns endpoint

        val networkStateProvider = mockk<NetworkStateProvider>(relaxed = true)
        every { networkStateProvider.networkState } returns flowOf(
            mockk { every { isInternetAvailable } returns true },
        )

        val credentials = OctiServer.Credentials(
            serverAdress = OctiServer.Address(domain = "test.example.com"),
            accountId = OctiServer.Credentials.AccountId("test-account"),
            devicePassword = OctiServer.Credentials.DevicePassword("password"),
            encryptionKeyset = PayloadEncryption().exportKeyset(),
        )

        return OctiServerConnector(
            credentials = credentials,
            initialDeviceMetadata = emptyList(),
            scope = this.backgroundScope,
            dispatcherProvider = testDispatcher,
            endpointFactory = endpointFactory,
            blobStoreHub = mockk(relaxed = true),
            networkStateProvider = networkStateProvider,
            syncSettings = syncSettings,
            syncState = ConnectorSyncState(),
            syncCache = syncCache,
            supportedModuleIds = setOf(power, wifi),
            baseHttpClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        ).also { it.start(this.backgroundScope) }
    }

    private suspend fun OctiServerConnector.sync(options: SyncOptions) = execute(ConnectorCommand.Sync(options))

    private fun setupSuccessfulRead() {
        coEvery { endpoint.listDevices() } returns listOf(linkedDevice(peerId))
        coEvery { endpoint.readModule(any(), any()) } returns readData()
    }

    @Nested
    inner class `granting coverage` {
        @Test
        fun `full read that retrieved module data sets lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupSuccessfulRead()

            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            connector.state.first().lastFullReadAt.shouldNotBeNull()
        }
    }

    @Nested
    inner class `withholding coverage` {
        @Test
        fun `targeted first read does not set lastFullReadAt`() = runTest {
            // The targeted first read publishes a partial snapshot (existing == null), so the
            // absence of a module for a peer proves nothing.
            val connector = createConnector()
            setupSuccessfulRead()

            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                ),
            )

            connector.data.first().shouldNotBeNull()
            connector.state.first().lastFullReadAt.shouldBeNull()
        }

        @Test
        fun `device-filtered read does not set lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupSuccessfulRead()

            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    deviceFilter = setOf(peerId),
                ),
            )

            connector.state.first().lastFullReadAt.shouldBeNull()
        }

        @Test
        fun `full read where every module fetch failed leaves lastFullReadAt null`() = runTest {
            // readServer() swallows per-module failures into null and still returns a non-null
            // OctiServerData, so an all-failed read must not count as proof of absence.
            val connector = createConnector()
            coEvery { endpoint.listDevices() } returns listOf(linkedDevice(peerId))
            coEvery { endpoint.readModule(any(), any()) } throws IllegalStateException("boom")

            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            connector.data.first().shouldNotBeNull()
            connector.state.first().lastFullReadAt.shouldBeNull()
        }
    }

    @Nested
    inner class `clearing coverage` {
        @Test
        fun `pause clears lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupSuccessfulRead()
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))
            connector.state.first().lastFullReadAt.shouldNotBeNull()

            connector.execute(ConnectorCommand.Pause(ConnectorPauseReason.Manual))

            connector.state.first().lastFullReadAt.shouldBeNull()
        }

        @Test
        fun `resume clears lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupSuccessfulRead()
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))
            connector.state.first().lastFullReadAt.shouldNotBeNull()

            connector.execute(ConnectorCommand.Resume)

            connector.state.first().lastFullReadAt.shouldBeNull()
        }

        @Test
        fun `reset clears lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupSuccessfulRead()
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))
            connector.state.first().lastFullReadAt.shouldNotBeNull()

            connector.execute(ConnectorCommand.Reset)

            connector.state.first().lastFullReadAt.shouldBeNull()
        }
    }
}
