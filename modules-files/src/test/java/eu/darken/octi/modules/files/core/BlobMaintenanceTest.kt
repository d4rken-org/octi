package eu.darken.octi.modules.files.core

import android.content.Context
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.datastore.value
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobCacheDirs
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobMetadata
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class BlobMaintenanceTest : BaseTest() {

    private val context = mockk<Context>()
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()
    private val blobManager = mockk<BlobManager>()
    private val storageStatusManager = mockk<eu.darken.octi.sync.core.blob.StorageStatusManager>(relaxed = true)
    private val fileShareHandler = mockk<FileShareHandler>()
    private val fileShareCache = mockk<FileShareCache>()
    private val fileShareSettings = mockk<FileShareSettings>()
    private val pendingDeletes = mockk<DataStoreValue<Map<String, PendingDelete>>>()
    private val syncSettings = mockk<SyncSettings>()

    @TempDir
    lateinit var tempDir: File

    private val selfDeviceId = DeviceId("self-device")
    private val connectorA = ConnectorId(ConnectorType.OCTISERVER, "server-a.example.com", "acc-1")
    private val connectorB = ConnectorId(ConnectorType.GDRIVE, "gdrive", "acc-2")

    @BeforeEach
    fun setup() {
        every { context.cacheDir } returns tempDir
        every { syncSettings.deviceId } returns selfDeviceId
        every { fileShareSettings.pendingDeletes } returns pendingDeletes
        every { blobManager.trimBackoff(any()) } just runs
    }

    private fun createBlobMaintenance(testScope: kotlinx.coroutines.test.TestScope): BlobMaintenance {
        return BlobMaintenance(
            scope = testScope.backgroundScope,
            dispatcherProvider = dispatcherProvider,
            blobManager = blobManager,
            storageStatusManager = storageStatusManager,
            fileShareHandler = fileShareHandler,
            fileShareCache = fileShareCache,
            fileShareSettings = fileShareSettings,
            syncSettings = syncSettings,
            blobCacheDirs = BlobCacheDirs(context),
        )
    }

    private fun makeFile(
        name: String = "test.pdf",
        blobKey: String = "blob-1",
        checksum: String = "abc123",
        expiresAt: kotlin.time.Instant = Clock.System.now() + 2.hours,
        availableOn: Set<String> = setOf(connectorA.idString),
        connectorRefs: Map<String, RemoteBlobRef> = emptyMap(),
    ) = FileShareInfo.SharedFile(
        name = name,
        mimeType = "application/pdf",
        size = 1024,
        blobKey = blobKey,
        checksum = checksum,
        sharedAt = Clock.System.now(),
        expiresAt = expiresAt,
        availableOn = availableOn,
        connectorRefs = connectorRefs,
    )

    private fun makeModuleData(files: List<FileShareInfo.SharedFile>) = ModuleData(
        modifiedAt = Clock.System.now(),
        deviceId = selfDeviceId,
        moduleId = FileShareModule.MODULE_ID,
        data = FileShareInfo(files = files),
    )

    private fun makeTombstone(blobKey: String, remaining: Set<String> = emptySet()) = PendingDelete(
        blobKey = blobKey,
        remainingConnectors = remaining,
        createdAt = Clock.System.now(),
    )

    @Nested
    inner class `retryMirrorUploads` {

        @Test
        fun `downloads from available, uploads to missing, updates both locations together`() = runTest2 {
            val file = makeFile(
                blobKey = "blob-mirror",
                checksum = "", // empty checksum skips integrity check
                availableOn = setOf(connectorA.idString),
                connectorRefs = mapOf(connectorA.idString to RemoteBlobRef("srv-a-blob")),
            )
            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(
                connectorA.idString to connectorA,
                connectorB.idString to connectorB,
            )
            every { pendingDeletes.flow } returns flowOf(emptyMap())
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery { fileShareHandler.updateLocations(any(), any(), any()) } just runs
            coEvery {
                blobManager.get(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey(file.blobKey),
                    candidates = mapOf(connectorA to RemoteBlobRef("srv-a-blob")),
                    expectedPlaintextSize = file.size,
                    openSink = any(),
                )
            } coAnswers {
                // Invoke the openSink lambda so BlobMaintenance can read the (empty) staged file
                val openSink = arg<() -> okio.Sink>(5)
                openSink().close()
                BlobMetadata(size = 0, createdAt = Clock.System.now(), checksum = "")
            }
            coEvery {
                blobManager.put(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey(file.blobKey),
                    openSource = any(),
                    metadata = any(),
                    eligibleConnectors = setOf(connectorB),
                )
            } returns BlobManager.PutResult(
                successful = setOf(connectorB),
                perConnectorErrors = emptyMap(),
                remoteRefs = mapOf(connectorB to RemoteBlobRef("remote-ref-b")),
            )

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 1) {
                fileShareHandler.updateLocations(
                    blobKey = file.blobKey,
                    newAvailableOn = setOf(connectorA.idString, connectorB.idString),
                    newConnectorRefs = mapOf(
                        connectorA.idString to RemoteBlobRef("srv-a-blob"),
                        connectorB.idString to RemoteBlobRef("remote-ref-b"),
                    ),
                )
            }
        }

        @Test
        fun `skips pendingDeletes and expired files`() = runTest2 {
            val pendingFile = makeFile(blobKey = "pending-blob", availableOn = setOf(connectorA.idString))
            val expiredFile = makeFile(
                blobKey = "expired-blob",
                expiresAt = Clock.System.now() - 1.hours,
                availableOn = setOf(connectorA.idString),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(pendingFile, expiredFile))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(
                connectorA.idString to connectorA,
                connectorB.idString to connectorB,
            )
            every { pendingDeletes.flow } returns flowOf(mapOf("pending-blob" to makeTombstone("pending-blob")))
            coEvery { blobManager.isBackedOff(any(), any()) } returns false

            // pruneExpired and retryPendingDeletes also run, stub them
            coEvery { blobManager.delete(any(), any(), any(), any()) } returns emptySet()
            coEvery { fileShareHandler.removeFile(any()) } just runs
            coEvery { fileShareHandler.updateLocations(any(), any(), any()) } just runs
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            // No downloads or uploads should happen for mirror retry
            coVerify(exactly = 0) {
                blobManager.get(any(), any(), any(), any(), any(), any())
            }
            coVerify(exactly = 0) {
                blobManager.put(any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `skips file when all connectors already available`() = runTest2 {
            val file = makeFile(
                blobKey = "all-mirrored",
                availableOn = setOf(connectorA.idString, connectorB.idString),
                connectorRefs = mapOf(
                    connectorA.idString to RemoteBlobRef("srv-a"),
                    connectorB.idString to RemoteBlobRef("all-mirrored"),
                ),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(
                connectorA.idString to connectorA,
                connectorB.idString to connectorB,
            )
            every { pendingDeletes.flow } returns flowOf(emptyMap())
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 0) {
                blobManager.get(any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Nested
    inner class `retryPendingDeletes` {

        @Test
        fun `removes orphaned keys not in cache`() = runTest2 {
            // "orphan-key" is in pendingDeletes but not in files list
            val orphanMap = mapOf("orphan-key" to makeTombstone("orphan-key"))
            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(emptyList())
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(connectorA.idString to connectorA)
            every { pendingDeletes.flow } returns flowOf(orphanMap)
            var capturedValue: Map<String, PendingDelete>? = null
            coEvery { pendingDeletes.update(any()) } coAnswers {
                val transform = firstArg<(Map<String, PendingDelete>) -> Map<String, PendingDelete>?>()
                val result = transform(orphanMap) ?: emptyMap()
                capturedValue = result
                DataStoreValue.Updated(orphanMap, result)
            }

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            // The orphan key should be removed from pendingDeletes
            capturedValue shouldBe emptyMap()
        }

        @Test
        fun `retries with configured connectors, removes on full success`() = runTest2 {
            val file = makeFile(
                blobKey = "pending-file",
                availableOn = setOf(connectorA.idString),
                connectorRefs = mapOf(connectorA.idString to RemoteBlobRef("srv-a")),
            )
            val initialMap = mapOf("pending-file" to makeTombstone("pending-file"))

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(connectorA.idString to connectorA)
            every { pendingDeletes.flow } returns flowOf(initialMap)
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery {
                blobManager.delete(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey("pending-file"),
                    targets = mapOf(connectorA to RemoteBlobRef("srv-a")),
                )
            } returns setOf(connectorA)
            coEvery { fileShareHandler.removeFile("pending-file") } just runs
            coEvery { fileShareHandler.updateLocations(any(), any(), any()) } just runs

            var capturedValue: Map<String, PendingDelete>? = null
            coEvery { pendingDeletes.update(any()) } coAnswers {
                val transform = firstArg<(Map<String, PendingDelete>) -> Map<String, PendingDelete>?>()
                val result = transform(initialMap) ?: emptyMap()
                capturedValue = result
                DataStoreValue.Updated(initialMap, result)
            }

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 1) { fileShareHandler.removeFile("pending-file") }
            // pendingDeletes should have the key removed
            capturedValue shouldBe emptyMap()
        }

        @Test
        fun `updates both locations together on partial delete`() = runTest2 {
            val file = makeFile(
                blobKey = "partial-delete",
                availableOn = setOf(connectorA.idString, connectorB.idString),
                connectorRefs = mapOf(
                    connectorA.idString to RemoteBlobRef("srv-a"),
                    connectorB.idString to RemoteBlobRef("partial-delete"),
                ),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(
                connectorA.idString to connectorA,
                connectorB.idString to connectorB,
            )
            every { pendingDeletes.flow } returns flowOf(mapOf("partial-delete" to makeTombstone("partial-delete")))
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery {
                blobManager.delete(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey("partial-delete"),
                    targets = mapOf(
                        connectorA to RemoteBlobRef("srv-a"),
                        connectorB to RemoteBlobRef("partial-delete"),
                    ),
                )
            } returns setOf(connectorA) // Only A succeeds
            coEvery { fileShareHandler.updateLocations(any(), any(), any()) } just runs
            coEvery { fileShareHandler.removeFile(any()) } just runs
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 0) { fileShareHandler.removeFile("partial-delete") }
            coVerify(exactly = 1) {
                fileShareHandler.updateLocations(
                    blobKey = "partial-delete",
                    newAvailableOn = setOf(connectorB.idString),
                    newConnectorRefs = mapOf(connectorB.idString to RemoteBlobRef("partial-delete")),
                )
            }
        }

        @Test
        fun `migrated empty remainingConnectors tries all configured connectors`() = runTest2 {
            val file = makeFile(
                blobKey = "migrated-blob",
                availableOn = setOf(connectorA.idString, connectorB.idString),
                connectorRefs = mapOf(
                    connectorA.idString to RemoteBlobRef("srv-a"),
                    connectorB.idString to RemoteBlobRef("migrated-blob"),
                ),
            )
            // Migrated tombstone has empty remainingConnectors (unknown from old Set<String> format)
            val migratedTombstone = PendingDelete(
                blobKey = "migrated-blob",
                remainingConnectors = emptySet(),
                createdAt = Clock.System.now(),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(
                connectorA.idString to connectorA,
                connectorB.idString to connectorB,
            )
            every { pendingDeletes.flow } returns flowOf(mapOf("migrated-blob" to migratedTombstone))
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery {
                blobManager.delete(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey("migrated-blob"),
                    targets = mapOf(
                        connectorA to RemoteBlobRef("srv-a"),
                        connectorB to RemoteBlobRef("migrated-blob"),
                    ),
                )
            } returns setOf(connectorA, connectorB)
            coEvery { fileShareHandler.removeFile("migrated-blob") } just runs
            coEvery { fileShareHandler.updateLocations(any(), any(), any()) } just runs
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 1) { fileShareHandler.removeFile("migrated-blob") }
        }
    }

    @Nested
    inner class `pruneExpired` {

        @Test
        fun `deletes blobs and removes file entry on full success`() = runTest2 {
            val file = makeFile(
                blobKey = "expired-full",
                expiresAt = Clock.System.now() - 1.hours,
                availableOn = setOf(connectorA.idString),
                connectorRefs = mapOf(connectorA.idString to RemoteBlobRef("srv-a")),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(connectorA.idString to connectorA)
            every { pendingDeletes.flow } returns flowOf(emptyMap())
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery {
                blobManager.delete(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey("expired-full"),
                    targets = mapOf(connectorA to RemoteBlobRef("srv-a")),
                )
            } returns setOf(connectorA)
            coEvery { fileShareHandler.removeFile("expired-full") } just runs
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 1) { fileShareHandler.removeFile("expired-full") }
        }

        @Test
        fun `partial expiry shrinks both availableOn and connectorRefs together`() = runTest2 {
            // Bug 3 regression guard: pruneExpired must patch BOTH locations, not just availableOn.
            val file = makeFile(
                blobKey = "expired-partial",
                expiresAt = Clock.System.now() - 1.hours,
                availableOn = setOf(connectorA.idString, connectorB.idString),
                connectorRefs = mapOf(
                    connectorA.idString to RemoteBlobRef("srv-a"),
                    connectorB.idString to RemoteBlobRef("expired-partial"),
                ),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(
                connectorA.idString to connectorA,
                connectorB.idString to connectorB,
            )
            every { pendingDeletes.flow } returns flowOf(emptyMap())
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery {
                blobManager.delete(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey("expired-partial"),
                    targets = mapOf(
                        connectorA to RemoteBlobRef("srv-a"),
                        connectorB to RemoteBlobRef("expired-partial"),
                    ),
                )
            } returns setOf(connectorA) // Only A succeeds
            coEvery { fileShareHandler.updateLocations(any(), any(), any()) } just runs
            coEvery { fileShareHandler.removeFile(any()) } just runs
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 0) { fileShareHandler.removeFile("expired-partial") }
            coVerify(exactly = 1) {
                fileShareHandler.updateLocations(
                    blobKey = "expired-partial",
                    newAvailableOn = setOf(connectorB.idString),
                    newConnectorRefs = mapOf(connectorB.idString to RemoteBlobRef("expired-partial")),
                )
            }
        }

        @Test
        fun `forgets stale entries after STALE_FORGET_DELAY`() = runTest2 {
            // File expired AND past the 24h stale forget delay, no configured connectors overlap
            val file = makeFile(
                blobKey = "stale-forgotten",
                expiresAt = Clock.System.now() - 25.hours, // expired 25h ago → 1h past 24h delay
                availableOn = setOf("old-disconnected-connector"),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(connectorA.idString to connectorA)
            every { pendingDeletes.flow } returns flowOf(emptyMap())
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery { fileShareHandler.removeFile("stale-forgotten") } just runs
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 1) { fileShareHandler.removeFile("stale-forgotten") }
        }

        @Test
        fun `keeps stale entries within STALE_FORGET_DELAY`() = runTest2 {
            // File expired but within the 24h stale forget delay, no configured connectors overlap
            val file = makeFile(
                blobKey = "stale-recent",
                expiresAt = Clock.System.now() - 1.hours, // expired 1h ago → 23h before forget delay
                availableOn = setOf("old-disconnected-connector"),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(connectorA.idString to connectorA)
            every { pendingDeletes.flow } returns flowOf(emptyMap())
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 0) { fileShareHandler.removeFile("stale-recent") }
        }

        @Test
        fun `skips connectors listed in availableOn but missing from connectorRefs`() = runTest2 {
            // Regression guard: earlier code fell back to blobKey as refStr, producing a bogus
            // RemoteBlobRef. The fix skips the connector entirely so no delete call is made with
            // an invalid id.
            val file = makeFile(
                blobKey = "missing-ref",
                expiresAt = Clock.System.now() - 1.hours,
                availableOn = setOf(connectorA.idString),
                connectorRefs = emptyMap(),
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(file))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(connectorA.idString to connectorA)
            every { pendingDeletes.flow } returns flowOf(emptyMap())
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery { pendingDeletes.update(any()) } returns DataStoreValue.Updated(emptyMap(), emptyMap())

            val maintenance = createBlobMaintenance(this)
            maintenance.runOnce()

            coVerify(exactly = 0) { blobManager.delete(any(), any(), any(), any()) }
            coVerify(exactly = 0) { fileShareHandler.removeFile(any()) }
            coVerify(exactly = 0) { fileShareHandler.updateLocations(any(), any(), any()) }
        }
    }

    @Nested
    inner class `runMirrorUploadsFor` {

        @Test
        fun `does not run pendingDeletes or pruneExpired or trimBackoff`() = runTest2 {
            val targetFile = makeFile(
                blobKey = "blob-target",
                checksum = "", // empty checksum skips integrity check
                availableOn = setOf(connectorA.idString),
                connectorRefs = mapOf(connectorA.idString to RemoteBlobRef("srv-a-blob")),
            )
            val unrelatedFile = makeFile(
                blobKey = "blob-unrelated",
                checksum = "",
                availableOn = setOf(connectorA.idString),
                connectorRefs = mapOf(connectorA.idString to RemoteBlobRef("srv-a-other")),
                expiresAt = Clock.System.now() - 1.hours, // expired — runOnce would prune it
            )

            coEvery { fileShareCache.get(selfDeviceId) } returns makeModuleData(listOf(targetFile, unrelatedFile))
            coEvery { blobManager.configuredConnectorsByIdString() } returns mapOf(
                connectorA.idString to connectorA,
                connectorB.idString to connectorB,
            )
            every { pendingDeletes.flow } returns flowOf(
                mapOf("orphan-tombstone" to makeTombstone("orphan-tombstone")),
            )
            coEvery { blobManager.isBackedOff(any(), any()) } returns false
            coEvery { fileShareHandler.updateLocations(any(), any(), any()) } just runs
            coEvery {
                blobManager.get(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey(targetFile.blobKey),
                    candidates = mapOf(connectorA to RemoteBlobRef("srv-a-blob")),
                    expectedPlaintextSize = targetFile.size,
                    openSink = any(),
                )
            } coAnswers {
                val openSink = arg<() -> okio.Sink>(5)
                openSink().close()
                BlobMetadata(size = 0, createdAt = Clock.System.now(), checksum = "")
            }
            coEvery {
                blobManager.put(
                    deviceId = selfDeviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey(targetFile.blobKey),
                    openSource = any(),
                    metadata = any(),
                    eligibleConnectors = setOf(connectorB),
                )
            } returns BlobManager.PutResult(
                successful = setOf(connectorB),
                perConnectorErrors = emptyMap(),
                remoteRefs = mapOf(connectorB to RemoteBlobRef("remote-ref-b")),
            )

            val maintenance = createBlobMaintenance(this)
            maintenance.runMirrorUploadsFor(setOf(targetFile.blobKey))

            // Targeted retry must mirror the target row…
            coVerify(exactly = 1) {
                fileShareHandler.updateLocations(
                    blobKey = targetFile.blobKey,
                    newAvailableOn = setOf(connectorA.idString, connectorB.idString),
                    newConnectorRefs = mapOf(
                        connectorA.idString to RemoteBlobRef("srv-a-blob"),
                        connectorB.idString to RemoteBlobRef("remote-ref-b"),
                    ),
                )
            }
            // …and absolutely nothing else.
            coVerify(exactly = 0) { blobManager.delete(any(), any(), any(), any()) }
            coVerify(exactly = 0) { fileShareHandler.removeFile(any()) }
            coVerify(exactly = 0) { blobManager.trimBackoff(any()) }
            coVerify(exactly = 0) { pendingDeletes.update(any()) }
        }
    }
}
