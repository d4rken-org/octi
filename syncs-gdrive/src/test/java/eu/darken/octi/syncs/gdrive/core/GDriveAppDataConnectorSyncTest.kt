package eu.darken.octi.syncs.gdrive.core

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.client.util.DateTime
import com.google.api.services.drive.model.Change
import com.google.api.services.drive.model.ChangeList
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.StartPageToken
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.CapabilitiesCodec
import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.ConnectorSyncState
import eu.darken.octi.sync.core.DeviceCapabilitiesProvider
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.encryption.CryptoCapabilities
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.StorageStatusProvider
import eu.darken.octi.sync.core.cache.SyncCache
import eu.darken.octi.sync.core.execute
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import okio.ByteString.Companion.encodeUtf8
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Buffer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import com.google.api.services.drive.model.File as GDriveFile

class GDriveAppDataConnectorSyncTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = TestDispatcherProvider()
    private val power = ModuleId("eu.darken.octi.module.core.power")
    private val wifi = ModuleId("eu.darken.octi.module.core.wifi")

    private lateinit var mockDrive: Drive
    private lateinit var mockChanges: Drive.Changes
    private lateinit var mockChangesList: Drive.Changes.List
    private lateinit var mockGetStartPageToken: Drive.Changes.GetStartPageToken
    private lateinit var mockFiles: Drive.Files
    private lateinit var mockFilesList: Drive.Files.List
    private lateinit var mockFilesGet: Drive.Files.Get

    private lateinit var syncSettings: SyncSettings
    private lateinit var syncState: ConnectorSyncState

    @BeforeEach
    fun setup() {
        mockDrive = mockk(relaxed = true)
        mockChanges = mockk(relaxed = true)
        mockChangesList = mockk(relaxed = true)
        mockGetStartPageToken = mockk(relaxed = true)
        mockFiles = mockk(relaxed = true)
        mockFilesList = mockk(relaxed = true)
        mockFilesGet = mockk(relaxed = true)

        every { mockDrive.changes() } returns mockChanges
        every { mockChanges.list(any()) } returns mockChangesList
        every { mockChangesList.setSpaces(any()) } returns mockChangesList
        every { mockChangesList.setFields(any<String>()) } returns mockChangesList
        every { mockChanges.getStartPageToken() } returns mockGetStartPageToken
        every { mockGetStartPageToken.setSupportsAllDrives(any()) } returns mockGetStartPageToken

        every { mockDrive.files() } returns mockFiles
        every { mockFiles.list() } returns mockFilesList
        every { mockFilesList.setSpaces(any()) } returns mockFilesList
        every { mockFilesList.setFields(any<String>()) } returns mockFilesList
        every { mockFiles.get(any()) } returns mockFilesGet
        every { mockFilesGet.setFields(any<String>()) } returns mockFilesGet

        syncSettings = mockk(relaxed = true)
        syncState = ConnectorSyncState()
    }

    private fun TestScope.createConnector(
        initialDeviceMetadata: List<DeviceMetadata> = emptyList(),
        syncCache: SyncCache? = null,
    ): GDriveAppDataConnector {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this.backgroundScope,
            produceFile = { File(tempDir, "test_sync_${System.nanoTime()}.preferences_pb") },
        )
        every { syncSettings.dataStore } returns testDataStore
        every { syncSettings.deviceLabel } returns testDataStore.createValue("sync.device.self.label", null as String?)
        every { syncSettings.deviceId } returns DeviceId("test-device")
        // Processor's pause guard reads syncSettings.isPaused() on every command —
        // supply an empty flow so guardPauseIfNeeded doesn't NoSuchElementException on flow.first().
        coEvery { syncSettings.isPaused(any()) } returns false

        val testAccount = GoogleAccount(accountId = "test-account", email = "test@example.com")
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns tempDir
        val json = Json { ignoreUnknownKeys = true }

        val mockNetworkState = mockk<NetworkStateProvider>(relaxed = true)
        every { mockNetworkState.networkState } returns flowOf(
            mockk { every { isInternetAvailable } returns true },
        )
        val cache = syncCache ?: SyncCache(json, testDispatcher, context)

        val capabilitiesCodec = CapabilitiesCodec(json)
        val connector = GDriveAppDataConnector(
            account = testAccount,
            initialDeviceMetadata = initialDeviceMetadata,
            scope = this.backgroundScope,
            dispatcherProvider = testDispatcher,
            context = context,
            networkStateProvider = mockNetworkState,
            supportedModuleIds = setOf(power, wifi),
            syncSettings = syncSettings,
            syncState = syncState,
            syncCache = cache,
            json = json,
            capabilitiesProvider = DeviceCapabilitiesProvider(
                object : CryptoCapabilities { override val gcmSivAvailable: Boolean = true },
            ),
            capabilitiesCodec = capabilitiesCodec,
        )

        // Inject mock Drive via reflection, bypassing GDriveBaseConnector's lazy init
        val field = GDriveBaseConnector::class.java.getDeclaredField("gdrive\$delegate")
        field.isAccessible = true
        field.set(connector, lazyOf(mockDrive))

        // Start the processor so submit/execute actually run commands.
        connector.start(this.backgroundScope)

        return connector
    }

    /**
     * Legacy-shape wrapper for tests: preserves the old `connector.sync(options)` call
     * semantics by routing through the queue's execute helper. Keeps the large test body
     * readable without rewriting every call site.
     */
    private suspend fun GDriveAppDataConnector.sync(options: SyncOptions) =
        execute(ConnectorCommand.Sync(options))

    @Test
    fun `starts with initial device metadata`() = runTest {
        val metadata = listOf(
            DeviceMetadata(
                deviceId = DeviceId("cached-device"),
                version = "1",
                platform = "android",
                label = "Cached Device",
            )
        )

        val connector = createConnector(initialDeviceMetadata = metadata)

        connector.state.first().deviceMetadata shouldBe metadata
    }

    private fun setupNoChanges(newToken: String = "token-2") {
        every { mockChangesList.execute() } returns ChangeList().apply {
            newStartPageToken = newToken
            changes = emptyList()
        }
    }

    private fun setupHasChanges(newToken: String = "token-2") {
        every { mockChangesList.execute() } returns ChangeList().apply {
            newStartPageToken = newToken
            changes = listOf(Change().apply { fileId = "changed-file-1" })
        }
    }

    private fun setupStartPageToken(token: String = "start-token") {
        every { mockGetStartPageToken.execute() } returns StartPageToken().apply {
            startPageToken = token
        }
    }

    private fun setupEmptyDriveRead() {
        // appDataRoot returns a file with no "devices" child
        val rootFile = GDriveFile().apply {
            id = "appDataFolder"
            name = "appDataFolder"
            mimeType = "application/vnd.google-apps.folder"
        }
        every { mockFilesGet.execute() } returns rootFile
        // child("devices") returns empty
        every { mockFilesList.execute() } returns FileList().apply {
            files = emptyList()
        }
    }

    @Nested
    inner class `sync guard behavior` {

        @Test
        fun `periodic sync skips readDrive when no changes`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupEmptyDriveRead()

            // First sync: initializes syncToken, forces full read
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Second sync: no changes → skips readDrive
            setupNoChanges("token-2")
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // changes.list was called for the second sync's checkSyncChanges
            verify(atLeast = 1) { mockChangesList.execute() }
        }

        @Test
        fun `periodic sync with stats skips Drive file reads when no changes`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupEmptyDriveRead()

            // First sync initializes syncToken and performs the initial read.
            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            io.mockk.clearMocks(
                mockFiles,
                mockFilesGet,
                mockFilesList,
                answers = false,
                childMocks = false,
                exclusionRules = false,
            )
            setupNoChanges("token-2")

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            verify(exactly = 1) { mockChangesList.execute() }
            verify(exactly = 0) { mockFiles.get(any()) }
            verify(exactly = 0) { mockFiles.list() }
        }

        @Test
        fun `targeted sync skips change guard`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupEmptyDriveRead()

            // First: full sync to initialize _data
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Reset mock tracking
            io.mockk.clearMocks(mockChangesList, answers = false, childMocks = false, exclusionRules = false)
            setupEmptyDriveRead()

            // Targeted sync: should NOT call changes.list
            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                    deviceFilter = setOf(DeviceId("device-1")),
                ),
            )

            // checkSyncChanges should not be called for targeted syncs
            verify(exactly = 0) { mockChanges.list(any()) }
        }

        @Test
        fun `syncToken not advanced on targeted read`() = runTest {
            val connector = createConnector()
            setupStartPageToken("initial-token")
            setupEmptyDriveRead()

            // Full sync to initialize
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            val tokenBeforeTargeted = syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value()
            tokenBeforeTargeted.shouldNotBeBlank()

            // Targeted sync
            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                ),
            )

            val tokenAfterTargeted = syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value()
            tokenAfterTargeted shouldBe tokenBeforeTargeted
        }

        @Test
        fun `syncToken advanced after full read with changes`() = runTest {
            val connector = createConnector()
            setupStartPageToken("initial-token")
            setupEmptyDriveRead()

            // First sync: initializes token
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Second sync: has changes, should advance token
            setupHasChanges("advanced-token")
            setupEmptyDriveRead()
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            val token = syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value()
            token shouldBe "advanced-token"
        }

        @Test
        fun `first sync forces full read even with moduleFilter`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupEmptyDriveRead()

            // First sync with filters — should still do full read
            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                ),
            )

            // syncToken should be initialized (full read happened)
            val token = syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value()
            token.shouldNotBeBlank()
        }

        @Test
        fun `restart with valid token still reads drive when data is uninitialized`() = runTest {
            val connector = createConnector()

            // Pre-populate sync token, simulating a previous session that left a valid token
            syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value("pre-existing-token")

            setupEmptyDriveRead()
            setupNoChanges("pre-existing-token")

            // First sync after restart: _data is null but NoChanges — should still do full read
            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            // Data must be non-null even though the change guard said "no changes"
            connector.data.first().shouldNotBeNull()

            // Second sync: data already loaded, still no changes — optimization must kick in
            io.mockk.clearMocks(
                mockFiles,
                mockFilesGet,
                mockFilesList,
                answers = false,
                childMocks = false,
                exclusionRules = false,
            )
            setupNoChanges("pre-existing-token")

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            verify(exactly = 0) { mockFiles.get(any()) }
            verify(exactly = 0) { mockFiles.list() }
        }
    }

    @Nested
    inner class `full read coverage` {

        private val coverageDevice = DeviceId("device-a")
        private val coveragePowerFileId = "coverage-power-file-id"

        /**
         * Full read that actually retrieves a module payload — the only shape that may claim
         * coverage.
         */
        private fun setupDriveReadWithModuleData(payload: String = "power-data") {
            setupDriveRead(withModuleFile = true, payload = payload)
        }

        /**
         * Device dir exists, but no module payload comes back: `devices` is non-empty while every
         * entry has zero modules. Same observable shape as transient per-module read failures.
         */
        private fun setupDriveReadWithoutModuleData() {
            setupDriveRead(withModuleFile = false)
        }

        private fun setupDriveRead(withModuleFile: Boolean, payload: String = "power-data") {
            val rootFile = GDriveFile().apply {
                id = "appDataFolder"
                name = "appDataFolder"
                mimeType = "application/vnd.google-apps.folder"
            }
            val devicesDir = GDriveFile().apply {
                id = "devices-dir-id"
                name = "devices"
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf("appDataFolder")
            }
            val deviceDir = GDriveFile().apply {
                id = "device-a-dir-id"
                name = coverageDevice.id
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf("devices-dir-id")
            }
            val powerFile = GDriveFile().apply {
                id = coveragePowerFileId
                name = power.id
                mimeType = "application/octet-stream"
                parents = listOf("device-a-dir-id")
                modifiedTime = DateTime(1000L)
            }

            val rootGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns rootFile
            }
            val moduleGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns powerFile
                every { it.executeMediaAndDownloadTo(any()) } answers {
                    firstArg<java.io.OutputStream>().write(payload.toByteArray())
                }
            }
            every { mockFiles.get(any()) } answers {
                when (firstArg<String>()) {
                    "appDataFolder" -> rootGet
                    coveragePowerFileId -> moduleGet
                    else -> mockk(relaxed = true)
                }
            }
            every { mockFilesList.execute() } returns FileList().apply {
                files = buildList {
                    add(devicesDir)
                    add(deviceDir)
                    if (withModuleFile) add(powerFile)
                }
            }
        }

        @Test
        fun `full read that retrieved module data sets lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveReadWithModuleData()

            connector.state.first().lastFullReadAt.shouldBeNull()

            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            connector.state.first().lastFullReadAt.shouldNotBeNull()
        }

        @Test
        fun `full read where no device yielded module data leaves lastFullReadAt null`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveReadWithoutModuleData()

            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            val data = connector.data.first()
            data.shouldNotBeNull()
            data.devices shouldHaveSize 1
            data.devices.single().modules.shouldBeEmpty()
            connector.state.first().lastFullReadAt.shouldBeNull()
        }

        @Test
        fun `full read with a missing device data dir leaves lastFullReadAt null`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            // No "devices" dir at all → readDrive returns devices = emptySet()
            setupEmptyDriveRead()

            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            connector.data.first()?.devices.shouldNotBeNull().shouldBeEmpty()
            connector.state.first().lastFullReadAt.shouldBeNull()
        }

        @Test
        fun `targeted read on top of existing data does not advance lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveReadWithModuleData()

            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))
            val afterFullRead = connector.state.first().lastFullReadAt
            afterFullRead.shouldNotBeNull()

            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                ),
            )

            connector.state.first().lastFullReadAt shouldBe afterFullRead
        }

        @Test
        fun `pause clears lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveReadWithModuleData()
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))
            connector.state.first().lastFullReadAt.shouldNotBeNull()

            connector.execute(ConnectorCommand.Pause())

            connector.state.first().lastFullReadAt.shouldBeNull()
        }

        @Test
        fun `resume clears lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveReadWithModuleData()
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))
            connector.state.first().lastFullReadAt.shouldNotBeNull()

            connector.execute(ConnectorCommand.Resume)

            connector.state.first().lastFullReadAt.shouldBeNull()
        }

        @Test
        fun `reset clears lastFullReadAt`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveReadWithModuleData()
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))
            connector.state.first().lastFullReadAt.shouldNotBeNull()

            // The reset path resolves the devices dir via child(), which the mock can't filter by
            // parent — hand it an empty listing so the deletion walk finds nothing to delete.
            setupEmptyDriveRead()

            connector.execute(ConnectorCommand.Reset)

            connector.state.first().lastFullReadAt.shouldBeNull()
        }
    }

    @Nested
    inner class `error handling` {

        @Test
        fun `isSyncing resets on runDriveAction error`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")

            // Make appDataRoot throw
            every { mockFilesGet.execute() } throws RuntimeException("Drive error")

            // First sync fails
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Second sync should not be blocked by isSyncing
            setupEmptyDriveRead()
            setupStartPageToken("start-token-2")
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // If isSyncing wasn't reset, second sync would be skipped and no token would be set
            val token = syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value()
            // May or may not have a token depending on error recovery, but the key is it didn't deadlock
        }

        @Test
        fun `410 resets syncToken`() = runTest {
            val connector = createConnector()
            setupStartPageToken("initial-token")
            setupEmptyDriveRead()

            // First sync: initialize token
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            val tokenBefore = syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value()
            tokenBefore.shouldNotBeBlank()

            // Second sync: 410 error on changes.list
            val exception410 = mockk<com.google.api.client.googleapis.json.GoogleJsonResponseException>(relaxed = true)
            every { exception410.statusCode } returns 410
            every { mockChangesList.execute() } throws exception410
            setupEmptyDriveRead()
            setupStartPageToken("fresh-token")

            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Token should have been reset and re-initialized
            val tokenAfter = syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value()
            // ForceFullSync path re-initializes the token
            tokenAfter.shouldNotBeBlank()
        }
    }

    @Nested
    inner class `pagination` {

        @Test
        fun `checkSyncChanges loops through all pages`() = runTest {
            val connector = createConnector()
            setupStartPageToken("initial-token")
            setupEmptyDriveRead()

            // First sync to initialize
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Set up paginated changes: page 1 has nextPageToken, page 2 has final token
            var callCount = 0
            every { mockChangesList.execute() } answers {
                callCount++
                if (callCount == 1) {
                    ChangeList().apply {
                        nextPageToken = "page-2-token"
                        changes = listOf(Change().apply { fileId = "file-1" })
                    }
                } else {
                    ChangeList().apply {
                        newStartPageToken = "final-token"
                        changes = listOf(Change().apply { fileId = "file-2" })
                    }
                }
            }

            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Both pages should have been fetched
            callCount shouldBe 2

            val token = syncSettings.dataStore
                .createValue("gdrive.sync_token.test-account", null as String?)
                .value()
            token shouldBe "final-token"
        }
    }

    @Nested
    inner class `fileId cache` {

        private val deviceA = DeviceId("device-a")
        private val powerFileId = "power-file-id-123"

        private fun setupDriveWithPowerModule(
            payload: String = "power-data",
            deviceId: DeviceId = deviceA,
            includeDeviceInfo: Boolean = false,
            appDataRootId: String = "appDataFolder",
        ) {
            val rootFile = GDriveFile().apply {
                id = appDataRootId
                name = "appDataFolder"
                mimeType = "application/vnd.google-apps.folder"
            }
            val devicesDir = GDriveFile().apply {
                id = "devices-dir-id"
                name = "devices"
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(appDataRootId)
            }
            val deviceADir = GDriveFile().apply {
                id = "device-a-dir-id"
                name = deviceId.id
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf("devices-dir-id")
            }
            val powerFile = GDriveFile().apply {
                id = powerFileId
                name = power.id
                mimeType = "application/octet-stream"
                parents = listOf("device-a-dir-id")
                modifiedTime = DateTime(1000L)
            }
            val deviceInfoFile = GDriveFile().apply {
                id = "device-info-file-id"
                name = "_device.json"
                mimeType = "application/octet-stream"
                parents = listOf("device-a-dir-id")
                modifiedTime = DateTime(1500L)
            }

            val rootGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns rootFile
            }
            val moduleGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns powerFile
                every { it.executeMediaAndDownloadTo(any()) } answers {
                    firstArg<java.io.OutputStream>().write(payload.toByteArray())
                }
            }
            val deviceInfoGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns deviceInfoFile
                every { it.executeMediaAndDownloadTo(any()) } answers {
                    firstArg<java.io.OutputStream>().write("""{"version":"1","platform":"android","label":"Device A"}""".toByteArray())
                }
            }

            every { mockFiles.get(any()) } answers {
                when (firstArg<String>()) {
                    "appDataFolder" -> rootGet
                    powerFileId -> moduleGet
                    "device-info-file-id" -> deviceInfoGet
                    else -> mockk(relaxed = true)
                }
            }

            every { mockFilesList.execute() } returns FileList().apply {
                files = buildList {
                    add(devicesDir)
                    add(deviceADir)
                    add(powerFile)
                    if (includeDeviceInfo) add(deviceInfoFile)
                }
            }
        }

        private fun moduleWrite(payloadText: String): SyncOptions.ModuleWrite {
            val module = object : SyncWrite.Device.Module {
                override val moduleId: ModuleId = power
                override val payload = payloadText.encodeUtf8()
            }
            return SyncOptions.ModuleWrite(module = module, expectedHash = "hash-$payloadText")
        }

        private fun googleJsonException(statusCode: Int) =
            mockk<com.google.api.client.googleapis.json.GoogleJsonResponseException>(relaxed = true).also {
                every { it.statusCode } returns statusCode
            }

        private fun setupUpdateCapture(
            updateIds: MutableList<String>,
            staleIds: Set<String> = emptySet(),
        ) {
            every {
                mockFiles.update(
                    any<String>(),
                    any<GDriveFile>(),
                    any<com.google.api.client.http.AbstractInputStreamContent>(),
                )
            } answers {
                val fileId = firstArg<String>()
                updateIds += fileId
                mockk<Drive.Files.Update>(relaxed = true).also {
                    every { it.setFields(any<String>()) } returns it
                    if (fileId in staleIds) {
                        every { it.execute() } throws googleJsonException(404)
                    } else {
                        every { it.execute() } returns GDriveFile().apply {
                            id = fileId
                            name = "updated"
                        }
                    }
                }
            }
        }

        @Test
        fun `full read indexes devices under resolved appData root id`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveWithPowerModule(
                payload = "indexed",
                includeDeviceInfo = true,
                appDataRootId = "resolved-appdata-root-id",
            )

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            verify(exactly = 1) { mockFilesList.execute() }
            verify(exactly = 1) { mockFiles.get("appDataFolder") }
            val data = connector.data.first()
            data.shouldNotBeNull()
            data.devices.single().modules.single().payload.utf8() shouldBe "indexed"

            val metadata = connector.state.first().deviceMetadata.single()
            metadata.deviceId shouldBe deviceA
            metadata.version shouldBe "1"
            metadata.platform shouldBe "android"
            metadata.label shouldBe "Device A"
            // _device.json (1500) is newer than the power module file (1000) and counts too.
            metadata.lastSeen shouldBe kotlin.time.Instant.fromEpochMilliseconds(1500L)
        }

        @Test
        fun `full read with stats persists device metadata cache`() = runTest {
            val context = mockk<Context>(relaxed = true)
            every { context.cacheDir } returns tempDir
            val syncCache = SyncCache(Json { ignoreUnknownKeys = true }, testDispatcher, context)
            val connector = createConnector(syncCache = syncCache)
            setupStartPageToken("start-token")
            setupDriveWithPowerModule("indexed", includeDeviceInfo = true)

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            val cached = syncCache.loadDeviceMetadata(connector.identifier)
            cached.shouldNotBeNull()
            cached.single().deviceId shouldBe deviceA
            cached.single().label shouldBe "Device A"
        }

        @Test
        fun `targeted sync uses direct fetch when cache is warm`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveWithPowerModule("original")

            // Full sync populates _data + fileIdCache
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))
            connector.data.first().shouldNotBeNull()

            // Override module mock with updated payload for direct fetch
            val updatedPowerFile = GDriveFile().apply {
                id = powerFileId
                name = power.id
                parents = listOf("device-a-dir-id")
                modifiedTime = DateTime(2000L)
            }
            val updatedGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns updatedPowerFile
                every { it.executeMediaAndDownloadTo(any()) } answers {
                    firstArg<java.io.OutputStream>().write("updated".toByteArray())
                }
            }
            every { mockFiles.get(powerFileId) } returns updatedGet

            // Break directory traversal — readDrive would return empty
            every { mockFilesList.execute() } returns FileList().apply { files = emptyList() }

            // Targeted sync with BOTH filters → should use direct fetch
            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                    deviceFilter = setOf(deviceA),
                ),
            )

            // Data should have updated payload — proves direct fetch was used
            val data = connector.data.first()
            data.shouldNotBeNull()
            val module = data.devices
                .first { it.deviceId == deviceA }
                .modules
                .first { it.moduleId == power }
            module.payload.utf8() shouldBe "updated"

            val metadata = connector.state.first().deviceMetadata.single()
            metadata.deviceId shouldBe deviceA
            metadata.lastSeen shouldBe kotlin.time.Instant.fromEpochMilliseconds(2000L)
        }

        @Test
        fun `targeted sync with only moduleFilter uses readDrive not cache`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveWithPowerModule("original")

            // Full sync
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Re-setup drive with different payload
            setupDriveWithPowerModule("from-readDrive")

            // Targeted sync with ONLY moduleFilter → readDrive, not cache
            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                ),
            )

            val data = connector.data.first()
            data.shouldNotBeNull()
            val module = data.devices
                .first { it.deviceId == deviceA }
                .modules
                .first { it.moduleId == power }
            module.payload.utf8() shouldBe "from-readDrive"
        }

        @Test
        fun `targeted sync falls back to readDrive when cache is empty`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupEmptyDriveRead()

            // First sync: empty drive → _data populated but cache empty
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Setup populated drive for fallback
            setupDriveWithPowerModule("fallback-data")

            // Targeted sync — cache miss → falls back to readDrive
            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                    deviceFilter = setOf(deviceA),
                ),
            )

            val data = connector.data.first()
            data.shouldNotBeNull()
            val module = data.devices
                .first { it.deviceId == deviceA }
                .modules
                .first { it.moduleId == power }
            module.payload.utf8() shouldBe "fallback-data"
        }

        @Test
        fun `direct fetch validates file name and falls back on mismatch`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveWithPowerModule("original")

            // Full sync populates cache
            connector.sync(SyncOptions(stats = false, readData = true, writeData = false))

            // Override getFileMetadata to return wrong name (stale cache)
            val wrongNameFile = GDriveFile().apply {
                id = powerFileId
                name = "wrong-module-name"
                parents = listOf("device-a-dir-id")
                modifiedTime = DateTime(2000L)
            }
            val wrongGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns wrongNameFile
            }
            every { mockFiles.get(powerFileId) } returns wrongGet

            // Re-setup list mock for fallback readDrive
            setupDriveWithPowerModule("fallback-after-mismatch")

            // Targeted sync — direct fetch detects mismatch, falls back
            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = true,
                    writeData = false,
                    moduleFilter = setOf(power),
                    deviceFilter = setOf(deviceA),
                ),
            )

            val data = connector.data.first()
            data.shouldNotBeNull()
            val module = data.devices
                .first { it.deviceId == deviceA }
                .modules
                .first { it.moduleId == power }
            module.payload.utf8() shouldBe "fallback-after-mismatch"
        }

        @Test
        fun `write uses warm file and directory id caches without traversal`() = runTest {
            val currentDevice = DeviceId("test-device")
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveWithPowerModule("original", deviceId = currentDevice, includeDeviceInfo = true)

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            io.mockk.clearMocks(
                mockFiles,
                mockFilesGet,
                mockFilesList,
                answers = false,
                childMocks = false,
                exclusionRules = false,
            )
            val updateIds = mutableListOf<String>()
            setupUpdateCapture(updateIds)

            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = false,
                    writeData = true,
                    writePayload = listOf(moduleWrite("written")),
                ),
            )

            updateIds.contains(powerFileId) shouldBe true
            updateIds.contains("device-info-file-id") shouldBe true
            verify(exactly = 0) { mockFiles.get(any()) }
            verify(exactly = 0) { mockFiles.list() }
            verify(exactly = 0) { mockFiles.create(any<GDriveFile>()) }
        }

        @Test
        fun `write falls back to path lookup when warm module id is stale`() = runTest {
            val currentDevice = DeviceId("test-device")
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupDriveWithPowerModule("original", deviceId = currentDevice, includeDeviceInfo = true)

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            io.mockk.clearMocks(
                mockFiles,
                mockFilesGet,
                mockFilesList,
                answers = false,
                childMocks = false,
                exclusionRules = false,
            )
            val freshPowerFile = GDriveFile().apply {
                id = "fresh-power-file-id"
                name = power.id
                mimeType = "application/octet-stream"
                parents = listOf("device-a-dir-id")
                modifiedTime = DateTime(2000L)
            }
            every { mockFiles.list() } returns mockFilesList
            every { mockFilesList.execute() } returns FileList().apply {
                files = listOf(freshPowerFile)
            }
            val updateIds = mutableListOf<String>()
            setupUpdateCapture(updateIds, staleIds = setOf(powerFileId))

            connector.sync(
                SyncOptions(
                    stats = false,
                    readData = false,
                    writeData = true,
                    writePayload = listOf(moduleWrite("written")),
                ),
            )

            updateIds.contains(powerFileId) shouldBe true
            updateIds.contains("fresh-power-file-id") shouldBe true
            verify(exactly = 0) { mockFiles.get(any()) }
            verify(exactly = 1) { mockFiles.list() }
            verify(exactly = 0) { mockFiles.create(any<GDriveFile>()) }
        }
    }

    @Nested
    inner class `self device writes` {

        private val selfDevice = DeviceId("test-device")
        private val selfPowerFileId = "self-power-file-id"
        private val selfWifiFileId = "self-wifi-file-id"
        private val selfInfoFileId = "self-device-info-id"

        private fun rootFile() = GDriveFile().apply {
            id = "appDataFolder"
            name = "appDataFolder"
            mimeType = "application/vnd.google-apps.folder"
        }

        private fun devicesDir() = GDriveFile().apply {
            id = "devices-dir-id"
            name = "devices"
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf("appDataFolder")
        }

        private fun deviceDir() = GDriveFile().apply {
            id = "self-device-dir-id"
            name = selfDevice.id
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf("devices-dir-id")
        }

        private fun moduleFile(fileId: String, module: ModuleId, modifiedAt: Long) = GDriveFile().apply {
            id = fileId
            name = module.id
            mimeType = "application/octet-stream"
            parents = listOf("self-device-dir-id")
            modifiedTime = DateTime(modifiedAt)
        }

        private fun infoFile(modifiedAt: Long) = GDriveFile().apply {
            id = selfInfoFileId
            name = "_device.json"
            mimeType = "application/octet-stream"
            parents = listOf("self-device-dir-id")
            modifiedTime = DateTime(modifiedAt)
        }

        private fun stubFileGets(entries: List<GDriveFile>) {
            val root = rootFile()
            val rootGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns root
            }
            val getsById = entries.associate { entry ->
                entry.id to mockk<Drive.Files.Get>(relaxed = true).also {
                    every { it.setFields(any<String>()) } returns it
                    every { it.execute() } returns entry
                    every { it.executeMediaAndDownloadTo(any()) } answers {
                        val payload = when (entry.name) {
                            "_device.json" -> """{"version":"1","platform":"android","label":"Self"}"""
                            else -> "payload-${entry.name}"
                        }
                        firstArg<java.io.OutputStream>().write(payload.toByteArray())
                    }
                }
            }
            every { mockFiles.get(any()) } answers {
                when (val fileId = firstArg<String>()) {
                    "appDataFolder" -> rootGet
                    else -> getsById[fileId] ?: mockk(relaxed = true)
                }
            }
        }

        /** Flat app-data listing, the shape [listAppDataFiles] returns during a full read. */
        private fun setupFullListing(
            powerModifiedAt: Long = 1000L,
            wifiModifiedAt: Long = 1000L,
            infoModifiedAt: Long = 1500L,
        ) {
            val entries = listOf(
                devicesDir(),
                deviceDir(),
                moduleFile(selfPowerFileId, power, powerModifiedAt),
                moduleFile(selfWifiFileId, wifi, wifiModifiedAt),
                infoFile(infoModifiedAt),
            )
            stubFileGets(entries)
            every { mockFilesList.execute() } returns FileList().apply { files = entries }
        }

        /**
         * The mock cannot filter a listing by the request's name query, and `child()` throws on more
         * than one match — so hand out one result per lookup, in traversal order.
         */
        private fun setupChildLookups(vararg results: GDriveFile) {
            var call = 0
            every { mockFilesList.execute() } answers {
                val next = results.getOrNull(call++)
                FileList().apply { files = listOfNotNull(next) }
            }
        }

        private fun setupUpdates(updateIds: MutableList<String>, failingIds: Set<String> = emptySet()) {
            every {
                mockFiles.update(
                    any<String>(),
                    any<GDriveFile>(),
                    any<com.google.api.client.http.AbstractInputStreamContent>(),
                )
            } answers {
                val fileId = firstArg<String>()
                updateIds += fileId
                mockk<Drive.Files.Update>(relaxed = true).also {
                    every { it.setFields(any<String>()) } returns it
                    if (fileId in failingIds) {
                        every { it.execute() } throws RuntimeException("upload failed: $fileId")
                    } else {
                        every { it.execute() } returns GDriveFile().apply {
                            id = fileId
                            name = "updated"
                        }
                    }
                }
            }
        }

        private fun moduleWrite(module: ModuleId, payloadText: String): SyncOptions.ModuleWrite {
            val written = object : SyncWrite.Device.Module {
                override val moduleId: ModuleId = module
                override val payload = payloadText.encodeUtf8()
            }
            return SyncOptions.ModuleWrite(module = written, expectedHash = "hash-${module.id}")
        }

        private fun writeSync(vararg writes: SyncOptions.ModuleWrite) = SyncOptions(
            stats = false,
            readData = false,
            writeData = true,
            writePayload = writes.toList(),
        )

        @Test
        fun `a failing module upload keeps the hashes of the ones that landed`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupFullListing()

            // Warm the file id caches so the write goes straight to update().
            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            io.mockk.clearMocks(
                mockFiles,
                mockFilesGet,
                mockFilesList,
                answers = false,
                childMocks = false,
                exclusionRules = false,
            )
            val updateIds = mutableListOf<String>()
            setupUpdates(updateIds, failingIds = setOf(selfWifiFileId))

            runCatching {
                connector.sync(writeSync(moduleWrite(power, "power-written"), moduleWrite(wifi, "wifi-written")))
            }.isFailure shouldBe true

            updateIds.contains(selfPowerFileId) shouldBe true
            syncState.getRecord(connector.identifier, power)?.hash shouldBe "hash-${power.id}"
            syncState.getRecord(connector.identifier, wifi).shouldBeNull()
        }

        @Test
        fun `device info is rewritten after a reset even when its content is unchanged`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupFullListing()

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            val firstWriteIds = mutableListOf<String>()
            setupUpdates(firstWriteIds)
            connector.sync(writeSync(moduleWrite(power, "v1")))
            firstWriteIds.contains(selfInfoFileId) shouldBe true

            // Reset deletes our device dir — this listing leaves nothing for the deletion walk.
            setupEmptyDriveRead()
            connector.execute(ConnectorCommand.Reset)

            // Caches were cleared, so the next write walks the tree again.
            stubFileGets(listOf(devicesDir(), deviceDir()))
            setupChildLookups(
                devicesDir(),
                deviceDir(),
                moduleFile(selfPowerFileId, power, 2000L),
                infoFile(2000L),
            )
            val secondWriteIds = mutableListOf<String>()
            setupUpdates(secondWriteIds)

            connector.sync(writeSync(moduleWrite(power, "v1")))

            secondWriteIds.contains(selfInfoFileId) shouldBe true
        }

        @Test
        fun `lastSeen counts the device info manifest`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupFullListing(powerModifiedAt = 1000L, wifiModifiedAt = 1000L, infoModifiedAt = 4000L)

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            val metadata = connector.state.first().deviceMetadata.single { it.deviceId == selfDevice }
            metadata.lastSeen shouldBe kotlin.time.Instant.fromEpochMilliseconds(4000L)
        }

        @Test
        fun `lastSeen uses the newest module file when it beats the manifest`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupFullListing(powerModifiedAt = 5000L, wifiModifiedAt = 1000L, infoModifiedAt = 1500L)

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            val metadata = connector.state.first().deviceMetadata.single { it.deviceId == selfDevice }
            metadata.lastSeen shouldBe kotlin.time.Instant.fromEpochMilliseconds(5000L)
        }
    }

    @Nested
    inner class `device metadata resilience` {

        private val deviceA = DeviceId("device-a")
        private val deviceB = DeviceId("device-b")

        private fun folder(fileId: String, fileName: String, parentId: String?) = GDriveFile().apply {
            id = fileId
            name = fileName
            mimeType = "application/vnd.google-apps.folder"
            parentId?.let { parents = listOf(it) }
        }

        private fun file(fileId: String, fileName: String, parentId: String, modifiedAt: Long) = GDriveFile().apply {
            id = fileId
            name = fileName
            mimeType = "application/octet-stream"
            parents = listOf(parentId)
            modifiedTime = DateTime(modifiedAt)
        }

        /** Drive permits duplicate names: device-a's directory holds two `_device.json` entries. */
        private fun setupListingWithDuplicateManifest() {
            val root = folder("appDataFolder", "appDataFolder", null)
            val entries = listOf(
                folder("devices-dir-id", "devices", "appDataFolder"),
                folder("device-a-dir-id", deviceA.id, "devices-dir-id"),
                folder("device-b-dir-id", deviceB.id, "devices-dir-id"),
                file("a-power-file-id", power.id, "device-a-dir-id", 1000L),
                file("a-info-file-id-1", "_device.json", "device-a-dir-id", 1500L),
                file("a-info-file-id-2", "_device.json", "device-a-dir-id", 1700L),
                file("b-power-file-id", power.id, "device-b-dir-id", 2000L),
                file("b-info-file-id", "_device.json", "device-b-dir-id", 2500L),
            )
            val payloads = mapOf(
                "a-power-file-id" to "power-a",
                "a-info-file-id-1" to """{"version":"1","platform":"android","label":"Device A"}""",
                "a-info-file-id-2" to """{"version":"2","platform":"android","label":"Device A duplicate"}""",
                "b-power-file-id" to "power-b",
                "b-info-file-id" to """{"version":"3","platform":"android","label":"Device B"}""",
            )

            val rootGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns root
            }
            val getsById = entries.associate { entry ->
                entry.id to mockk<Drive.Files.Get>(relaxed = true).also {
                    every { it.setFields(any<String>()) } returns it
                    every { it.execute() } returns entry
                    every { it.executeMediaAndDownloadTo(any()) } answers {
                        firstArg<java.io.OutputStream>().write(payloads[entry.id].orEmpty().toByteArray())
                    }
                }
            }
            every { mockFiles.get(any()) } answers {
                when (val fileId = firstArg<String>()) {
                    "appDataFolder" -> rootGet
                    else -> getsById[fileId] ?: mockk(relaxed = true)
                }
            }
            every { mockFilesList.execute() } returns FileList().apply { files = entries }
        }

        @Test
        fun `a duplicate device manifest does not abort the metadata read`() = runTest {
            val connector = createConnector()
            setupStartPageToken("start-token")
            setupListingWithDuplicateManifest()

            connector.sync(SyncOptions(stats = true, readData = true, writeData = false))

            val metadata = connector.state.first().deviceMetadata
            metadata shouldHaveSize 2

            // The ambiguous manifest is skipped, not fatal.
            val ambiguous = metadata.single { it.deviceId == deviceA }
            ambiguous.version.shouldBeNull()
            ambiguous.label.shouldBeNull()
            ambiguous.platform.shouldBeNull()
            ambiguous.capabilities.shouldBeNull()
            // Both duplicates are ordinary files, so they still count towards lastSeen.
            ambiguous.lastSeen shouldBe kotlin.time.Instant.fromEpochMilliseconds(1700L)

            // The other device is untouched by its neighbour's broken directory.
            val healthy = metadata.single { it.deviceId == deviceB }
            healthy.version shouldBe "3"
            healthy.platform shouldBe "android"
            healthy.label shouldBe "Device B"
            healthy.lastSeen shouldBe kotlin.time.Instant.fromEpochMilliseconds(2500L)
        }
    }

    @Nested
    inner class `blob store cache` {

        private val blobDevice = DeviceId("blob-device")
        private val blobKey = BlobKey("blob-1")
        private val remoteRef = RemoteBlobRef("blob-1")
        private val blobFileId = "blob-file-id"

        private fun createBlobStore(connector: GDriveAppDataConnector): GDriveBlobStore {
            return GDriveBlobStore(
                connector = connector,
                connectorId = connector.identifier,
                storageStatus = mockk<StorageStatusProvider>(relaxed = true),
            )
        }

        private fun googleJsonException(statusCode: Int) =
            mockk<com.google.api.client.googleapis.json.GoogleJsonResponseException>(relaxed = true).also {
                every { it.statusCode } returns statusCode
            }

        private fun blobFile(
            id: String = blobFileId,
            name: String = remoteRef.value,
            size: Long = 4L,
            createdAt: Long = 1000L,
        ) = GDriveFile().apply {
            this.id = id
            this.name = name
            mimeType = "application/octet-stream"
            parents = listOf("blob-module-dir-id")
            setSize(size)
            createdTime = DateTime(createdAt)
            modifiedTime = DateTime(createdAt)
        }

        private fun setupBlobTreeListing(blobFile: GDriveFile = blobFile()) {
            val rootFile = GDriveFile().apply {
                id = "appDataFolder"
                name = "appDataFolder"
                mimeType = "application/vnd.google-apps.folder"
            }
            val blobStoreDir = GDriveFile().apply {
                id = "blob-store-dir-id"
                name = "blob-store"
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf("appDataFolder")
            }
            val deviceDir = GDriveFile().apply {
                id = "blob-device-dir-id"
                name = blobDevice.id
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf("blob-store-dir-id")
            }
            val moduleDir = GDriveFile().apply {
                id = "blob-module-dir-id"
                name = power.id
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf("blob-device-dir-id")
            }
            val rootGet = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                every { it.execute() } returns rootFile
            }
            every { mockFiles.get("appDataFolder") } returns rootGet

            var listCall = 0
            every { mockFiles.list() } returns mockFilesList
            every { mockFilesList.execute() } answers {
                listCall++
                FileList().apply {
                    files = when (listCall) {
                        1 -> listOf(blobStoreDir)
                        2 -> listOf(deviceDir)
                        3 -> listOf(moduleDir)
                        else -> listOf(blobFile)
                    }
                }
            }
        }

        private fun setupBlobGet(
            id: String,
            file: GDriveFile,
            payload: String,
            metadataThrows404: Boolean = false,
        ) {
            val get = mockk<Drive.Files.Get>(relaxed = true).also {
                every { it.setFields(any<String>()) } returns it
                if (metadataThrows404) {
                    every { it.execute() } throws googleJsonException(404)
                } else {
                    every { it.execute() } returns file
                }
                every { it.executeMediaAndDownloadTo(any()) } answers {
                    firstArg<java.io.OutputStream>().write(payload.toByteArray())
                }
            }
            every { mockFiles.get(id) } returns get
        }

        private fun setupBlobDelete(deletedIds: MutableList<String>) {
            every { mockFiles.delete(any()) } answers {
                val fileId = firstArg<String>()
                deletedIds += fileId
                mockk<Drive.Files.Delete>(relaxed = true).also {
                    every { it.execute() } returns null
                }
            }
        }

        @Test
        fun `warm blob cache get and delete avoid path traversal`() = runTest {
            val connector = createConnector()
            val store = createBlobStore(connector)
            val file = blobFile()
            setupBlobTreeListing(file)

            store.list(blobDevice, power) shouldBe setOf(remoteRef)

            io.mockk.clearMocks(
                mockFiles,
                mockFilesGet,
                mockFilesList,
                answers = false,
                childMocks = false,
                exclusionRules = false,
            )
            setupBlobGet(blobFileId, file, payload = "data")
            val sink = Buffer()

            val metadata = store.get(
                deviceId = blobDevice,
                moduleId = power,
                key = blobKey,
                remoteRef = remoteRef,
                sink = sink,
                expectedPlaintextSize = 4L,
            )

            metadata.size shouldBe 4L
            sink.readUtf8() shouldBe "data"
            verify(exactly = 0) { mockFiles.list() }

            io.mockk.clearMocks(
                mockFiles,
                answers = false,
                childMocks = false,
                exclusionRules = false,
            )
            val deletedIds = mutableListOf<String>()
            setupBlobDelete(deletedIds)

            store.delete(blobDevice, power, remoteRef)

            deletedIds shouldBe listOf(blobFileId)
            verify(exactly = 0) { mockFiles.get(any()) }
            verify(exactly = 0) { mockFiles.list() }
        }

        @Test
        fun `blob get falls back to path lookup when cached file id is stale`() = runTest {
            val connector = createConnector()
            val store = createBlobStore(connector)
            val staleFile = blobFile()
            setupBlobTreeListing(staleFile)

            store.list(blobDevice, power) shouldBe setOf(remoteRef)

            io.mockk.clearMocks(
                mockFiles,
                mockFilesGet,
                mockFilesList,
                answers = false,
                childMocks = false,
                exclusionRules = false,
            )
            val freshFile = blobFile(id = "fresh-blob-file-id", createdAt = 2000L)
            setupBlobGet(blobFileId, staleFile, payload = "stale", metadataThrows404 = true)
            setupBlobGet("fresh-blob-file-id", freshFile, payload = "fresh")
            every { mockFiles.list() } returns mockFilesList
            every { mockFilesList.execute() } returns FileList().apply {
                files = listOf(freshFile)
            }
            val sink = Buffer()

            val metadata = store.get(
                deviceId = blobDevice,
                moduleId = power,
                key = blobKey,
                remoteRef = remoteRef,
                sink = sink,
                expectedPlaintextSize = 5L,
            )

            metadata.createdAt shouldBe kotlin.time.Instant.fromEpochMilliseconds(2000L)
            sink.readUtf8() shouldBe "fresh"
            verify(exactly = 1) { mockFiles.list() }
        }
    }
}
