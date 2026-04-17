package eu.darken.octi.modules.files.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FileShareSyncTest : BaseTest() {

    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()
    private val syncSettings = mockk<SyncSettings>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    private val serializer = FileShareSerializer(json)

    private val sync = FileShareSync(
        dispatcherProvider = dispatcherProvider,
        syncSettings = syncSettings,
        syncManager = syncManager,
        fileShareSerializer = serializer,
    )

    private fun sharedFile(
        blobKey: String,
        availableOn: Set<String> = emptySet(),
        connectorRefs: Map<String, RemoteBlobRef> = emptyMap(),
    ) = FileShareInfo.SharedFile(
        name = "file-$blobKey.pdf",
        mimeType = "application/pdf",
        size = 1024,
        blobKey = blobKey,
        checksum = "abc",
        sharedAt = Clock.System.now(),
        expiresAt = Clock.System.now() + 48.hours,
        availableOn = availableOn,
        connectorRefs = connectorRefs,
    )

    @Test
    fun `serialize emits one BlobAttachment per SharedFile with forwarded fields`() {
        val a = sharedFile(
            blobKey = "blob-a",
            availableOn = setOf("srv-1", "gdrive-2"),
            connectorRefs = mapOf(
                "srv-1" to RemoteBlobRef("srv-remote-a"),
                "gdrive-2" to RemoteBlobRef("blob-a"),
            ),
        )
        val b = sharedFile(
            blobKey = "blob-b",
            availableOn = setOf("srv-1"),
            connectorRefs = mapOf("srv-1" to RemoteBlobRef("srv-remote-b")),
        )
        val info = FileShareInfo(files = listOf(a, b))

        val module = sync.serialize(info)

        module.moduleId shouldBe FileShareModule.MODULE_ID
        module.blobs!!.size shouldBe 2
        module.blobs!![0] shouldBe SyncWrite.BlobAttachment(
            logicalKey = "blob-a",
            connectorRefs = mapOf(
                "srv-1" to RemoteBlobRef("srv-remote-a"),
                "gdrive-2" to RemoteBlobRef("blob-a"),
            ),
            availableOn = setOf("srv-1", "gdrive-2"),
        )
        module.blobs!![1] shouldBe SyncWrite.BlobAttachment(
            logicalKey = "blob-b",
            connectorRefs = mapOf("srv-1" to RemoteBlobRef("srv-remote-b")),
            availableOn = setOf("srv-1"),
        )
    }

    @Test
    fun `serialize empty FileShareInfo emits non-null empty blobs list`() {
        val module = sync.serialize(FileShareInfo())
        module.blobs shouldNotBe null
        module.blobs!! shouldBe emptyList()
    }

    @Test
    fun `serialize SharedFile with empty connectorRefs still copies logicalKey`() {
        val file = sharedFile(blobKey = "blob-empty")
        val module = sync.serialize(FileShareInfo(files = listOf(file)))
        module.blobs!!.single() shouldBe SyncWrite.BlobAttachment(
            logicalKey = "blob-empty",
            connectorRefs = emptyMap(),
        )
    }

    @Test
    fun `serialize payload round-trips through the serializer`() {
        val file = sharedFile(
            blobKey = "blob-roundtrip",
            availableOn = setOf("srv-1"),
            connectorRefs = mapOf("srv-1" to RemoteBlobRef("srv-remote")),
        )
        val original = FileShareInfo(files = listOf(file))
        val module = sync.serialize(original)
        val decoded = serializer.deserialize(module.payload)
        decoded shouldBe original
    }
}
