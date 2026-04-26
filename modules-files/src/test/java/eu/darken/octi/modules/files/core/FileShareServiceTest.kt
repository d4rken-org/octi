package eu.darken.octi.modules.files.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobCacheDirs
import eu.darken.octi.sync.core.blob.BlobFileTooLargeException
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobQuotaExceededException
import eu.darken.octi.sync.core.blob.BlobServerStorageLowException
import eu.darken.octi.sync.core.blob.BlobStoreConstraints
import eu.darken.octi.sync.core.blob.BlobStoreQuota
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.ByteArrayInputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FileShareServiceTest : BaseTest() {

    private val context = mockk<Context>()
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()
    private val handler = mockk<FileShareHandler>()
    private val fileShareSettings = mockk<FileShareSettings>()
    private val pendingDeletes = mockk<DataStoreValue<Map<String, PendingDelete>>>()
    private val blobManager = mockk<BlobManager>()
    private val blobMaintenance = mockk<BlobMaintenance>(relaxed = true)
    private val syncSettings = mockk<SyncSettings>()
    private fun createService() = FileShareService(
        context = context,
        dispatcherProvider = dispatcherProvider,
        fileShareHandler = handler,
        fileShareSettings = fileShareSettings,
        blobManager = blobManager,
        blobMaintenance = blobMaintenance,
        syncSettings = syncSettings,
        blobCacheDirs = BlobCacheDirs(context),
    )

    @Test
    fun `deleteOwnFile returns deleted when every connector delete succeeds`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val activeConnector = ConnectorId(ConnectorType.OCTISERVER, "test.example.com", "acc-1")
        val ownDeviceId = DeviceId("self")
        val file = FileShareInfo.SharedFile(
            name = "doc.pdf",
            mimeType = "application/pdf",
            size = 123,
            blobKey = "blob-1",
            checksum = "abc",
            sharedAt = Clock.System.now(),
            expiresAt = Clock.System.now() + 1.hours,
            availableOn = setOf(activeConnector.idString),
            connectorRefs = mapOf(activeConnector.idString to RemoteBlobRef("srv-blob-ref-1")),
        )

        every { context.cacheDir } returns cacheDir
        every { syncSettings.deviceId } returns ownDeviceId
        every { fileShareSettings.pendingDeletes } returns pendingDeletes
        coEvery { handler.currentOwn() } returns FileShareInfo(files = listOf(file))
        coEvery { handler.removeFile(file.blobKey) } just runs
        coEvery { handler.updateLocations(any(), any(), any()) } just runs
        coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(activeConnector.idString to activeConnector)
        coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())
        coEvery {
            blobManager.delete(
                deviceId = ownDeviceId,
                moduleId = FileShareModule.MODULE_ID,
                blobKey = BlobKey(file.blobKey),
                targets = mapOf(activeConnector to RemoteBlobRef("srv-blob-ref-1")),
            )
        } returns setOf(activeConnector)

        val result = createService().deleteOwnFile(file.blobKey)

        result shouldBe FileShareService.DeleteResult.Deleted
        coVerify(exactly = 1) { handler.removeFile(file.blobKey) }
        coVerify(exactly = 0) { handler.updateLocations(any(), any(), any()) }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `deleteOwnFile keeps pending delete when stale mirrors remain`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val activeConnector = ConnectorId(ConnectorType.OCTISERVER, "test.example.com", "acc-1")
        val ownDeviceId = DeviceId("self")
        val file = FileShareInfo.SharedFile(
            name = "doc.pdf",
            mimeType = "application/pdf",
            size = 123,
            blobKey = "blob-1",
            checksum = "abc",
            sharedAt = Clock.System.now(),
            expiresAt = Clock.System.now() + 1.hours,
            availableOn = setOf(activeConnector.idString, "stale-connector"),
            // Only the active connector has a known ref — "stale-connector" is listed
            // as available but unreachable (no ref). Delete should proceed on active only.
            connectorRefs = mapOf(activeConnector.idString to RemoteBlobRef("srv-blob-ref-1")),
        )

        every { context.cacheDir } returns cacheDir
        every { syncSettings.deviceId } returns ownDeviceId
        every { fileShareSettings.pendingDeletes } returns pendingDeletes
        coEvery { handler.currentOwn() } returns FileShareInfo(files = listOf(file))
        coEvery { handler.removeFile(file.blobKey) } just runs
        coEvery { handler.updateLocations(any(), any(), any()) } just runs
        coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(activeConnector.idString to activeConnector)
        coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())
        coEvery {
            blobManager.delete(
                deviceId = ownDeviceId,
                moduleId = FileShareModule.MODULE_ID,
                blobKey = BlobKey(file.blobKey),
                targets = mapOf(activeConnector to RemoteBlobRef("srv-blob-ref-1")),
            )
        } returns setOf(activeConnector)

        val result = createService().deleteOwnFile(file.blobKey)

        result shouldBe FileShareService.DeleteResult.Partial(setOf("stale-connector"))
        coVerify(exactly = 0) { handler.removeFile(file.blobKey) }
        coVerify(exactly = 1) { handler.updateLocations(file.blobKey, setOf("stale-connector"), emptyMap<String, RemoteBlobRef>()) }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `deleteOwnFile keeps stale-only entries pending for later cleanup`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val file = FileShareInfo.SharedFile(
            name = "doc.pdf",
            mimeType = "application/pdf",
            size = 123,
            blobKey = "blob-1",
            checksum = "abc",
            sharedAt = Clock.System.now(),
            expiresAt = Clock.System.now() + 1.hours,
            availableOn = setOf("stale-connector"),
        )

        every { context.cacheDir } returns cacheDir
        every { syncSettings.deviceId } returns ownDeviceId
        every { fileShareSettings.pendingDeletes } returns pendingDeletes
        coEvery { handler.currentOwn() } returns FileShareInfo(files = listOf(file))
        coEvery { handler.removeFile(file.blobKey) } just runs
        coEvery { handler.updateLocations(any(), any(), any()) } just runs
        coEvery { blobManager.configuredConnectorsByIdString() } returns emptyMap()
        coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

        val result = createService().deleteOwnFile(file.blobKey)

        result shouldBe FileShareService.DeleteResult.Partial(setOf("stale-connector"))
        coVerify(exactly = 0) { handler.removeFile(file.blobKey) }
        coVerify(exactly = 0) { handler.updateLocations(any(), any(), any()) }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile maps all-too-large errors to FileTooLarge with minimum cap`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val fileBytes = ByteArray(42) { it.toByte() }
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(fileBytes) }
        every { syncSettings.deviceId } returns ownDeviceId

        val smallerCap = 10L
        val largerCap = 100L
        coEvery {
            blobManager.put(any(), any(), any(), any(), any(), any(), any())
        } returns BlobManager.PutResult(
            successful = emptySet(),
            perConnectorErrors = mapOf(
                connectorA to BlobFileTooLargeException(
                    connectorId = connectorA,
                    constraints = BlobStoreConstraints(maxFileBytes = largerCap, maxTotalBytes = null),
                    requestedBytes = fileBytes.size.toLong(),
                ),
                connectorB to BlobFileTooLargeException(
                    connectorId = connectorB,
                    constraints = BlobStoreConstraints(maxFileBytes = smallerCap, maxTotalBytes = null),
                    requestedBytes = fileBytes.size.toLong(),
                ),
            ),
        )

        val result = createService().shareFile(uri)

        result.shouldBeInstanceOf<FileShareService.ShareResult.FileTooLarge>()
        (result as FileShareService.ShareResult.FileTooLarge).maxBytes shouldBe smallerCap
        result.requestedBytes shouldBe fileBytes.size.toLong()

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile returns ServerStorageLow when every connector reports BlobServerStorageLowException`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val fileBytes = ByteArray(42) { it.toByte() }
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(fileBytes) }
        every { syncSettings.deviceId } returns ownDeviceId

        coEvery {
            blobManager.put(any(), any(), any(), any(), any(), any(), any())
        } returns BlobManager.PutResult(
            successful = emptySet(),
            perConnectorErrors = mapOf(
                connectorA to BlobServerStorageLowException(connectorA),
                connectorB to BlobServerStorageLowException(connectorB),
            ),
        )

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.ServerStorageLow

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile returns AccountQuotaFull when every connector reports BlobQuotaExceededException`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val fileBytes = ByteArray(42) { it.toByte() }
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(fileBytes) }
        every { syncSettings.deviceId } returns ownDeviceId

        coEvery {
            blobManager.put(any(), any(), any(), any(), any(), any(), any())
        } returns BlobManager.PutResult(
            successful = emptySet(),
            perConnectorErrors = mapOf(
                connectorA to BlobQuotaExceededException(
                    quota = BlobStoreQuota(connectorId = connectorA, usedBytes = 100, totalBytes = 100),
                    requestedBytes = fileBytes.size.toLong(),
                ),
            ),
        )

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.AccountQuotaFull

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile with mixed errors falls through to AllConnectorsFailed`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val fileBytes = ByteArray(42) { it.toByte() }
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(fileBytes) }
        every { syncSettings.deviceId } returns ownDeviceId

        coEvery {
            blobManager.put(any(), any(), any(), any(), any(), any(), any())
        } returns BlobManager.PutResult(
            successful = emptySet(),
            perConnectorErrors = mapOf(
                connectorA to BlobFileTooLargeException(
                    connectorId = connectorA,
                    constraints = BlobStoreConstraints(maxFileBytes = 10L, maxTotalBytes = null),
                    requestedBytes = fileBytes.size.toLong(),
                ),
                connectorB to java.io.IOException("network down"),
            ),
        )

        val result = createService().shareFile(uri)

        result.shouldBeInstanceOf<FileShareService.ShareResult.AllConnectorsFailed>()

        cacheDir.deleteRecursively()
    }

    @Test
    fun `deleteOwnFile tombstone tracks remainingConnectors and both locations shrink together`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val activeConnector = ConnectorId(ConnectorType.OCTISERVER, "test.example.com", "acc-1")
        val ownDeviceId = DeviceId("self")
        val file = FileShareInfo.SharedFile(
            name = "doc.pdf",
            mimeType = "application/pdf",
            size = 123,
            blobKey = "blob-1",
            checksum = "abc",
            sharedAt = Clock.System.now(),
            expiresAt = Clock.System.now() + 1.hours,
            availableOn = setOf(activeConnector.idString, "stale-connector"),
            connectorRefs = mapOf(
                activeConnector.idString to RemoteBlobRef("srv-blob-id-1"),
                "stale-connector" to RemoteBlobRef("key-1"),
            ),
        )

        every { context.cacheDir } returns cacheDir
        every { syncSettings.deviceId } returns ownDeviceId
        every { fileShareSettings.pendingDeletes } returns pendingDeletes
        coEvery { handler.currentOwn() } returns FileShareInfo(files = listOf(file))
        coEvery { handler.removeFile(any()) } just runs
        coEvery { handler.updateLocations(any(), any(), any()) } just runs
        coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(activeConnector.idString to activeConnector)
        coEvery {
            blobManager.delete(
                deviceId = ownDeviceId,
                moduleId = FileShareModule.MODULE_ID,
                blobKey = BlobKey(file.blobKey),
                targets = mapOf(activeConnector to RemoteBlobRef("srv-blob-id-1")),
            )
        } returns setOf(activeConnector)

        // Capture the tombstone produced by the update transform
        var capturedValue: Map<String, PendingDelete>? = null
        coEvery { pendingDeletes.update(any()) } coAnswers {
            val transform = firstArg<(Map<String, PendingDelete>) -> Map<String, PendingDelete>?>()
            val result = transform(emptyMap()) ?: emptyMap()
            capturedValue = result
            DataStoreValue.Updated(emptyMap(), result)
        }

        val result = createService().deleteOwnFile(file.blobKey)

        result shouldBe FileShareService.DeleteResult.Partial(setOf("stale-connector"))
        capturedValue!!["blob-1"]!!.remainingConnectors shouldBe setOf("stale-connector")
        // availableOn AND connectorRefs must shrink together — both exclude the successfully deleted connector
        coVerify(exactly = 1) {
            handler.updateLocations(
                blobKey = file.blobKey,
                newAvailableOn = setOf("stale-connector"),
                newConnectorRefs = mapOf("stale-connector" to RemoteBlobRef("key-1")),
            )
        }

        cacheDir.deleteRecursively()
    }
}
