package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant

class SyncManagerSyncEventsTest : BaseTest() {

    private val connectorId1 = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test", account = "acc1")
    private val connectorId2 = ConnectorId(type = ConnectorType.GDRIVE, subtype = "test", account = "acc2")
    private val deviceId = DeviceId("device-1")
    private val moduleId1 = ModuleId("eu.darken.octi.module.core.power")
    private val moduleId2 = ModuleId("eu.darken.octi.module.core.meta")
    private val moduleId3 = ModuleId("eu.darken.octi.module.core.wifi")

    private fun mockEvent(
        connectorId: ConnectorId = connectorId1,
        moduleId: ModuleId = moduleId1,
    ) = SyncEvent.ModuleChanged(
        connectorId = connectorId,
        deviceId = deviceId,
        moduleId = moduleId,
        modifiedAt = Instant.now(),
        action = SyncEvent.ModuleChanged.Action.UPDATED,
    )

    private fun createEventFlow() = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)

    private fun mockConnector(
        id: ConnectorId,
        events: MutableSharedFlow<SyncEvent>,
    ): SyncConnector = mockk(relaxed = true) {
        every { identifier } returns id
        every { syncEvents } returns events
    }

    private fun createMergedFlow(connectorsFlow: MutableStateFlow<List<SyncConnector>>) =
        connectorsFlow.flatMapLatest { cons ->
            if (cons.isEmpty()) emptyFlow()
            else cons.map { it.syncEvents }.merge()
        }

    @Nested
    inner class `event merging` {
        @Test
        fun `merges events from multiple connectors`() = runTest2 {
            val events1 = createEventFlow()
            val events2 = createEventFlow()
            val connectorsFlow = MutableStateFlow(
                listOf(mockConnector(connectorId1, events1), mockConnector(connectorId2, events2)),
            )

            val received = mutableListOf<SyncEvent>()
            val job = launch { createMergedFlow(connectorsFlow).toList(received) }
            advanceUntilIdle()

            events1.emit(mockEvent(connectorId1))
            events2.emit(mockEvent(connectorId2))
            advanceUntilIdle()

            job.cancelAndJoin()

            received.size shouldBe 2
        }

        @Test
        fun `no event loss on rapid emissions`() = runTest2 {
            val events = createEventFlow()
            val connectorsFlow = MutableStateFlow(listOf(mockConnector(connectorId1, events)))

            val received = mutableListOf<SyncEvent>()
            val job = launch { createMergedFlow(connectorsFlow).toList(received) }
            advanceUntilIdle()

            events.emit(mockEvent(moduleId = moduleId1))
            events.emit(mockEvent(moduleId = moduleId2))
            events.emit(mockEvent(moduleId = moduleId3))
            advanceUntilIdle()

            job.cancelAndJoin()

            received.size shouldBe 3
        }
    }
}
