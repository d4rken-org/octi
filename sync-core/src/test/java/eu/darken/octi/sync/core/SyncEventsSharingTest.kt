package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class SyncEventsSharingTest : BaseTest() {

    private val connectorId = ConnectorId(type = "kserver", subtype = "test", account = "acc1")
    private val deviceId = DeviceId("device-1")
    private val moduleId = ModuleId("eu.darken.octi.module.core.power")

    private fun mockEvent() = SyncEvent.ModuleChanged(
        connectorId = connectorId,
        deviceId = deviceId,
        moduleId = moduleId,
        modifiedAt = Instant.now(),
        action = SyncEvent.ModuleChanged.Action.UPDATED,
    )

    @Nested
    inner class `connector-level sharing` {
        @Test
        fun `multiple subscribers share one upstream subscription`() = runTest2(autoCancel = true) {
            val subscriptionCount = AtomicInteger(0)
            val events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)

            // Simulate a connector's syncEvents with shareIn — tracks how many times upstream is subscribed
            val sharedFlow = events
                .onStart { subscriptionCount.incrementAndGet() }
                .shareIn(this, SharingStarted.WhileSubscribed(), replay = 0)

            val received1 = mutableListOf<SyncEvent>()
            val received2 = mutableListOf<SyncEvent>()
            val job1 = launch { sharedFlow.toList(received1) }
            val job2 = launch { sharedFlow.toList(received2) }
            advanceUntilIdle()

            events.emit(mockEvent())
            advanceUntilIdle()

            // Both subscribers received the event
            received1.size shouldBe 1
            received2.size shouldBe 1

            // But upstream was subscribed only ONCE (shared)
            subscriptionCount.get() shouldBe 1

            job1.cancelAndJoin()
            job2.cancelAndJoin()
        }

        @Test
        fun `upstream cancelled when all subscribers leave`() = runTest2(autoCancel = true) {
            val subscriptionCount = AtomicInteger(0)
            val cancellationCount = AtomicInteger(0)
            val events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)

            val sharedFlow = events
                .onStart { subscriptionCount.incrementAndGet() }
                .shareIn(this, SharingStarted.WhileSubscribed(), replay = 0)

            val job1 = launch { sharedFlow.collect {} }
            val job2 = launch { sharedFlow.collect {} }
            advanceUntilIdle()

            subscriptionCount.get() shouldBe 1

            // Cancel first subscriber — upstream should still be active
            job1.cancelAndJoin()
            advanceUntilIdle()

            // Cancel last subscriber — upstream should be cancelled
            job2.cancelAndJoin()
            advanceUntilIdle()

            // Re-subscribe — should start a NEW upstream subscription
            val job3 = launch { sharedFlow.collect {} }
            advanceUntilIdle()

            subscriptionCount.get() shouldBe 2

            job3.cancelAndJoin()
        }
    }

    @Nested
    inner class `manager-level sharing` {
        @Test
        fun `SyncManager merge does not duplicate connector subscriptions`() = runTest2(autoCancel = true) {
            val subscriptionCount = AtomicInteger(0)
            val events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)

            val connector: SyncConnector = mockk(relaxed = true) {
                every { identifier } returns connectorId
                // Connector already shares via shareIn, so onStart counts connector-level subscriptions
                every { syncEvents } returns events
                    .onStart { subscriptionCount.incrementAndGet() }
                    .shareIn(this@runTest2, SharingStarted.WhileSubscribed(), replay = 0)
            }

            val connectorsFlow = MutableStateFlow(listOf(connector))

            // Replicate SyncManager.syncEvents composition
            val managerEvents = connectorsFlow
                .flatMapLatest { cons ->
                    if (cons.isEmpty()) emptyFlow()
                    else cons.map { it.syncEvents }.merge()
                }
                .shareIn(this, SharingStarted.WhileSubscribed(), replay = 0)

            // Two subscribers at the manager level
            val job1 = launch { managerEvents.collect {} }
            val job2 = launch { managerEvents.collect {} }
            advanceUntilIdle()

            // Connector's upstream should only be subscribed once
            subscriptionCount.get() shouldBe 1

            job1.cancelAndJoin()
            job2.cancelAndJoin()
        }
    }
}
