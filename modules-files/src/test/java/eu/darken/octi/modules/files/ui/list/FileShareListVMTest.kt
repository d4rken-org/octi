package eu.darken.octi.modules.files.ui.list

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.modules.files.core.FileShareRepo
import eu.darken.octi.modules.files.core.FileShareService
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FileShareListVMTest : BaseTest() {

    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()
    private val fileShareRepo = mockk<FileShareRepo>()
    private val fileShareService = mockk<FileShareService>(relaxed = true)
    private val moduleManager = mockk<ModuleManager>()
    private val blobManager = mockk<BlobManager>()
    private val syncSettings = mockk<SyncSettings>()

    @Test
    fun `state hides expired files and marks unavailable files from non-overlapping connectors`() = runTest2 {
        val selfDevice = DeviceId("self")
        val remoteDevice = DeviceId("remote")
        val connector = ConnectorId(ConnectorType.OCTISERVER, "test.example.com", "acc-1")
        val now = Clock.System.now()

        val activeFile = FileShareInfo.SharedFile(
            name = "active.txt",
            mimeType = "text/plain",
            size = 12,
            blobKey = "blob-active",
            checksum = "abc",
            sharedAt = now,
            expiresAt = now + 2.hours,
            availableOn = setOf("other-connector"),
        )
        val expiredFile = activeFile.copy(
            name = "expired.txt",
            blobKey = "blob-expired",
            expiresAt = now - 1.hours,
        )

        every { syncSettings.deviceId } returns selfDevice
        every { fileShareRepo.state } returns flowOf(
            BaseModuleRepo.State(
                moduleId = FileShareModule.MODULE_ID,
                others = listOf(
                    ModuleData(
                        modifiedAt = now,
                        deviceId = remoteDevice,
                        moduleId = FileShareModule.MODULE_ID,
                        data = FileShareInfo(files = listOf(activeFile, expiredFile)),
                    )
                ),
            )
        )
        every { moduleManager.byDevice } returns flowOf(
            ModuleManager.ByDevice(
                devices = mapOf(
                    remoteDevice to listOf(
                        ModuleData(
                            modifiedAt = now,
                            deviceId = remoteDevice,
                            moduleId = MetaModule.MODULE_ID,
                            data = MetaInfo(
                                deviceLabel = "Remote Pixel",
                                deviceId = remoteDevice,
                                octiVersionName = "1.0",
                                octiGitSha = "abc123",
                                deviceManufacturer = "Google",
                                deviceName = "Pixel",
                                deviceType = MetaInfo.DeviceType.PHONE,
                                deviceBootedAt = now,
                                androidVersionName = "15",
                                androidApiLevel = 35,
                                androidSecurityPatch = null,
                            ),
                        )
                    )
                )
            )
        )
        every { blobManager.quotas() } returns flowOf(mapOf(connector to null))
        every { fileShareService.transfers } returns MutableStateFlow(emptyMap())

        val vm = FileShareListVM(
            dispatcherProvider = dispatcherProvider,
            fileShareRepo = fileShareRepo,
            fileShareService = fileShareService,
            moduleManager = moduleManager,
            blobManager = blobManager,
            syncSettings = syncSettings,
        )
        vm.initialize(remoteDevice.id)

        val state = vm.state.first()

        state.deviceLabel shouldBe "Remote Pixel"
        state.files.size shouldBe 1
        state.files.single().sharedFile.name shouldBe "active.txt"
        state.files.single().isAvailable shouldBe false
    }
}
