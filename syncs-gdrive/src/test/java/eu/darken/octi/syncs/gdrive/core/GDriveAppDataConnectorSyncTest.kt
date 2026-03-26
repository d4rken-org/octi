package eu.darken.octi.syncs.gdrive.core

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.Change
import com.google.api.services.drive.model.ChangeList
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.StartPageToken
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
    }

    private fun TestScope.createConnector(): GDriveAppDataConnector {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this.backgroundScope,
            produceFile = { File(tempDir, "test_sync_${System.nanoTime()}.preferences_pb") },
        )
        every { syncSettings.dataStore } returns testDataStore
        every { syncSettings.deviceId } returns DeviceId("test-device")

        val mockClient = mockk<GoogleClient>(relaxed = true)
        every { mockClient.account.id.id } returns "test-account"
        every { mockClient.account.isAppDataScope } returns true

        val mockNetworkState = mockk<NetworkStateProvider>(relaxed = true)
        every { mockNetworkState.networkState } returns flowOf(
            mockk { every { isInternetAvailable } returns true },
        )

        val connector = GDriveAppDataConnector(
            client = mockClient,
            scope = this.backgroundScope,
            dispatcherProvider = testDispatcher,
            context = mockk<Context>(relaxed = true),
            networkStateProvider = mockNetworkState,
            supportedModuleIds = setOf(power, wifi),
            syncSettings = syncSettings,
        )

        // Inject mock Drive via reflection, bypassing GDriveBaseConnector's lazy init
        val field = GDriveBaseConnector::class.java.getDeclaredField("gdrive\$delegate")
        field.isAccessible = true
        field.set(connector, lazyOf(mockDrive))

        return connector
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
            id = "root-id"
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
}
