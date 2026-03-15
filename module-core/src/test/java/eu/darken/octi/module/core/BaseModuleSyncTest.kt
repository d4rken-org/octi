package eu.darken.octi.module.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant

class BaseModuleSyncTest : BaseTest() {

    @MockK lateinit var syncManager: SyncManager
    @MockK lateinit var syncSettings: SyncSettings
    @MockK lateinit var dispatcherProvider: DispatcherProvider

    private val testDeviceId = DeviceId("test-device")
    private val testModuleId = ModuleId("test.module")

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

    @Test
    fun `isSyncing is false initially`() = runTest2 {
        sync.isSyncing.first() shouldBe false
    }

    @Test
    fun `isSyncing is true during active sync`() = runTest2 {
        coEvery { syncManager.write(any<SyncWrite.Device.Module>()) } coAnswers {
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
    fun `isSyncing is false after sync throws`() = runTest2 {
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
    fun `isSyncing is false after cancellation`() = runTest2 {
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

    private class TestModuleSync(
        moduleId: ModuleId,
        dispatcherProvider: DispatcherProvider,
        syncSettings: SyncSettings,
        syncManager: SyncManager,
    ) : BaseModuleSync<String>(
        moduleId = moduleId,
        tag = "Test:Sync",
        dispatcherProvider = dispatcherProvider,
        syncSettings = syncSettings,
        syncManager = syncManager,
        moduleSerializer = object : ModuleSerializer<String> {
            override fun serialize(item: String): okio.ByteString = okio.ByteString.of(*item.toByteArray())
            override fun deserialize(raw: okio.ByteString): String = raw.utf8()
        },
    )
}
