package eu.darken.octi.modules.files.ui.list

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.modules.files.core.FileKey
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.modules.files.core.FileShareRepo
import eu.darken.octi.modules.files.core.FileShareService
import eu.darken.octi.modules.files.core.FileShareSettings
import eu.darken.octi.modules.files.core.PendingDelete
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobStoreQuota
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val pendingDeletes = mockk<DataStoreValue<Map<String, PendingDelete>>>().apply {
        every { flow } returns flowOf(emptyMap())
    }
    private val hintFlag = mockk<DataStoreValue<Boolean>>().apply {
        every { flow } returns flowOf(false)
    }
    private val fileShareSettings = mockk<FileShareSettings>().apply {
        every { isUsageHintDismissed } returns hintFlag
        every { pendingDeletes } returns this@FileShareListVMTest.pendingDeletes
    }

    private val selfDevice = DeviceId("self")
    private val remoteDevice = DeviceId("remote")
    private val connector = ConnectorId(ConnectorType.OCTISERVER, "test.example.com", "acc-1")
    private val now = Clock.System.now()

    private fun makeFile(
        name: String = "active.txt",
        blobKey: String = "blob-active",
        size: Long = 12L,
        availableOn: Set<String> = setOf(connector.idString),
        connectorRefs: Map<String, RemoteBlobRef> = mapOf(connector.idString to RemoteBlobRef("ref-$blobKey")),
        sharedAt: kotlin.time.Instant = now,
        expiresAt: kotlin.time.Instant = now + 2.hours,
    ) = FileShareInfo.SharedFile(
        name = name,
        mimeType = "text/plain",
        size = size,
        blobKey = blobKey,
        checksum = "abc",
        sharedAt = sharedAt,
        expiresAt = expiresAt,
        availableOn = availableOn,
        connectorRefs = connectorRefs,
    )

    private fun metaModuleData(deviceId: DeviceId, label: String) = ModuleData(
        modifiedAt = now,
        deviceId = deviceId,
        moduleId = MetaModule.MODULE_ID,
        data = MetaInfo(
            deviceLabel = label,
            deviceId = deviceId,
            octiVersionName = "1.0",
            octiGitSha = "abc",
            deviceManufacturer = "Google",
            deviceName = "Pixel",
            deviceType = MetaInfo.DeviceType.PHONE,
            deviceBootedAt = now,
            androidVersionName = "15",
            androidApiLevel = 35,
            androidSecurityPatch = null,
        ),
    )

    private fun setupBaseMocks(
        selfFiles: List<FileShareInfo.SharedFile> = emptyList(),
        otherFiles: Map<DeviceId, List<FileShareInfo.SharedFile>> = emptyMap(),
        labels: Map<DeviceId, String> = mapOf(selfDevice to "Self Pixel", remoteDevice to "Remote Pixel"),
        configuredConnectors: Map<ConnectorId, BlobStoreQuota?> = mapOf(connector to null),
    ) {
        every { syncSettings.deviceId } returns selfDevice
        val selfData = if (selfFiles.isNotEmpty()) {
            ModuleData(
                modifiedAt = now,
                deviceId = selfDevice,
                moduleId = FileShareModule.MODULE_ID,
                data = FileShareInfo(files = selfFiles),
            )
        } else null
        val others = otherFiles.map { (devId, files) ->
            ModuleData(
                modifiedAt = now,
                deviceId = devId,
                moduleId = FileShareModule.MODULE_ID,
                data = FileShareInfo(files = files),
            )
        }
        every { fileShareRepo.state } returns flowOf(
            BaseModuleRepo.State(
                moduleId = FileShareModule.MODULE_ID,
                self = selfData,
                others = others,
            )
        )
        every { fileShareRepo.isEnabled } returns flowOf(true)
        every { moduleManager.byDevice } returns flowOf(
            ModuleManager.ByDevice(
                devices = labels.mapValues { (devId, label) -> listOf(metaModuleData(devId, label)) },
            )
        )
        every { blobManager.quotas() } returns flowOf(configuredConnectors)
        every { blobManager.retryStatus } returns flowOf(emptyMap())
        every { fileShareService.transfers } returns MutableStateFlow(emptyMap())
    }

    private fun makeVM() = FileShareListVM(
        dispatcherProvider = dispatcherProvider,
        fileShareRepo = fileShareRepo,
        fileShareService = fileShareService,
        moduleManager = moduleManager,
        blobManager = blobManager,
        syncSettings = syncSettings,
        fileShareSettings = fileShareSettings,
        syncManager = syncManager,
    )

    @Test
    fun `state merges self and others into a single attributed list`() = runTest2 {
        val selfFile = makeFile(name = "mine.txt", blobKey = "blob-mine")
        val remoteFile = makeFile(name = "theirs.txt", blobKey = "blob-theirs")
        setupBaseMocks(
            selfFiles = listOf(selfFile),
            otherFiles = mapOf(remoteDevice to listOf(remoteFile)),
        )

        val vm = makeVM()
        vm.initialize(null)

        val state = vm.state.first()
        state.files.size shouldBe 2
        state.files.map { it.sharedFile.name }.toSet() shouldBe setOf("mine.txt", "theirs.txt")
        state.files.first { it.sharedFile.blobKey == "blob-mine" }.let {
            it.isOwn shouldBe true
            it.ownerDeviceId shouldBe selfDevice
            it.ownerDeviceLabel shouldBe "Self Pixel"
        }
        state.files.first { it.sharedFile.blobKey == "blob-theirs" }.let {
            it.isOwn shouldBe false
            it.ownerDeviceId shouldBe remoteDevice
            it.ownerDeviceLabel shouldBe "Remote Pixel"
        }
        state.availableDevices.map { it.deviceId } shouldContainExactly listOf(selfDevice, remoteDevice)
    }

    @Test
    fun `expired files are filtered out`() = runTest2 {
        val live = makeFile(name = "live.txt", blobKey = "blob-live", expiresAt = now + 2.hours)
        val expired = makeFile(name = "old.txt", blobKey = "blob-old", expiresAt = now - 1.hours)
        setupBaseMocks(otherFiles = mapOf(remoteDevice to listOf(live, expired)))

        val vm = makeVM()
        vm.initialize(null)
        vm.state.first().files.map { it.sharedFile.name } shouldBe listOf("live.txt")
    }

    @Test
    fun `initialize seeds activeFilters with the deviceId once`() = runTest2 {
        setupBaseMocks(
            selfFiles = listOf(makeFile(name = "mine.txt", blobKey = "blob-mine")),
            otherFiles = mapOf(remoteDevice to listOf(makeFile(name = "theirs.txt", blobKey = "blob-theirs"))),
        )

        val vm = makeVM()
        vm.initialize(remoteDevice.id)

        val state = vm.state.first()
        state.activeFilters shouldBe setOf(remoteDevice)
        state.files.map { it.sharedFile.name } shouldBe listOf("theirs.txt")
    }

    @Test
    fun `initialize with null leaves activeFilters empty`() = runTest2 {
        setupBaseMocks(
            selfFiles = listOf(makeFile(blobKey = "self-1")),
            otherFiles = mapOf(remoteDevice to listOf(makeFile(blobKey = "remote-1"))),
        )
        val vm = makeVM()
        vm.initialize(null)
        vm.state.first().activeFilters shouldBe emptySet()
    }

    @Test
    fun `onToggleFilter adds and removes per-device filters`() = runTest2 {
        setupBaseMocks(
            selfFiles = listOf(makeFile(blobKey = "self-1")),
            otherFiles = mapOf(remoteDevice to listOf(makeFile(blobKey = "remote-1"))),
        )
        val vm = makeVM()
        vm.initialize(null)

        vm.state.first().activeFilters shouldBe emptySet()
        vm.onToggleFilter(remoteDevice)
        vm.state.first().activeFilters shouldBe setOf(remoteDevice)
        vm.onToggleFilter(remoteDevice)
        vm.state.first().activeFilters shouldBe emptySet()
    }

    @Test
    fun `sort by name ascending and descending`() = runTest2 {
        setupBaseMocks(
            otherFiles = mapOf(
                remoteDevice to listOf(
                    makeFile(name = "b.txt", blobKey = "b"),
                    makeFile(name = "a.txt", blobKey = "a"),
                    makeFile(name = "c.txt", blobKey = "c"),
                )
            )
        )
        val vm = makeVM()
        vm.initialize(null)

        vm.onSortChange(FileShareListVM.SortKey.NAME) // first toggle goes descending=true
        vm.state.first().files.map { it.sharedFile.name } shouldBe listOf("c.txt", "b.txt", "a.txt")
        vm.onSortChange(FileShareListVM.SortKey.NAME) // re-tap flips
        vm.state.first().files.map { it.sharedFile.name } shouldBe listOf("a.txt", "b.txt", "c.txt")
    }

    @Test
    fun `sort by size desc`() = runTest2 {
        setupBaseMocks(
            otherFiles = mapOf(
                remoteDevice to listOf(
                    makeFile(name = "small", blobKey = "s", size = 10),
                    makeFile(name = "big", blobKey = "b", size = 1000),
                    makeFile(name = "mid", blobKey = "m", size = 100),
                )
            )
        )
        val vm = makeVM()
        vm.initialize(null)
        vm.onSortChange(FileShareListVM.SortKey.SIZE)
        vm.state.first().files.map { it.sharedFile.size } shouldBe listOf(1000L, 100L, 10L)
    }

    @Test
    fun `canOpenOrSave is false for own file when no connector ref overlaps`() = runTest2 {
        val orphan = makeFile(
            blobKey = "orphan",
            availableOn = setOf("disconnected-connector"),
            connectorRefs = mapOf("disconnected-connector" to RemoteBlobRef("dropped")),
        )
        setupBaseMocks(selfFiles = listOf(orphan))
        val vm = makeVM()
        vm.initialize(null)

        val item = vm.state.first().files.single()
        item.isOwn shouldBe true
        item.canOpenOrSave shouldBe false
    }

    @Test
    fun `canOpenOrSave is true when connector ref overlaps configured connectors`() = runTest2 {
        setupBaseMocks(selfFiles = listOf(makeFile()))
        val vm = makeVM()
        vm.initialize(null)
        vm.state.first().files.single().canOpenOrSave shouldBe true
    }

    @Test
    fun `partial delete tombstone marks row as isPendingDelete`() = runTest2 {
        val ownFile = makeFile(blobKey = "stuck", name = "stuck.txt")
        setupBaseMocks(selfFiles = listOf(ownFile))
        every { pendingDeletes.flow } returns flowOf(
            mapOf("stuck" to PendingDelete(blobKey = "stuck", remainingConnectors = setOf(connector.idString), createdAt = now))
        )

        val vm = makeVM()
        vm.initialize(null)

        val item = vm.state.first().files.single()
        item.isPendingDelete shouldBe true
    }

    @Test
    fun `onSaveFile uses the row's ownerDeviceId not the initial filter`() = runTest2 {
        val otherDevice = DeviceId("third")
        val sharedByThird = makeFile(blobKey = "third-blob")
        setupBaseMocks(
            otherFiles = mapOf(otherDevice to listOf(sharedByThird)),
            labels = mapOf(selfDevice to "Self", remoteDevice to "Remote", otherDevice to "Third"),
        )
        coEvery { fileShareService.saveFile(any(), any(), any()) } returns FileShareService.SaveResult.Success

        val vm = makeVM()
        vm.initialize(remoteDevice.id) // initial filter unrelated to the row's owner

        // remoteDevice has no files; clearing the filter exposes 'third's row.
        vm.onClearFilter(remoteDevice)
        val item = vm.state.first().files.single()
        item.ownerDeviceId shouldBe otherDevice

        val saveUri = mockk<android.net.Uri>(relaxed = true)
        vm.onSaveFile(item, saveUri)

        coVerify(exactly = 1) {
            fileShareService.saveFile(item.sharedFile, otherDevice, any())
        }
    }

    @Test
    fun `onOpenFile uses the row's ownerDeviceId and emits OpenFile event`() = runTest2 {
        val sharedByRemote = makeFile(blobKey = "remote-blob")
        setupBaseMocks(otherFiles = mapOf(remoteDevice to listOf(sharedByRemote)))
        val uri = mockk<android.net.Uri>(relaxed = true)
        coEvery { fileShareService.openFile(any(), any()) } returns FileShareService.OpenResult.Success(uri, "text/plain")

        val vm = makeVM()
        vm.initialize(null)
        val item = vm.state.first().files.single()
        vm.onOpenFile(item)

        coVerify(exactly = 1) {
            fileShareService.openFile(item.sharedFile, remoteDevice)
        }
        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.OpenFile>()
    }

    @Test
    fun `onDeleteFile clears deletingKeys even on exception`() = runTest2 {
        val ownFile = makeFile(blobKey = "del-fail")
        setupBaseMocks(selfFiles = listOf(ownFile))
        coEvery { fileShareService.deleteOwnFile(any()) } throws RuntimeException("boom")

        val vm = makeVM()
        vm.initialize(null)
        val item = vm.state.first().files.single()
        vm.onDeleteFile(item)

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.ShowMessage>()
        vm.state.first().files.single().isDeleting shouldBe false
    }

    @Test
    fun `sheetTarget keyed by FileKey resolves to the live FileItem`() = runTest2 {
        val file = makeFile(blobKey = "row-1")
        setupBaseMocks(otherFiles = mapOf(remoteDevice to listOf(file)))

        val vm = makeVM()
        vm.initialize(null)
        val item = vm.state.first().files.single()
        vm.onRowClick(item)

        val state = vm.state.first()
        state.sheetTargetKey shouldBe item.key
        state.sheetTarget shouldNotBe null
        state.sheetTarget!!.sharedFile.blobKey shouldBe "row-1"
    }

    @Test
    fun `FileKey distinguishes same blobKey across devices`() = runTest2 {
        val same = "shared-blob"
        setupBaseMocks(
            selfFiles = listOf(makeFile(name = "self-version", blobKey = same)),
            otherFiles = mapOf(remoteDevice to listOf(makeFile(name = "remote-version", blobKey = same))),
        )
        val vm = makeVM()
        vm.initialize(null)

        val items = vm.state.first().files
        items.size shouldBe 2
        items.map { it.key }.toSet().size shouldBe 2
        items.first { it.isOwn }.key shouldBe FileKey.of(selfDevice, same)
        items.first { !it.isOwn }.key shouldBe FileKey.of(remoteDevice, same)
    }
}
