package eu.darken.octi.module.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.ByteString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant

class BaseModuleSyncTest : BaseTest() {

    @MockK lateinit var syncManager: SyncManager
    @MockK lateinit var syncSettings: SyncSettings
    @MockK lateinit var dispatcherProvider: DispatcherProvider

    private val testDeviceId = DeviceId("test-device")
    private val otherDeviceId = DeviceId("other-device")
    private val testModuleId = ModuleId("test.module")
    private val testConnectorId = ConnectorId(type = "test", subtype = "test", account = "test")

    private lateinit var sync: TestModuleSync

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        every { syncSettings.deviceId } returns testDeviceId
        every { syncManager.data } returns flowOf(emptyList())

        sync = TestModuleSync(
            moduleId = testModuleId,
            dispatcherProvider = dispatcherProvider,
            syncSettings = syncSettings,
            syncManager = syncManager,
        )
    }

    private fun createSyncReadDevice(
        deviceId: DeviceId,
        moduleId: ModuleId,
        payload: String,
    ): SyncRead.Device = object : SyncRead.Device {
        override val deviceId = deviceId
        override val modules = listOf(
            object : SyncRead.Device.Module {
                override val connectorId = testConnectorId
                override val deviceId = deviceId
                override val moduleId = moduleId
                override val modifiedAt = Instant.now()
                override val payload: ByteString = ByteString.of(*payload.toByteArray())
            }
        )
    }

    private fun createSync(
        dataFlow: MutableSharedFlow<Collection<SyncRead.Device>>,
        serializer: ModuleSerializer<String> = defaultSerializer,
    ): TestModuleSync {
        every { syncManager.data } returns dataFlow
        return TestModuleSync(
            moduleId = testModuleId,
            dispatcherProvider = dispatcherProvider,
            syncSettings = syncSettings,
            syncManager = syncManager,
            moduleSerializer = serializer,
        )
    }

    @Nested
    inner class `write tracking` {
        @Test
        fun `syncActivity is IDLE initially`() = runTest2 {
            sync.syncActivity.first() shouldBe ModuleSync.SyncActivity.IDLE
            sync.isSyncing.first() shouldBe false
        }

        @Test
        fun `syncActivity is WRITING during active sync`() = runTest2 {
            coEvery { syncManager.write(any<SyncWrite.Device.Module>()) } coAnswers {
                sync.syncActivity.first() shouldBe ModuleSync.SyncActivity.WRITING
                sync.isSyncing.first() shouldBe true
            }

            val data = ModuleData(
                modifiedAt = Instant.now(),
                deviceId = testDeviceId,
                moduleId = testModuleId,
                data = "test-data",
            )
            sync.sync(data)

            sync.isSyncing.first() shouldBe false
        }

        @Test
        fun `syncActivity is IDLE after sync throws`() = runTest2 {
            coEvery { syncManager.write(any<SyncWrite.Device.Module>()) } throws RuntimeException("Sync failed")

            val data = ModuleData(
                modifiedAt = Instant.now(),
                deviceId = testDeviceId,
                moduleId = testModuleId,
                data = "test-data",
            )

            try {
                sync.sync(data)
            } catch (_: RuntimeException) {
                // Expected
            }

            sync.isSyncing.first() shouldBe false
        }

        @Test
        fun `syncActivity is IDLE after cancellation`() = runTest2 {
            coEvery { syncManager.write(any<SyncWrite.Device.Module>()) } coAnswers {
                kotlinx.coroutines.awaitCancellation()
            }

            val data = ModuleData(
                modifiedAt = Instant.now(),
                deviceId = testDeviceId,
                moduleId = testModuleId,
                data = "test-data",
            )

            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                sync.sync(data)
            }
            job.cancel()
            job.join()

            sync.isSyncing.first() shouldBe false
        }
    }

    @Nested
    inner class `read tracking` {
        @Test
        fun `isSyncing is false after others read completes`() = runTest2 {
            val dataFlow = MutableSharedFlow<Collection<SyncRead.Device>>()
            val testSync = createSync(dataFlow)

            val othersJob = testSync.others.launchIn(this)

            dataFlow.emit(listOf(createSyncReadDevice(otherDeviceId, testModuleId, "hello")))

            testSync.isSyncing.first() shouldBe false

            othersJob.cancel()
        }

        @Test
        fun `isSyncing is false after others deserialization error`() = runTest2 {
            val dataFlow = MutableSharedFlow<Collection<SyncRead.Device>>()
            val failingSerializer = object : ModuleSerializer<String> {
                override fun serialize(item: String): ByteString = ByteString.of(*item.toByteArray())
                override fun deserialize(raw: ByteString): String = throw RuntimeException("Deserialization failed")
            }
            val testSync = createSync(dataFlow, failingSerializer)

            val othersJob = testSync.others.launchIn(this)

            dataFlow.emit(listOf(createSyncReadDevice(otherDeviceId, testModuleId, "bad-data")))

            testSync.isSyncing.first() shouldBe false

            othersJob.cancel()
        }

        @Test
        fun `isSyncing stays true when read overlaps with ongoing write`() = runTest2 {
            val dataFlow = MutableSharedFlow<Collection<SyncRead.Device>>()
            val testSync = createSync(dataFlow)
            coEvery { syncManager.write(any<SyncWrite.Device.Module>()) } coAnswers {
                kotlinx.coroutines.awaitCancellation()
            }

            val othersJob = testSync.others.launchIn(this)

            // Start a write that suspends indefinitely
            val writeJob = launch(UnconfinedTestDispatcher(testScheduler)) {
                testSync.sync(
                    ModuleData(
                        modifiedAt = Instant.now(),
                        deviceId = testDeviceId,
                        moduleId = testModuleId,
                        data = "write-data",
                    )
                )
            }

            // isSyncing should be true from the write
            testSync.isSyncing.first() shouldBe true

            // Emit read data while write is still in progress - counter goes 1->2->1
            dataFlow.emit(listOf(createSyncReadDevice(otherDeviceId, testModuleId, "read-data")))

            // Still syncing because write is ongoing
            testSync.isSyncing.first() shouldBe true

            // Cancel write
            writeJob.cancel()
            writeJob.join()

            testSync.isSyncing.first() shouldBe false

            othersJob.cancel()
        }
    }

    private class TestModuleSync(
        moduleId: ModuleId,
        dispatcherProvider: DispatcherProvider,
        syncSettings: SyncSettings,
        syncManager: SyncManager,
        moduleSerializer: ModuleSerializer<String> = defaultSerializer,
    ) : BaseModuleSync<String>(
        moduleId = moduleId,
        tag = "Test:Sync",
        dispatcherProvider = dispatcherProvider,
        syncSettings = syncSettings,
        syncManager = syncManager,
        moduleSerializer = moduleSerializer,
    )

    companion object {
        private val defaultSerializer = object : ModuleSerializer<String> {
            override fun serialize(item: String): ByteString = ByteString.of(*item.toByteArray())
            override fun deserialize(raw: ByteString): String = raw.utf8()
        }
    }
}
