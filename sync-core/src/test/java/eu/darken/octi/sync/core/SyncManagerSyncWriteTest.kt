package eu.darken.octi.sync.core

import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.cache.SyncCache
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class SyncManagerSyncWriteTest : BaseTest() {

    private val connectorId1 = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test", account = "acc1")
    private val connectorId2 = ConnectorId(type = ConnectorType.GDRIVE, subtype = "test", account = "acc2")
    private val deviceId = DeviceId("device-1")
    private val powerModuleId = ModuleId("eu.darken.octi.module.core.power")
    private val wifiModuleId = ModuleId("eu.darken.octi.module.core.wifi")

    private lateinit var connector1: SyncConnector
    private lateinit var connector2: SyncConnector
    private lateinit var syncSettings: SyncSettings
    private lateinit var pausedConnectorsValue: MutableStateFlow<Set<ConnectorId>>
    private lateinit var syncCache: SyncCache
    private lateinit var connectorHub: ConnectorHub
    private lateinit var connectorsFlow: MutableStateFlow<List<SyncConnector>>
    private lateinit var connectorSyncState: ConnectorSyncState

    private fun createModule(moduleId: ModuleId, payload: String = "test"): SyncWrite.Device.Module = mockk {
        every { this@mockk.moduleId } returns moduleId
        every { this@mockk.payload } returns payload.encodeUtf8()
    }

    @BeforeEach
    fun setup() {
        connector1 = mockk(relaxed = true) {
            every { identifier } returns connectorId1
            every { state } returns flowOf()
            every { data } returns flowOf(null)
            every { syncEvents } returns flowOf()
            every { syncEventMode } returns MutableStateFlow(SyncConnector.EventMode.NONE)
        }
        connector2 = mockk(relaxed = true) {
            every { identifier } returns connectorId2
            every { state } returns flowOf()
            every { data } returns flowOf(null)
            every { syncEvents } returns flowOf()
            every { syncEventMode } returns MutableStateFlow(SyncConnector.EventMode.NONE)
        }
        connectorsFlow = MutableStateFlow(listOf(connector1))

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

        connectorSyncState = ConnectorSyncState()
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
            connectorSyncState = connectorSyncState,
        )
        return sm to job
    }

    @Nested
    inner class `payload updates` {
        @Test
        fun `updatePayload stores module and sync passes it via writePayload`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "battery-data"))

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val optionsSlot = slot<SyncOptions>()
            coVerify { connector1.sync(capture(optionsSlot)) }
            optionsSlot.captured.writePayload.single().moduleId shouldBe powerModuleId

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `multiple modules are all passed via writePayload`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "power-data"))
            sm.updatePayload(createModule(wifiModuleId, "wifi-data"))

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val optionsSlot = slot<SyncOptions>()
            coVerify { connector1.sync(capture(optionsSlot)) }
            optionsSlot.captured.writePayload.map { it.moduleId } shouldContainExactlyInAnyOrder
                listOf(powerModuleId, wifiModuleId)

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `latest payload per module wins`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "old-data"))
            sm.updatePayload(createModule(powerModuleId, "new-data"))

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val optionsSlot = slot<SyncOptions>()
            coVerify { connector1.sync(capture(optionsSlot)) }
            optionsSlot.captured.writePayload.size shouldBe 1
            optionsSlot.captured.writePayload.single().payload shouldBe "new-data".encodeUtf8()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `writeData false does not include payload`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(connectorId1, SyncOptions(writeData = false, readData = false, stats = false))

            val optionsSlot = slot<SyncOptions>()
            coVerify { connector1.sync(capture(optionsSlot)) }
            optionsSlot.captured.writePayload shouldBe emptyList()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `no payloads results in empty writePayload`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val optionsSlot = slot<SyncOptions>()
            coVerify { connector1.sync(capture(optionsSlot)) }
            optionsSlot.captured.writePayload shouldBe emptyList()

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `hash deduplication` {
        @Test
        fun `hash match skips module on second sync`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "data"))

            // First sync — hash mismatch, module included
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            val firstOptions = slot<SyncOptions>()
            coVerify { connector1.sync(capture(firstOptions)) }
            firstOptions.captured.writePayload.size shouldBe 1

            // Second sync — hash matches, module skipped
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            val allOptions = mutableListOf<SyncOptions>()
            coVerify(exactly = 2) { connector1.sync(capture(allOptions)) }
            allOptions.last().writePayload shouldBe emptyList()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `hash mismatch includes module after payload update`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "old-data"))
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            // Update payload with new data
            sm.updatePayload(createModule(powerModuleId, "new-data"))
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val allOptions = mutableListOf<SyncOptions>()
            coVerify(exactly = 2) { connector1.sync(capture(allOptions)) }
            allOptions.last().writePayload.size shouldBe 1
            allOptions.last().writePayload.single().payload shouldBe "new-data".encodeUtf8()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `new connector gets all modules — empty hashes`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "data"))

            // Sync connector1 first
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            // Connector2 has never synced — should get the module
            sm.sync(connectorId2, SyncOptions(writeData = true, readData = false, stats = false))

            val options2 = slot<SyncOptions>()
            coVerify { connector2.sync(capture(options2)) }
            options2.captured.writePayload.size shouldBe 1

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `multiple connectors have independent hash tracking`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "data"))

            // Sync both connectors
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            sm.sync(connectorId2, SyncOptions(writeData = true, readData = false, stats = false))

            // Both should have received the module
            val opts1 = slot<SyncOptions>()
            coVerify { connector1.sync(capture(opts1)) }
            opts1.captured.writePayload.size shouldBe 1

            val opts2 = slot<SyncOptions>()
            coVerify { connector2.sync(capture(opts2)) }
            opts2.captured.writePayload.size shouldBe 1

            // Second sync for both — hashes match, both empty
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            sm.sync(connectorId2, SyncOptions(writeData = true, readData = false, stats = false))

            val all1 = mutableListOf<SyncOptions>()
            coVerify(exactly = 2) { connector1.sync(capture(all1)) }
            all1.last().writePayload shouldBe emptyList()

            val all2 = mutableListOf<SyncOptions>()
            coVerify(exactly = 2) { connector2.sync(capture(all2)) }
            all2.last().writePayload shouldBe emptyList()

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `sync failure` {
        @Test
        fun `sync failure does not update hashes — module resent next sync`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "data"))

            // First sync fails
            coEvery { connector1.sync(any()) } throws RuntimeException("Network error")
            runCatching { sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false)) }

            // Second sync succeeds — module should be resent
            coEvery { connector1.sync(any()) } returns Unit
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val allOptions = mutableListOf<SyncOptions>()
            coVerify(exactly = 2) { connector1.sync(capture(allOptions)) }
            allOptions.last().writePayload.size shouldBe 1

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `connector not found` {
        @Test
        fun `singleOrNull returns null — sync skipped gracefully`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val unknownId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "unknown", account = "unknown")
            sm.sync(unknownId, SyncOptions(writeData = true, readData = false, stats = false))

            // No connector.sync called at all
            coVerify(exactly = 0) { connector1.sync(any()) }

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `pause guards` {
        @Test
        fun `sync(connectorId) is a no-op when connector is paused`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pausedConnectorsValue.value = setOf(connectorId1)
            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            coVerify(exactly = 0) { connector1.sync(any()) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `resetData is a no-op when connector is paused`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pausedConnectorsValue.value = setOf(connectorId1)

            sm.resetData(connectorId1)

            coVerify(exactly = 0) { connector1.resetData() }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `global sync() skips paused connectors`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pausedConnectorsValue.value = setOf(connectorId1)
            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(SyncOptions(writeData = true, readData = false, stats = false))
            advanceUntilIdle()

            coVerify(exactly = 0) { connector1.sync(any()) }
            coVerify(exactly = 1) { connector2.sync(any()) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `unpausing resumes sync`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pausedConnectorsValue.value = setOf(connectorId1)
            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            coVerify(exactly = 0) { connector1.sync(any()) }

            pausedConnectorsValue.value = emptySet()
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            coVerify(exactly = 1) { connector1.sync(any()) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `connectors flow excludes paused entries`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pausedConnectorsValue.value = setOf(connectorId1)
            advanceUntilIdle()

            val active = sm.connectors.first()
            active.map { it.identifier } shouldBe listOf(connectorId2)

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `allConnectors flow includes paused entries`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pausedConnectorsValue.value = setOf(connectorId1)
            advanceUntilIdle()

            val all = sm.allConnectors.first()
            all.map { it.identifier } shouldContainExactlyInAnyOrder listOf(connectorId1, connectorId2)

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `payload updated during sync` {
        @Test
        fun `new payload sent on next sync when updated between syncs`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "v1"))
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            // Payload updated
            sm.updatePayload(createModule(powerModuleId, "v2"))
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val allOptions = mutableListOf<SyncOptions>()
            coVerify(exactly = 2) { connector1.sync(capture(allOptions)) }
            allOptions[0].writePayload.single().payload shouldBe "v1".encodeUtf8()
            allOptions[1].writePayload.single().payload shouldBe "v2".encodeUtf8()

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `error isolation` {
        @Test
        fun `one connector failure does not prevent other connectors from syncing`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "data"))

            // connector1 throws, connector2 should still sync
            coEvery { connector1.sync(any()) } throws RuntimeException("Network error")

            sm.sync(SyncOptions(writeData = true, readData = false, stats = false))
            advanceUntilIdle()

            // connector2 should have been called despite connector1 failure
            val opts2 = slot<SyncOptions>()
            coVerify { connector2.sync(capture(opts2)) }
            opts2.captured.writePayload.size shouldBe 1

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `sync lock contention` {
        @Test
        fun `pending flag triggers re-run after current sync completes`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "v1"))

            // Make connector1.sync() block until we release the gate
            val gate = CompletableDeferred<Unit>()
            var callCount = 0
            coEvery { connector1.sync(any()) } coAnswers {
                callCount++
                if (callCount == 1) gate.await()
            }

            // First sync acquires lock and blocks inside connector.sync()
            val firstSync = launch { sm.sync(SyncOptions(writeData = true, readData = false, stats = false)) }
            advanceUntilIdle()

            // Second sync hits tryLock failure and sets pending flag
            sm.sync(SyncOptions(writeData = true, readData = false, stats = false))
            advanceUntilIdle()

            // Release the gate — first sync completes, then re-runs due to pending flag
            gate.complete(Unit)
            advanceUntilIdle()
            firstSync.join()

            // connector.sync should have been called twice: original + pending re-run
            coVerify(exactly = 2) { connector1.sync(any()) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `no re-run when nothing is pending`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(SyncOptions(writeData = true, readData = false, stats = false))
            advanceUntilIdle()

            // Only called once — no pending flag was set
            coVerify(exactly = 1) { connector1.sync(any()) }

            job.cancel()
            advanceUntilIdle()
        }
    }
}
