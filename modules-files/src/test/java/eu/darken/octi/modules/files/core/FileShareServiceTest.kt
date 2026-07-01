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
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobCacheDirs
import eu.darken.octi.sync.core.blob.BlobFileTooLargeException
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobQuotaExceededException
import eu.darken.octi.sync.core.blob.BlobServerStorageLowException
import eu.darken.octi.sync.core.blob.StorageStatusManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import okio.Buffer
import okio.Source
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FileShareServiceTest : BaseTest() {

    private val context = mockk<Context>()
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()
    private val handler = mockk<FileShareHandler>()
    private val fileShareSettings = mockk<FileShareSettings>()
    private val pendingDeletes = mockk<DataStoreValue<Map<String, PendingDelete>>>()
    private val fileShareSync = mockk<FileShareSync>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val blobManager = mockk<BlobManager>()
    private val blobMaintenance = mockk<BlobMaintenance>(relaxed = true)
    private val storageStatusManager = mockk<StorageStatusManager>(relaxed = true)
    private val syncSettings = mockk<SyncSettings>()
    private val publisher = mockk<FileSharePublisher>(relaxed = true)
    private fun createService() = FileShareService(
        context = context,
        dispatcherProvider = dispatcherProvider,
        fileShareHandler = handler,
        fileShareSettings = fileShareSettings,
        fileShareSync = fileShareSync,
        syncManager = syncManager,
        blobManager = blobManager,
        blobMaintenance = blobMaintenance,
        storageStatusManager = storageStatusManager,
        syncSettings = syncSettings,
        blobCacheDirs = BlobCacheDirs(context),
        publisher = publisher,
    )

    @Test
    fun `deleteFile for remote owner records delete request without deleting blobs`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val remoteDeviceId = DeviceId("remote")
        val file = FileShareInfo.SharedFile(
            name = "doc.pdf",
            mimeType = "application/pdf",
            size = 123,
            blobKey = "blob-remote",
            checksum = "abc",
            sharedAt = Clock.System.now(),
            expiresAt = Clock.System.now() + 1.hours,
            availableOn = setOf("connector"),
            connectorRefs = mapOf("connector" to RemoteBlobRef("ref")),
        )
        var capturedRequest: FileShareInfo.DeleteRequest? = null

        every { context.cacheDir } returns cacheDir
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { handler.upsertDeleteRequest(any()) } coAnswers {
            capturedRequest = firstArg()
        }
        coEvery { handler.currentOwn() } answers {
            FileShareInfo(deleteRequests = listOfNotNull(capturedRequest))
        }

        val result = createService().deleteFile(remoteDeviceId, file)

        result shouldBe FileShareService.DeleteResult.Requested
        capturedRequest!!.targetDeviceId shouldBe remoteDeviceId.id
        capturedRequest!!.blobKey shouldBe file.blobKey
        coVerify(exactly = 1) { publisher.publishNow() }
        coVerify(exactly = 0) { blobManager.delete(any(), any(), any(), any()) }

        cacheDir.deleteRecursively()
    }

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
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)
        coEvery {
            blobManager.put(any(), any(), any(), any(), any(), any(), any())
        } returns BlobManager.PutResult(
            successful = emptySet(),
            perConnectorErrors = mapOf(
                connectorA to BlobFileTooLargeException(
                    connectorId = connectorA,
                    maxFileBytes = largerCap,
                    requestedBytes = fileBytes.size.toLong(),
                ),
                connectorB to BlobFileTooLargeException(
                    connectorId = connectorB,
                    maxFileBytes = smallerCap,
                    requestedBytes = fileBytes.size.toLong(),
                ),
            ),
        )

        val result = createService().shareFile(uri)

        result.shouldBeInstanceOf<FileShareService.ShareResult.FileTooLarge>()
        result.maxBytes shouldBe smallerCap
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

        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)
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

        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA)
        coEvery {
            blobManager.put(any(), any(), any(), any(), any(), any(), any())
        } returns BlobManager.PutResult(
            successful = emptySet(),
            perConnectorErrors = mapOf(
                connectorA to BlobQuotaExceededException(
                    connectorId = connectorA,
                    usedBytes = 100,
                    totalBytes = 100,
                    accountLabel = null,
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

        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)
        coEvery {
            blobManager.put(any(), any(), any(), any(), any(), any(), any())
        } returns BlobManager.PutResult(
            successful = emptySet(),
            perConnectorErrors = mapOf(
                connectorA to BlobFileTooLargeException(
                    connectorId = connectorA,
                    maxFileBytes = 10L,
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
    fun `shareFile returns SourceUnavailable when staging source open throws FileNotFoundException`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } throws FileNotFoundException("gone")
        every { syncSettings.deviceId } returns ownDeviceId
        // Two connectors -> staging tier, which opens the URI directly before BlobManager.put.
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.SourceUnavailable
        coVerify(exactly = 0) { blobManager.put(any(), any(), any(), any(), any(), any(), any()) }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile returns SourceUnavailable when staging source open throws SecurityException`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        // A revoked read grant surfaces as SecurityException, which is NOT an IOException — it must
        // still be mapped rather than crashing the app.
        every { contentResolver.openInputStream(uri) } throws SecurityException("permission revoked")
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.SourceUnavailable

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile returns SourceUnavailable when staging read fails mid-copy`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        // Stream opens fine, then the provider drops mid-read (e.g. cloud file evicted).
        every { contentResolver.openInputStream(uri) } answers {
            object : java.io.InputStream() {
                override fun read(): Int = throw IOException("mid-read boom")
                override fun read(b: ByteArray): Int = throw IOException("mid-read boom")
                override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("mid-read boom")
            }
        }
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.SourceUnavailable

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile returns SourceUnavailable when single-connector pre-count and staging fallback both fail`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        // Single connector + unsized probe -> pre-count tier. Every open fails: the pre-count read
        // routes to the staging fallback, which re-opens and maps the still-dead URI to
        // SourceUnavailable. Without the fix, the thrown FileNotFoundException escapes -> crash.
        every { contentResolver.openInputStream(uri) } throws FileNotFoundException("gone")
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA)

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.SourceUnavailable
        coVerify(exactly = 0) { blobManager.put(any(), any(), any(), any(), any(), any(), any()) }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile returns SourceUnavailable when single-connector streaming re-open fails`() = runTest2 {
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
        // First open (pre-count) succeeds; the streaming re-open inside BlobManager.put fails.
        var opens = 0
        every { contentResolver.openInputStream(uri) } answers {
            opens++
            if (opens == 1) ByteArrayInputStream(fileBytes) else throw FileNotFoundException("gone")
        }
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA)
        // Drive the real openSource lambda so a genuine SourceUnavailableException lands in
        // perConnectorErrors, then assert that an all-source-failure put maps to SourceUnavailable
        // (the mapAllFailedToShareResult path for single-connector streaming tiers).
        coEvery { blobManager.put(any(), any(), any(), any(), any(), any(), any()) } answers {
            val openSource = arg<() -> Source>(3)
            val err = try {
                openSource()
                error("expected streaming re-open to throw")
            } catch (e: Throwable) {
                e
            }
            BlobManager.PutResult(successful = emptySet(), perConnectorErrors = mapOf(connectorA to err))
        }

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.SourceUnavailable

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile returns SourceUnavailable when single-connector streaming read fails mid-upload`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val fileBytes = ByteArray(42) { it.toByte() }
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        // Force UriProbe.Unsized (null size cursor) so the tier choice is deliberate: single
        // connector + Unsized -> Tier B′ (pre-count then stream).
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        // Pre-count open reads fine; the streaming re-open opens fine but throws mid-read (grant
        // revoked / cloud file evicted while uploading).
        var opens = 0
        every { contentResolver.openInputStream(uri) } answers {
            opens++
            if (opens == 1) {
                ByteArrayInputStream(fileBytes)
            } else {
                object : java.io.InputStream() {
                    override fun read(): Int = throw SecurityException("revoked mid-read")
                    override fun read(b: ByteArray, off: Int, len: Int): Int = throw SecurityException("revoked mid-read")
                }
            }
        }
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA)
        // Drive the real openSource lambda and READ it (not just open) so the mid-read failure is
        // exercised and mapped to SourceUnavailableException, then bucketed to SourceUnavailable.
        coEvery { blobManager.put(any(), any(), any(), any(), any(), any(), any()) } answers {
            val openSource = arg<() -> Source>(3)
            val err = try {
                openSource().use { src -> src.read(Buffer(), 8192L) }
                error("expected streaming read to throw")
            } catch (e: Throwable) {
                e
            }
            BlobManager.PutResult(successful = emptySet(), perConnectorErrors = mapOf(connectorA to err))
        }

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.SourceUnavailable

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile aborts the finalized blob and returns SourceUnavailable on B-prime URI content change`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val ref = RemoteBlobRef("ref-1")
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        // Single connector + unsized probe -> pre-count tier. First open (pre-count) and second
        // open (encrypt/upload) return DIFFERENT bytes, so the checksum cross-check trips after the
        // blob is already finalized on the server.
        var opens = 0
        every { contentResolver.openInputStream(uri) } answers {
            opens++
            if (opens == 1) ByteArrayInputStream(byteArrayOf(1, 2, 3)) else ByteArrayInputStream(byteArrayOf(9, 9, 9))
        }
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA)
        coEvery { blobManager.put(any(), any(), any(), any(), any(), any(), any()) } answers {
            val openSource = arg<() -> Source>(3)
            openSource().use { src ->
                val sink = Buffer()
                while (src.read(sink, 8192L) != -1L) sink.clear()
            }
            BlobManager.PutResult(
                successful = setOf(connectorA),
                perConnectorErrors = emptyMap(),
                remoteRefs = mapOf(connectorA to ref),
            )
        }
        coEvery { blobManager.abortPostFinalize(any(), any(), any()) } returns emptyList<Unit>()

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.SourceUnavailable
        // The finalized-but-unreferenced blob must be aborted, not orphaned.
        coVerify(exactly = 1) {
            blobManager.abortPostFinalize(ownDeviceId, FileShareModule.MODULE_ID, mapOf(connectorA to ref))
        }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile does NOT abort the blob when a post-upsert sync failure occurs`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(ByteArray(42)) }
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)
        coEvery { blobManager.put(any(), any(), any(), any(), any(), any(), any()) } returns BlobManager.PutResult(
            successful = setOf(connectorA, connectorB),
            perConnectorErrors = emptyMap(),
            remoteRefs = mapOf(connectorA to RemoteBlobRef("ref-a"), connectorB to RemoteBlobRef("ref-b")),
        )
        coEvery { handler.upsertFile(any()) } just runs
        coEvery { handler.currentOwn() } returns FileShareInfo()
        // The forced sync fails AFTER upsertFile has recorded the SharedFile. Aborting the blob now
        // would dangle that metadata, so the blob must be kept (writeFlow re-syncs later).
        coEvery { syncManager.sync(any<SyncOptions>()) } throws IOException("sync boom")

        shouldThrow<IOException> { createService().shareFile(uri) }

        coVerify(exactly = 0) { blobManager.abortPostFinalize(any(), any(), any()) }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile succeeds without aborting the blob on the happy path`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(ByteArray(42)) }
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)
        coEvery { blobManager.put(any(), any(), any(), any(), any(), any(), any()) } returns BlobManager.PutResult(
            successful = setOf(connectorA, connectorB),
            perConnectorErrors = emptyMap(),
            remoteRefs = mapOf(connectorA to RemoteBlobRef("ref-a"), connectorB to RemoteBlobRef("ref-b")),
        )
        coEvery { handler.upsertFile(any()) } just runs
        coEvery { handler.currentOwn() } returns FileShareInfo()

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.Success
        coVerify(exactly = 0) { blobManager.abortPostFinalize(any(), any(), any()) }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `shareFile aborts the finalized blob when cancelled before upsert`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val remoteRefs = mapOf(connectorA to RemoteBlobRef("ref-a"), connectorB to RemoteBlobRef("ref-b"))
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(ByteArray(42)) }
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)
        coEvery { blobManager.put(any(), any(), any(), any(), any(), any(), any()) } returns BlobManager.PutResult(
            successful = setOf(connectorA, connectorB),
            perConnectorErrors = emptyMap(),
            remoteRefs = remoteRefs,
        )
        // Cancellation lands inside the cleanup window (after finalize, before the SharedFile is
        // recorded) — the finalized blob must be aborted.
        coEvery { handler.upsertFile(any()) } throws CancellationException("cancelled")
        coEvery { blobManager.abortPostFinalize(any(), any(), any()) } returns emptyList<Unit>()

        val result = createService().shareFile(uri)

        result shouldBe FileShareService.ShareResult.Cancelled
        coVerify(exactly = 1) {
            blobManager.abortPostFinalize(ownDeviceId, FileShareModule.MODULE_ID, remoteRefs)
        }

        cacheDir.deleteRecursively()
    }

    @Test
    fun `cancellation during upsert still records the file and does not abort the blob`() = runTest2 {
        val cacheDir = java.nio.file.Files.createTempDirectory("files-service-test").toFile()
        val ownDeviceId = DeviceId("self")
        val connectorA = ConnectorId(ConnectorType.OCTISERVER, "srv-a.example.com", "acc-a")
        val connectorB = ConnectorId(ConnectorType.OCTISERVER, "srv-b.example.com", "acc-b")
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, null, null, null, null) } returns null
        every { contentResolver.getType(uri) } returns "application/octet-stream"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(ByteArray(42)) }
        every { syncSettings.deviceId } returns ownDeviceId
        coEvery { blobManager.configuredConnectorIds() } returns setOf(connectorA, connectorB)
        coEvery { blobManager.put(any(), any(), any(), any(), any(), any(), any()) } returns BlobManager.PutResult(
            successful = setOf(connectorA, connectorB),
            perConnectorErrors = emptyMap(),
            remoteRefs = mapOf(connectorA to RemoteBlobRef("ref-a"), connectorB to RemoteBlobRef("ref-b")),
        )
        // upsertFile suspends; we cancel the share while it's mid-record. Because it runs under
        // NonCancellable, it must finish (recording the SharedFile) and the blob must NOT be aborted
        // — otherwise the metadata would dangle. Without the NonCancellable guard, upsertFile would
        // throw CancellationException here and the finally block would abort the blob.
        val upsertReached = CompletableDeferred<Unit>()
        val releaseUpsert = CompletableDeferred<Unit>()
        var upsertCompleted = false
        coEvery { handler.upsertFile(any()) } coAnswers {
            upsertReached.complete(Unit)
            releaseUpsert.await()
            upsertCompleted = true
        }
        coEvery { handler.currentOwn() } returns FileShareInfo()
        coEvery { blobManager.abortPostFinalize(any(), any(), any()) } returns emptyList<Unit>()

        val service = createService()
        val job = launch { service.shareFile(uri) }
        upsertReached.await()
        job.cancel()
        releaseUpsert.complete(Unit)
        job.join()

        upsertCompleted shouldBe true
        coVerify(exactly = 0) { blobManager.abortPostFinalize(any(), any(), any()) }

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
