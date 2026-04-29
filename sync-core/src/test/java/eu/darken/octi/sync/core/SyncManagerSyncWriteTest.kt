package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.cache.SyncCache
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Clock

class SyncManagerSyncWriteTest : BaseTest() {

    private val connectorId1 = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test", account = "acc1")
    private val connectorId2 = ConnectorId(type = ConnectorType.GDRIVE, subtype = "test", account = "acc2")
    private val deviceId = DeviceId("device-1")
    private val powerModuleId = ModuleId("eu.darken.octi.module.core.power")
    private val wifiModuleId = ModuleId("eu.darken.octi.module.core.wifi")

    private lateinit var connector1: SyncConnector
    private lateinit var connector2: SyncConnector
    private lateinit var syncSettings: SyncSettings
    private lateinit var pauseStatesValue: MutableStateFlow<Set<ConnectorPauseState>>
    private lateinit var syncCache: SyncCache
    private lateinit var connectorHub: ConnectorHub
    private lateinit var connectorsFlow: MutableStateFlow<List<SyncConnector>>
    private lateinit var connectorSyncState: ConnectorSyncState

    private fun createModule(moduleId: ModuleId, payload: String = "test"): SyncWrite.Device.Module = mockk {
        every { this@mockk.moduleId } returns moduleId
        every { this@mockk.payload } returns payload.encodeUtf8()
    }

    private fun pausedState(
        connectorId: ConnectorId,
        reason: ConnectorPauseReason = ConnectorPauseReason.Manual,
    ) = ConnectorPauseState(connectorId = connectorId, reason = reason)

    /**
     * Set up a mocked connector that behaves like a real one for SyncManager's purposes:
     * submit() returns a fresh OperationId and, for Pause/Resume commands, side-effects the
     * paused-connectors setting (matching what the real processor would do). await() returns a
     * Succeeded terminal so execute() returns cleanly.
     */
    private fun SyncConnector.wireProcessorSideEffects() {
        val cid = this.identifier
        every { submit(any()) } answers {
            val cmd = firstArg<ConnectorCommand>()
            when (cmd) {
                is ConnectorCommand.Pause -> pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == cid }
                    .toSet() + ConnectorPauseState(cid, cmd.reason)
                ConnectorCommand.Resume -> pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == cid }
                    .toSet()
                else -> Unit
            }
            OperationId.create()
        }
        // await() takes a @JvmInline value class (OperationId). MockK unwraps value classes
        // so firstArg<OperationId>() would cast a String → use a fresh id in the response.
        coEvery { await(any()) } answers {
            ConnectorOperation.Succeeded(
                id = OperationId.create(),
                command = ConnectorCommand.Sync(),
                submittedAt = Clock.System.now(),
                startedAt = Clock.System.now(),
                finishedAt = Clock.System.now(),
            )
        }
    }

    /** Arrange a submit() that fails with [error] — execute() will rethrow it. */
    private fun SyncConnector.wireSubmitFailure(error: Throwable) {
        coEvery { await(any()) } answers {
            ConnectorOperation.Failed(
                id = OperationId.create(),
                command = ConnectorCommand.Sync(),
                submittedAt = Clock.System.now(),
                startedAt = Clock.System.now(),
                finishedAt = Clock.System.now(),
                error = error,
            )
        }
    }

    @BeforeEach
    fun setup() {
        connector1 = mockk(relaxed = true) {
            every { identifier } returns connectorId1
            every { state } returns flowOf()
            every { data } returns flowOf(null)
            every { operations } returns MutableStateFlow(emptyList())
            every { completions } returns MutableSharedFlow()
            every { syncEvents } returns flowOf()
            every { syncEventMode } returns MutableStateFlow(SyncConnector.EventMode.NONE)
        }
        connector2 = mockk(relaxed = true) {
            every { identifier } returns connectorId2
            every { state } returns flowOf()
            every { data } returns flowOf(null)
            every { operations } returns MutableStateFlow(emptyList())
            every { completions } returns MutableSharedFlow()
            every { syncEvents } returns flowOf()
            every { syncEventMode } returns MutableStateFlow(SyncConnector.EventMode.NONE)
        }
        connectorsFlow = MutableStateFlow(listOf(connector1))

        pauseStatesValue = MutableStateFlow(emptySet())
        syncSettings = mockk(relaxed = true) {
            every { connectorPauseStates } returns pauseStatesValue
            every { pausedConnectorIds } returns pauseStatesValue.map { it.connectorIds }
            coEvery { isPaused(any()) } coAnswers {
                pauseStatesValue.value.reasonFor(firstArg<ConnectorId>()) != null
            }
            coEvery { pauseReason(any()) } coAnswers {
                pauseStatesValue.value.reasonFor(firstArg<ConnectorId>())
            }
            coEvery { pauseConnector(any(), any()) } coAnswers {
                val connectorId = firstArg<ConnectorId>()
                val reason = secondArg<ConnectorPauseReason>()
                pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == connectorId }
                    .toSet() + ConnectorPauseState(connectorId, reason)
            }
            coEvery { resumeConnector(any()) } coAnswers {
                val connectorId = firstArg<ConnectorId>()
                pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == connectorId }
                    .toSet()
            }
            coEvery { migrateLegacyPauseStates() } coAnswers { }
        }
        every { syncSettings.deviceId } returns deviceId

        syncCache = mockk(relaxed = true)

        connectorHub = mockk(relaxed = true) {
            every { connectors } returns connectorsFlow
        }

        connectorSyncState = ConnectorSyncState()

        connector1.wireProcessorSideEffects()
        connector2.wireProcessorSideEffects()
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

    /** Extract Sync options from a captured ConnectorCommand.Sync — or fail if wrong type. */
    private fun CapturingSlot<ConnectorCommand>.syncOptions(): SyncOptions {
        return (captured as ConnectorCommand.Sync).options
    }

    @Nested
    inner class `payload updates` {
        @Test
        fun `updatePayload stores module and sync passes it via writePayload`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "battery-data"))

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val cmdSlot = slot<ConnectorCommand>()
            coVerify { connector1.submit(capture(cmdSlot)) }
            cmdSlot.syncOptions().writePayload.single().module.moduleId shouldBe powerModuleId

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

            val cmdSlot = slot<ConnectorCommand>()
            coVerify { connector1.submit(capture(cmdSlot)) }
            cmdSlot.syncOptions().writePayload.map { it.module.moduleId } shouldContainExactlyInAnyOrder
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

            val cmdSlot = slot<ConnectorCommand>()
            coVerify { connector1.submit(capture(cmdSlot)) }
            cmdSlot.syncOptions().writePayload.size shouldBe 1
            cmdSlot.syncOptions().writePayload.single().module.payload shouldBe "new-data".encodeUtf8()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `writeData false does not include payload`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(connectorId1, SyncOptions(writeData = false, readData = false, stats = false))

            val cmdSlot = slot<ConnectorCommand>()
            coVerify { connector1.submit(capture(cmdSlot)) }
            cmdSlot.syncOptions().writePayload shouldBe emptyList()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `no payloads results in empty writePayload`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val cmdSlot = slot<ConnectorCommand>()
            coVerify { connector1.submit(capture(cmdSlot)) }
            cmdSlot.syncOptions().writePayload shouldBe emptyList()

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `hash deduplication` {
        // Hash writes are now the connector's responsibility. Tests seed the hash after a
        // simulated successful sync and verify SyncManager filters accordingly.
        @Test
        fun `hash match skips module on second sync`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val module = createModule(powerModuleId, "data")
            sm.updatePayload(module)

            // First sync — hash mismatch, module included
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            val firstCmds = mutableListOf<ConnectorCommand>()
            coVerify { connector1.submit(capture(firstCmds)) }
            (firstCmds.first() as ConnectorCommand.Sync).options.writePayload.size shouldBe 1

            // Simulate connector setting the hash after successful write
            val hash = module.payload.sha256().hex()
            connectorSyncState.setHash(connectorId1, powerModuleId, hash)

            // Second sync — hash matches, module skipped
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            val allCmds = mutableListOf<ConnectorCommand>()
            coVerify(exactly = 2) { connector1.submit(capture(allCmds)) }
            (allCmds.last() as ConnectorCommand.Sync).options.writePayload shouldBe emptyList()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `hash mismatch includes module after payload update`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val oldModule = createModule(powerModuleId, "old-data")
            sm.updatePayload(oldModule)
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            // Simulate connector writing old hash
            connectorSyncState.setHash(connectorId1, powerModuleId, oldModule.payload.sha256().hex())

            // Update payload with new data
            sm.updatePayload(createModule(powerModuleId, "new-data"))
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val allCmds = mutableListOf<ConnectorCommand>()
            coVerify(exactly = 2) { connector1.submit(capture(allCmds)) }
            val lastOptions = (allCmds.last() as ConnectorCommand.Sync).options
            lastOptions.writePayload.size shouldBe 1
            lastOptions.writePayload.single().module.payload shouldBe "new-data".encodeUtf8()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `new connector gets all modules — empty hashes`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val module = createModule(powerModuleId, "data")
            sm.updatePayload(module)

            // Sync connector1 first
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            connectorSyncState.setHash(connectorId1, powerModuleId, module.payload.sha256().hex())

            // Connector2 has never synced — should get the module
            sm.sync(connectorId2, SyncOptions(writeData = true, readData = false, stats = false))

            val cmd2 = slot<ConnectorCommand>()
            coVerify { connector2.submit(capture(cmd2)) }
            (cmd2.captured as ConnectorCommand.Sync).options.writePayload.size shouldBe 1

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `multiple connectors have independent hash tracking`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val module = createModule(powerModuleId, "data")
            sm.updatePayload(module)
            val hash = module.payload.sha256().hex()

            // Sync both connectors
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            connectorSyncState.setHash(connectorId1, powerModuleId, hash)
            sm.sync(connectorId2, SyncOptions(writeData = true, readData = false, stats = false))
            connectorSyncState.setHash(connectorId2, powerModuleId, hash)

            // Both should have received the module on first sync
            val cmd1 = slot<ConnectorCommand>()
            coVerify { connector1.submit(capture(cmd1)) }
            (cmd1.captured as ConnectorCommand.Sync).options.writePayload.size shouldBe 1

            val cmd2 = slot<ConnectorCommand>()
            coVerify { connector2.submit(capture(cmd2)) }
            (cmd2.captured as ConnectorCommand.Sync).options.writePayload.size shouldBe 1

            // Second sync for both — hashes match, both empty
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            sm.sync(connectorId2, SyncOptions(writeData = true, readData = false, stats = false))

            val all1 = mutableListOf<ConnectorCommand>()
            coVerify(exactly = 2) { connector1.submit(capture(all1)) }
            (all1.last() as ConnectorCommand.Sync).options.writePayload shouldBe emptyList()

            val all2 = mutableListOf<ConnectorCommand>()
            coVerify(exactly = 2) { connector2.submit(capture(all2)) }
            (all2.last() as ConnectorCommand.Sync).options.writePayload shouldBe emptyList()

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

            // First sync fails — connector reports Failed, execute() throws
            connector1.wireSubmitFailure(RuntimeException("Network error"))
            runCatching { sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false)) }

            // No hash was set — simulate normal (non-failing) sync
            connector1.wireProcessorSideEffects()
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val all = mutableListOf<ConnectorCommand>()
            coVerify(exactly = 2) { connector1.submit(capture(all)) }
            (all.last() as ConnectorCommand.Sync).options.writePayload.size shouldBe 1

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

            coVerify(exactly = 0) { connector1.submit(any()) }

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

            pauseStatesValue.value = setOf(pausedState(connectorId1))
            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            coVerify(exactly = 0) { connector1.submit(match { it is ConnectorCommand.Sync }) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `resetData is a no-op when connector is paused`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pauseStatesValue.value = setOf(pausedState(connectorId1))

            sm.resetData(connectorId1)

            coVerify(exactly = 0) { connector1.submit(match { it is ConnectorCommand.Reset }) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `global sync() skips paused connectors`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pauseStatesValue.value = setOf(pausedState(connectorId1))
            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(SyncOptions(writeData = true, readData = false, stats = false))
            advanceUntilIdle()

            coVerify(exactly = 0) { connector1.submit(match { it is ConnectorCommand.Sync }) }
            coVerify(exactly = 1) { connector2.submit(match { it is ConnectorCommand.Sync }) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `unpausing resumes sync`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pauseStatesValue.value = setOf(pausedState(connectorId1))
            sm.updatePayload(createModule(powerModuleId, "data"))

            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            coVerify(exactly = 0) { connector1.submit(match { it is ConnectorCommand.Sync }) }

            pauseStatesValue.value = emptySet()
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            coVerify(exactly = 1) { connector1.submit(match { it is ConnectorCommand.Sync }) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `togglePause pause submits Pause command and flips setting`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.togglePause(connectorId1, paused = true)
            advanceUntilIdle()

            coVerify { connector1.submit(ConnectorCommand.Pause()) }
            pauseStatesValue.value shouldBe setOf(pausedState(connectorId1))

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `togglePause unpause submits Resume command and flips setting`() = runTest2 {
            pauseStatesValue.value = setOf(pausedState(connectorId1))
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.togglePause(connectorId1, paused = false)
            advanceUntilIdle()

            coVerify { connector1.submit(ConnectorCommand.Resume) }
            // Processor-side post-resume Sync is covered by ConnectorProcessor tests; here we
            // only verify SyncManager delegated correctly.
            pauseStatesValue.value shouldBe emptySet()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `togglePause no-op when already in target state`() = runTest2 {
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            // Already unpaused — toggle to unpaused = no-op
            sm.togglePause(connectorId1, paused = false)
            advanceUntilIdle()

            coVerify(exactly = 0) { connector1.submit(any()) }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `disconnect clears pause reason`() = runTest2 {
            pauseStatesValue.value = setOf(pausedState(connectorId1, ConnectorPauseReason.AuthIssue))
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.disconnect(connectorId1)
            advanceUntilIdle()

            pauseStatesValue.value shouldBe emptySet()

            job.cancel()
            advanceUntilIdle()
        }

        // See plan section "Auto-sync-after-resume" — the sync after Resume is now enqueued as
        // a side effect of the processor's Resume handling, so it's strictly ordered in the
        // queue relative to any subsequent Pause. That behavior is covered by
        // ConnectorProcessorTest; SyncManager no longer launches a separate post-unpause sync.
        // Therefore there is no SyncManager-level "re-pause races auto-sync" scenario anymore.

        @Test
        fun `connectors flow excludes paused entries`() = runTest2 {
            connectorsFlow.value = listOf(connector1, connector2)
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            pauseStatesValue.value = setOf(pausedState(connectorId1))
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

            pauseStatesValue.value = setOf(pausedState(connectorId1))
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

            val v1 = createModule(powerModuleId, "v1")
            sm.updatePayload(v1)
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))
            // Simulate connector writing hash after success
            connectorSyncState.setHash(connectorId1, powerModuleId, v1.payload.sha256().hex())

            // Payload updated
            sm.updatePayload(createModule(powerModuleId, "v2"))
            sm.sync(connectorId1, SyncOptions(writeData = true, readData = false, stats = false))

            val all = mutableListOf<ConnectorCommand>()
            coVerify(exactly = 2) { connector1.submit(capture(all)) }
            (all[0] as ConnectorCommand.Sync).options.writePayload.single().module.payload shouldBe "v1".encodeUtf8()
            (all[1] as ConnectorCommand.Sync).options.writePayload.single().module.payload shouldBe "v2".encodeUtf8()

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

            // connector1 reports Failed, connector2 should still sync
            connector1.wireSubmitFailure(RuntimeException("Network error"))

            sm.sync(SyncOptions(writeData = true, readData = false, stats = false))
            advanceUntilIdle()

            // connector2 should have been called despite connector1 failure
            val cmd2 = slot<ConnectorCommand>()
            coVerify { connector2.submit(capture(cmd2)) }
            (cmd2.captured as ConnectorCommand.Sync).options.writePayload.size shouldBe 1

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

            // Make connector1.await() block until we release the gate, simulating a long sync.
            val gate = CompletableDeferred<Unit>()
            var callCount = 0
            coEvery { connector1.await(any()) } coAnswers {
                callCount++
                if (callCount == 1) gate.await()
                ConnectorOperation.Succeeded(
                    id = OperationId.create(),
                    command = ConnectorCommand.Sync(),
                    submittedAt = Clock.System.now(),
                    startedAt = Clock.System.now(),
                    finishedAt = Clock.System.now(),
                )
            }

            // First sync acquires lock and blocks inside connector.await()
            val firstSync = launch { sm.sync(SyncOptions(writeData = true, readData = false, stats = false)) }
            advanceUntilIdle()

            // Second sync hits tryLock failure and sets pending flag
            sm.sync(SyncOptions(writeData = true, readData = false, stats = false))
            advanceUntilIdle()

            // Release the gate — first sync completes, then re-runs due to pending flag
            gate.complete(Unit)
            advanceUntilIdle()
            firstSync.join()

            // connector.submit should have been called twice: original + pending re-run
            coVerify(exactly = 2) { connector1.submit(match { it is ConnectorCommand.Sync }) }

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

            coVerify(exactly = 1) { connector1.submit(match { it is ConnectorCommand.Sync }) }

            job.cancel()
            advanceUntilIdle()
        }
    }
}
