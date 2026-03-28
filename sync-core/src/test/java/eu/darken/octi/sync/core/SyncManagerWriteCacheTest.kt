package eu.darken.octi.sync.core

import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.cache.SyncCache
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class SyncManagerWriteCacheTest : BaseTest() {

    private val connectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test", account = "acc1")
    private val deviceId = DeviceId("device-1")
    private val powerModuleId = ModuleId("eu.darken.octi.module.core.power")
    private val wifiModuleId = ModuleId("eu.darken.octi.module.core.wifi")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")

    private lateinit var connector: SyncConnector
    private lateinit var syncSettings: SyncSettings
    private lateinit var pausedConnectorsValue: MutableStateFlow<Set<ConnectorId>>
    private lateinit var syncCache: SyncCache
    private lateinit var connectorHub: ConnectorHub
    private lateinit var connectorsFlow: MutableStateFlow<List<SyncConnector>>

    private fun createModule(moduleId: ModuleId, payload: String = "test"): SyncWrite.Device.Module = mockk {
        every { this@mockk.moduleId } returns moduleId
        every { this@mockk.payload } returns payload.encodeUtf8()
    }

    @BeforeEach
    fun setup() {
        connector = mockk(relaxed = true) {
            every { identifier } returns connectorId
            every { state } returns flowOf()
            every { data } returns flowOf(null)
            every { syncEvents } returns flowOf()
            every { syncEventMode } returns MutableStateFlow(SyncConnector.EventMode.NONE)
        }
        connectorsFlow = MutableStateFlow(listOf(connector))

        pausedConnectorsValue = MutableStateFlow(emptySet())
        syncSettings = mockk(relaxed = true) {
            every { pausedConnectors } returns mockk<DataStoreValue<Set<ConnectorId>>>(relaxed = true) {
                every { flow } returns pausedConnectorsValue
            }
        }
        every { syncSettings.deviceId } returns deviceId

        syncCache = mockk(relaxed = true)

        connectorHub = mockk(relaxed = true) {
            every { connectors } returns connectorsFlow
        }
    }

    private fun TestScope.createSyncManager(): Pair<SyncManager, Job> {
        val job = Job()
        val sm = SyncManager(
            scope = this + job,
            dispatcherProvider = mockk(relaxed = true) {
                every { Default } returns coroutineContext[kotlinx.coroutines.CoroutineDispatcher.Key]!!
            },
            syncSettings = syncSettings,
            syncCache = syncCache,
            connectorHubs = setOf(connectorHub),
        )
        return sm to job
    }

    @Nested
    inner class `write caching` {
        @Test
        fun `write caches data and sends to connector`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.write(createModule(powerModuleId, "battery-data"))

            coVerify(exactly = 1) { connector.write(any()) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `write with no connectors still caches for later replay`() = runTest2 {
            connectorsFlow.value = emptyList()
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.write(createModule(powerModuleId, "battery-data"))

            coVerify(exactly = 0) { connector.write(any()) }

            // Add connector back
            connectorsFlow.value = listOf(connector)
            advanceUntilIdle()

            // Sync with writeData=true should replay cached write
            sm.sync(connectorId, SyncOptions(writeData = true, readData = false, stats = false))

            val writes = mutableListOf<SyncWrite>()
            coVerify(atLeast = 1) { connector.write(capture(writes)) }
            writes.last().modules.single().moduleId shouldBe powerModuleId

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `sync write replay` {
        @Test
        fun `sync with writeData true replays cached writes`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.write(createModule(powerModuleId, "power-data"))
            sm.write(createModule(wifiModuleId, "wifi-data"))

            sm.sync(connectorId, SyncOptions(writeData = true, readData = false, stats = false))

            val writes = mutableListOf<SyncWrite>()
            coVerify(atLeast = 3) { connector.write(capture(writes)) }

            val replayWrite = writes.last()
            replayWrite.modules.map { it.moduleId }.toSet() shouldBe setOf(powerModuleId, wifiModuleId)

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `sync with writeData false does not replay`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.write(createModule(powerModuleId, "power-data"))

            sm.sync(connectorId, SyncOptions(writeData = false, readData = false, stats = false))

            coVerify(exactly = 1) { connector.write(any()) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `cache keeps latest data per module`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.write(createModule(powerModuleId, "old-data"))
            sm.write(createModule(powerModuleId, "new-data"))

            sm.sync(connectorId, SyncOptions(writeData = true, readData = false, stats = false))

            val writes = mutableListOf<SyncWrite>()
            coVerify(atLeast = 1) { connector.write(capture(writes)) }

            val replayWrite = writes.last()
            replayWrite.modules.size shouldBe 1
            replayWrite.modules.single().moduleId shouldBe powerModuleId
            replayWrite.modules.single().payload shouldBe "new-data".encodeUtf8()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `replay includes all cached modules independently`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.write(createModule(powerModuleId, "power"))
            sm.write(createModule(wifiModuleId, "wifi"))
            sm.write(createModule(metaModuleId, "meta"))

            sm.sync(connectorId, SyncOptions(writeData = true, readData = false, stats = false))

            val writes = mutableListOf<SyncWrite>()
            coVerify(atLeast = 1) { connector.write(capture(writes)) }

            val replayWrite = writes.last()
            replayWrite.modules.map { it.moduleId }.toSet() shouldBe setOf(
                powerModuleId,
                wifiModuleId,
                metaModuleId,
            )

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `empty cache skips replay`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.sync(connectorId, SyncOptions(writeData = true, readData = false, stats = false))

            coVerify(exactly = 0) { connector.write(any()) }

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `paused connectors` {
        @Test
        fun `write skips paused connectors but still caches`() = runTest2 {
            pausedConnectorsValue.value = setOf(connectorId)

            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.write(createModule(powerModuleId, "data"))

            coVerify(exactly = 0) { connector.write(any()) }

            // Unpause and sync
            pausedConnectorsValue.value = emptySet()
            sm.sync(connectorId, SyncOptions(writeData = true, readData = false, stats = false))

            coVerify(atLeast = 1) { connector.write(any()) }

            job.cancel()
            advanceUntilIdle()
        }
    }
}
