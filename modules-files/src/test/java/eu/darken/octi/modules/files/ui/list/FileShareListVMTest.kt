package eu.darken.octi.modules.files.ui.list

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.modules.files.core.FileKey
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.modules.files.core.FileShareRepo
import eu.darken.octi.modules.files.core.FileShareService
import eu.darken.octi.modules.files.core.FileShareSettings
import eu.darken.octi.modules.files.core.IncomingShareInbox
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
import eu.darken.octi.sync.core.blob.StorageSnapshot
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.core.blob.StorageStatusManager
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FileShareListVMTest : BaseTest() {

    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()
    private val fileShareRepo = mockk<FileShareRepo>()
    private val fileShareService = mockk<FileShareService>(relaxed = true)
    private val moduleManager = mockk<ModuleManager>()
    private val blobManager = mockk<BlobManager>()
    private val storageStatusManager = mockk<StorageStatusManager>(relaxed = true)
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
        configuredConnectors: Map<ConnectorId, StorageStatus> = mapOf(connector to StorageStatus.Loading(connector, lastKnown = null)),
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
        every { storageStatusManager.statuses } returns flowOf(configuredConnectors)
        every { storageStatusManager.configuredConnectorIds } returns flowOf(configuredConnectors.keys)
        every { blobManager.retryStatus } returns flowOf(emptyMap())
        every { fileShareService.transfers } returns MutableStateFlow(emptyMap())
    }

    private val incomingShareInbox = IncomingShareInbox()

    // Existing tests assume unrestricted uploads — default to Pro so the free-tier gate
    // doesn't accidentally drop files. Free-tier tests build their own fake upgradeRepo.
    private fun fakeUpgradeRepo(isPro: Boolean): UpgradeRepo {
        val info = object : UpgradeRepo.Info {
            override val type = UpgradeRepo.Type.FOSS
            override val isPro = isPro
            override val upgradedAt: kotlin.time.Instant? = null
        }
        return mockk<UpgradeRepo>(relaxed = true).apply {
            every { upgradeInfo } returns flowOf(info)
        }
    }

    private fun makeVM(upgradeRepo: UpgradeRepo = fakeUpgradeRepo(isPro = true)) = FileShareListVM(
        dispatcherProvider = dispatcherProvider,
        appScope = CoroutineScope(SupervisorJob()),
        fileShareRepo = fileShareRepo,
        fileShareService = fileShareService,
        moduleManager = moduleManager,
        blobManager = blobManager,
        storageStatusManager = storageStatusManager,
        syncSettings = syncSettings,
        fileShareSettings = fileShareSettings,
        syncManager = syncManager,
        incomingShareInbox = incomingShareInbox,
        upgradeRepo = upgradeRepo,
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
    fun `initialize does not seed activeFilters - list opens unfiltered regardless of route deviceId`() = runTest2 {
        // Previously, opening the list from a peer's dashboard tile pre-filtered to that peer.
        // That made the screen feel inconsistent depending on entry point. Now `initialize` is
        // a no-op for filters and the list always opens with all devices visible.
        setupBaseMocks(
            selfFiles = listOf(makeFile(name = "mine.txt", blobKey = "blob-mine")),
            otherFiles = mapOf(remoteDevice to listOf(makeFile(name = "theirs.txt", blobKey = "blob-theirs"))),
        )

        val vm = makeVM()
        vm.initialize(remoteDevice.id) // would have pre-seeded the filter under the old behavior

        val state = vm.state.first()
        state.activeFilters shouldBe emptySet()
        state.files.map { it.sharedFile.name }.toSet() shouldBe setOf("mine.txt", "theirs.txt")
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
    fun `onToggleFilter from empty deselects that one device`() = runTest2 {
        setupBaseMocks(
            selfFiles = listOf(makeFile(blobKey = "self-1")),
            otherFiles = mapOf(remoteDevice to listOf(makeFile(blobKey = "remote-1"))),
        )
        val vm = makeVM()
        vm.initialize(null)

        vm.state.first().activeFilters shouldBe emptySet()
        // From the "all selected" canonical empty state, tapping remoteDevice should hide just
        // that device — filters become (available - {remoteDevice}) = {selfDevice}.
        vm.onToggleFilter(remoteDevice)
        vm.state.first().activeFilters shouldBe setOf(selfDevice)
        // Re-adding remoteDevice means everything is selected again → canonicalize to empty.
        vm.onToggleFilter(remoteDevice)
        vm.state.first().activeFilters shouldBe emptySet()
    }

    @Test
    fun `onToggleFilter on the last selected chip resets to empty`() = runTest2 {
        val third = DeviceId("third")
        setupBaseMocks(
            selfFiles = listOf(makeFile(blobKey = "self-1")),
            otherFiles = mapOf(
                remoteDevice to listOf(makeFile(blobKey = "remote-1")),
                third to listOf(makeFile(blobKey = "third-1")),
            ),
            labels = mapOf(selfDevice to "Self", remoteDevice to "Remote", third to "Third"),
        )
        val vm = makeVM()
        vm.initialize(null)
        // Bring filter down to {remote} via the public API (initialize no longer seeds filters).
        vm.onToggleFilter(selfDevice) // empty → {remote, third}
        vm.onToggleFilter(third)      // → {remote}
        vm.state.first().activeFilters shouldBe setOf(remoteDevice)

        // Tapping the only selected chip should empty the filter (= back to all-selected).
        vm.onToggleFilter(remoteDevice)
        vm.state.first().activeFilters shouldBe emptySet()
    }

    @Test
    fun `onToggleFilter canonicalizes all explicitly selected to empty`() = runTest2 {
        val third = DeviceId("third")
        setupBaseMocks(
            selfFiles = listOf(makeFile(blobKey = "self-1")),
            otherFiles = mapOf(
                remoteDevice to listOf(makeFile(blobKey = "remote-1")),
                third to listOf(makeFile(blobKey = "third-1")),
            ),
            labels = mapOf(selfDevice to "Self", remoteDevice to "Remote", third to "Third"),
        )
        val vm = makeVM()
        vm.initialize(null)
        // Reach {remote} via toggles.
        vm.onToggleFilter(selfDevice)
        vm.onToggleFilter(third)
        vm.state.first().activeFilters shouldBe setOf(remoteDevice)

        vm.onToggleFilter(selfDevice) // → {remote, self}
        vm.state.first().activeFilters shouldBe setOf(remoteDevice, selfDevice)
        vm.onToggleFilter(third)      // → {remote, self, third} == all → canonicalize to empty
        vm.state.first().activeFilters shouldBe emptySet()
    }

    @Test
    fun `enqueueShareFile swallows exception so appScope does not crash`() = runTest2 {
        setupBaseMocks(selfFiles = listOf(makeFile(blobKey = "self-1")))
        coEvery { fileShareService.shareFile(any()) } throws IOException("boom")

        val vm = makeVM()
        vm.initialize(null)
        val uri = mockk<android.net.Uri>(relaxed = true)
        // Should not throw — runCatching wraps the failing call.
        vm.enqueueShareFile(uri)
    }

    @Test
    fun `enqueueSaveFile swallows exception so appScope does not crash`() = runTest2 {
        val sharedByRemote = makeFile(blobKey = "remote-blob")
        setupBaseMocks(otherFiles = mapOf(remoteDevice to listOf(sharedByRemote)))
        coEvery { fileShareService.saveFile(any(), any(), any()) } throws IOException("boom")

        val vm = makeVM()
        vm.initialize(null)
        val item = vm.state.first().files.single()
        val uri = mockk<android.net.Uri>(relaxed = true)
        // Should not throw — runCatching wraps the failing call.
        vm.enqueueSaveFile(item, uri)
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
        vm.onToggleFilter(remoteDevice)
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
    fun `quotaItems filter Unsupported and pass StorageStatus through with last-known snapshot`() = runTest2 {
        val gdriveId = ConnectorId(ConnectorType.GDRIVE, "appdatascope", "gacc")
        val gdriveSnap = StorageSnapshot(
            connectorId = gdriveId,
            accountLabel = "you@gmail.com",
            usedBytes = 1024L,
            totalBytes = 4096L,
            availableBytes = 3072L,
            maxFileBytes = null,
            perFileOverheadBytes = 0L,
            updatedAt = now,
        )
        // OctiServer entry returns Unsupported; should be filtered out so the card lists only
        // the GDrive Ready row.
        setupBaseMocks(
            otherFiles = mapOf(remoteDevice to listOf(makeFile(blobKey = "remote-1"))),
            configuredConnectors = mapOf(
                connector to StorageStatus.Unsupported(connector),
                gdriveId to StorageStatus.Ready(gdriveId, gdriveSnap),
            ),
        )

        val vm = makeVM()
        vm.initialize(null)

        val items = vm.state.first().quotaItems
        items.size shouldBe 1
        val ready = items.single()
        ready.connectorId shouldBe gdriveId
        ready.lastKnown shouldNotBe null
        ready.lastKnown!!.accountLabel shouldBe "you@gmail.com"
        ready.lastKnown!!.usedBytes shouldBe 1024L
        ready.lastKnown!!.totalBytes shouldBe 4096L
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

    @Test
    fun `onShareFilesSequential awaits each shareFile before starting the next`() = runTest2 {
        setupBaseMocks()
        // Two URIs, two deferreds — until the first one completes, the second shareFile must not
        // have been invoked. This proves the loop suspends instead of fanning out concurrently.
        val gateA = kotlinx.coroutines.CompletableDeferred<FileShareService.ShareResult>()
        val gateB = kotlinx.coroutines.CompletableDeferred<FileShareService.ShareResult>()
        val uriA = mockk<android.net.Uri>(relaxed = true)
        val uriB = mockk<android.net.Uri>(relaxed = true)
        coEvery { fileShareService.shareFile(uriA) } coAnswers { gateA.await() }
        coEvery { fileShareService.shareFile(uriB) } coAnswers { gateB.await() }

        val vm = makeVM()
        vm.initialize(null)
        vm.onShareFilesSequential(listOf(uriA, uriB))

        // Wait until shareFile(uriA) has been invoked but is still suspended on gateA.
        coVerify(timeout = 1_000) { fileShareService.shareFile(uriA) }
        // Second URI must not have started yet.
        coVerify(exactly = 0) { fileShareService.shareFile(uriB) }

        gateA.complete(FileShareService.ShareResult.Success)
        coVerify(timeout = 1_000) { fileShareService.shareFile(uriB) }
        gateB.complete(FileShareService.ShareResult.Success)
    }

    @Test
    fun `consumeIncomingShare drains the inbox and invokes onShareFilesSequential`() = runTest2 {
        setupBaseMocks()
        val uri = mockk<android.net.Uri>(relaxed = true)
        coEvery { fileShareService.shareFile(uri) } returns FileShareService.ShareResult.Success
        val token = incomingShareInbox.enqueue(listOf(uri))

        val vm = makeVM()
        vm.initialize(null)
        vm.consumeIncomingShare(token)

        coVerify(timeout = 1_000) { fileShareService.shareFile(uri) }
        // Token consumed: a second consume must be a no-op (no extra shareFile call).
        vm.consumeIncomingShare(token)
        coVerify(exactly = 1) { fileShareService.shareFile(uri) }
    }

    @Test
    fun `free user with 0 own files - onShareClick emits LaunchPicker`() = runTest2 {
        setupBaseMocks()
        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)

        vm.onShareClick(auto = false)

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.LaunchPicker>()
        event.auto shouldBe false
    }

    @Test
    fun `free user with 1 own file - onShareClick emits AtLimit`() = runTest2 {
        setupBaseMocks(selfFiles = listOf(makeFile(blobKey = "self-1")))
        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)

        vm.onShareClick(auto = true)

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.AtLimit>()
        event.auto shouldBe true
    }

    @Test
    fun `free user with 1 own file - onShareFile emits AtLimit and skips upload`() = runTest2 {
        setupBaseMocks(selfFiles = listOf(makeFile(blobKey = "self-1")))
        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)
        val uri = mockk<android.net.Uri>(relaxed = true)

        vm.onShareFile(uri)

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.AtLimit>()
        coVerify(exactly = 0) { fileShareService.shareFile(any()) }
    }

    @Test
    fun `free user batch with 0 own files - first uploads and rest dropped`() = runTest2 {
        setupBaseMocks()
        val uriA = mockk<android.net.Uri>(relaxed = true)
        val uriB = mockk<android.net.Uri>(relaxed = true)
        val uriC = mockk<android.net.Uri>(relaxed = true)
        coEvery { fileShareService.shareFile(any()) } returns FileShareService.ShareResult.Success

        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)
        vm.onShareFilesSequential(listOf(uriA, uriB, uriC))

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.AtLimitDroppedExtras>()
        event.droppedCount shouldBe 2
        coVerify(timeout = 1_000, exactly = 1) { fileShareService.shareFile(uriA) }
        coVerify(exactly = 0) { fileShareService.shareFile(uriB) }
        coVerify(exactly = 0) { fileShareService.shareFile(uriC) }
    }

    @Test
    fun `free user batch with 1 own file - all dropped`() = runTest2 {
        setupBaseMocks(selfFiles = listOf(makeFile(blobKey = "self-1")))
        val uri = mockk<android.net.Uri>(relaxed = true)

        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)
        vm.onShareFilesSequential(listOf(uri))

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.AtLimit>()
        coVerify(exactly = 0) { fileShareService.shareFile(any()) }
    }

    @Test
    fun `free user consumeIncomingShare always drains the inbox even when at limit`() = runTest2 {
        setupBaseMocks(selfFiles = listOf(makeFile(blobKey = "self-1")))
        val uri = mockk<android.net.Uri>(relaxed = true)
        val token = incomingShareInbox.enqueue(listOf(uri))

        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)
        vm.consumeIncomingShare(token)

        // Token must be drained (a re-consume returns null and is a no-op).
        incomingShareInbox.drain(token) shouldBe null
        coVerify(exactly = 0) { fileShareService.shareFile(any()) }
    }

    @Test
    fun `free user onRetryFile works regardless of own-file count`() = runTest2 {
        val ownFile = makeFile(blobKey = "stuck", availableOn = setOf("missing"))
        setupBaseMocks(selfFiles = listOf(ownFile))
        coEvery { fileShareService.retryMirror(any()) } returns Unit

        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)
        val item = vm.state.first().files.single()
        vm.onRetryFile(item)

        coVerify(timeout = 1_000, exactly = 1) { fileShareService.retryMirror("stuck") }
    }

    @Test
    fun `pro user - onShareClick emits LaunchPicker even with many own files`() = runTest2 {
        setupBaseMocks(
            selfFiles = listOf(
                makeFile(blobKey = "a"),
                makeFile(blobKey = "b"),
                makeFile(blobKey = "c"),
            )
        )
        val vm = makeVM(fakeUpgradeRepo(isPro = true))
        vm.initialize(null)

        vm.onShareClick(auto = false)

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.LaunchPicker>()
    }

    @Test
    fun `lapsed pro with N grandfathered files - upload still blocked`() = runTest2 {
        // User was Pro and uploaded 5 files, now lapsed to Free. The count is the gate, not the
        // Pro flag — those existing files block new uploads until they expire or are deleted.
        setupBaseMocks(
            selfFiles = listOf(
                makeFile(blobKey = "a"),
                makeFile(blobKey = "b"),
                makeFile(blobKey = "c"),
                makeFile(blobKey = "d"),
                makeFile(blobKey = "e"),
            )
        )
        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)

        vm.onShareClick(auto = false)

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.AtLimit>()
    }

    @Test
    fun `state freeLimitReached is true for free user with 1 own file`() = runTest2 {
        setupBaseMocks(selfFiles = listOf(makeFile(blobKey = "self-1")))
        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)

        vm.state.first().freeLimitReached shouldBe true
    }

    @Test
    fun `state freeLimitReached is false for free user with 0 own files`() = runTest2 {
        setupBaseMocks()
        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)

        vm.state.first().freeLimitReached shouldBe false
    }

    @Test
    fun `state freeLimitReached is false for pro user even with many own files`() = runTest2 {
        setupBaseMocks(
            selfFiles = listOf(
                makeFile(blobKey = "a"),
                makeFile(blobKey = "b"),
                makeFile(blobKey = "c"),
            )
        )
        val vm = makeVM(fakeUpgradeRepo(isPro = true))
        vm.initialize(null)

        vm.state.first().freeLimitReached shouldBe false
    }

    @Test
    fun `expired own files do not count toward the free-tier limit`() = runTest2 {
        // User has 1 expired own file (24h ago) — should NOT block a new upload, since expired
        // files no longer occupy a connector slot.
        setupBaseMocks(selfFiles = listOf(makeFile(blobKey = "old", expiresAt = now - 1.hours)))
        val vm = makeVM(fakeUpgradeRepo(isPro = false))
        vm.initialize(null)

        vm.onShareClick(auto = false)

        val event = vm.uiEvents.first()
        event.shouldBeInstanceOf<FileShareListVM.UiEvent.LaunchPicker>()
    }
}
