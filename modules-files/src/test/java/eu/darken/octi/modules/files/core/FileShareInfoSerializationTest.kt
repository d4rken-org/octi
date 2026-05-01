package eu.darken.octi.modules.files.core

import eu.darken.octi.sync.core.RemoteBlobRef
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class FileShareInfoSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `wire format stability`() {
        val sharedAt = Instant.parse("2026-01-01T00:00:00Z")
        val expiresAt = Instant.parse("2026-01-03T00:00:00Z")
        val info = FileShareInfo(
            files = listOf(
                FileShareInfo.SharedFile(
                    name = "document.pdf",
                    mimeType = "application/pdf",
                    size = 1024,
                    blobKey = "key-1",
                    checksum = "abc",
                    sharedAt = sharedAt,
                    expiresAt = expiresAt,
                    availableOn = setOf("server-a", "gdrive-b"),
                    connectorRefs = mapOf(
                        "server-a" to RemoteBlobRef("srv-blob-id"),
                        "gdrive-b" to RemoteBlobRef("key-1"),
                    ),
                ),
            ),
            deleteRequests = listOf(
                FileShareInfo.DeleteRequest(
                    targetDeviceId = "device-b",
                    blobKey = "key-2",
                    requestedAt = Instant.parse("2026-01-02T00:00:00Z"),
                    retainUntil = Instant.parse("2026-01-04T00:00:00Z"),
                ),
            ),
        )
        val encoded = json.encodeToString(FileShareInfo.serializer(), info)
        encoded.toComparableJson() shouldBe """
            {
                "files": [
                    {
                        "name": "document.pdf",
                        "mimeType": "application/pdf",
                        "size": 1024,
                        "blobKey": "key-1",
                        "checksum": "abc",
                        "sharedAt": "2026-01-01T00:00:00Z",
                        "expiresAt": "2026-01-03T00:00:00Z",
                        "availableOn": ["server-a", "gdrive-b"],
                        "connectorRefs": {"server-a": "srv-blob-id", "gdrive-b": "key-1"}
                    }
                ],
                "deleteRequests": [
                    {
                        "targetDeviceId": "device-b",
                        "blobKey": "key-2",
                        "requestedAt": "2026-01-02T00:00:00Z",
                        "retainUntil": "2026-01-04T00:00:00Z"
                    }
                ]
            }
        """.toComparableJson()
    }

    @Test
    fun `round-trip empty FileShareInfo`() {
        val original = FileShareInfo()
        val serialized = json.encodeToString(FileShareInfo.serializer(), original)
        val deserialized = json.decodeFromString(FileShareInfo.serializer(), serialized)
        deserialized shouldBe original
    }

    @Test
    fun `round-trip FileShareInfo with files`() {
        val now = Clock.System.now()
        val original = FileShareInfo(
            files = listOf(
                FileShareInfo.SharedFile(
                    name = "document.pdf",
                    mimeType = "application/pdf",
                    size = 1024 * 1024,
                    blobKey = "550e8400-e29b-41d4-a716-446655440000",
                    checksum = "a1b2c3d4e5f6",
                    sharedAt = now,
                    expiresAt = now + 48.hours,
                    availableOn = setOf("octiserver:prod:acc1", "gdrive:default:user@gmail.com"),
                ),
                FileShareInfo.SharedFile(
                    name = "photo.jpg",
                    mimeType = "image/jpeg",
                    size = 512 * 1024,
                    blobKey = "660e8400-e29b-41d4-a716-446655440001",
                    checksum = "b2c3d4e5f6a1",
                    sharedAt = now,
                    expiresAt = now + 24.hours,
                    availableOn = setOf("octiserver:prod:acc1"),
                ),
            ),
        )
        val serialized = json.encodeToString(FileShareInfo.serializer(), original)
        val deserialized = json.decodeFromString(FileShareInfo.serializer(), serialized)
        deserialized shouldBe original
        deserialized.files.size shouldBe 2
        deserialized.files[0].availableOn.size shouldBe 2
    }

    @Test
    fun `round-trip FileShareInfo with delete requests`() {
        val now = Clock.System.now()
        val original = FileShareInfo(
            deleteRequests = listOf(
                FileShareInfo.DeleteRequest(
                    targetDeviceId = "device-b",
                    blobKey = "blob-1",
                    requestedAt = now,
                    retainUntil = now + 24.hours,
                ),
            ),
        )
        val serialized = json.encodeToString(FileShareInfo.serializer(), original)
        val deserialized = json.decodeFromString(FileShareInfo.serializer(), serialized)
        deserialized shouldBe original
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val jsonString = """
            {
                "files": [
                    {
                        "name": "test.txt",
                        "mimeType": "text/plain",
                        "size": 100,
                        "blobKey": "key-1",
                        "checksum": "abc",
                        "sharedAt": "2026-01-01T00:00:00Z",
                        "expiresAt": "2026-01-03T00:00:00Z",
                        "availableOn": ["conn-1"],
                        "futureField": "some-value"
                    }
                ],
                "futureTopLevel": 42
            }
        """
        val deserialized = json.decodeFromString(FileShareInfo.serializer(), jsonString)
        deserialized.files.size shouldBe 1
        deserialized.files[0].name shouldBe "test.txt"
        deserialized.files[0].checksum shouldBe "abc"
    }

    @Test
    fun `empty availableOn defaults to empty set`() {
        val jsonString = """
            {
                "files": [
                    {
                        "name": "test.txt",
                        "mimeType": "text/plain",
                        "size": 100,
                        "blobKey": "key-1",
                        "checksum": "abc",
                        "sharedAt": "2026-01-01T00:00:00Z",
                        "expiresAt": "2026-01-03T00:00:00Z"
                    }
                ]
            }
        """
        val deserialized = json.decodeFromString(FileShareInfo.serializer(), jsonString)
        deserialized.files[0].availableOn shouldBe emptySet()
    }

    @Test
    fun `connectorRefs round-trips correctly`() {
        val now = Clock.System.now()
        val original = FileShareInfo(
            files = listOf(
                FileShareInfo.SharedFile(
                    name = "test.pdf",
                    mimeType = "application/pdf",
                    size = 1024,
                    blobKey = "key-1",
                    checksum = "abc",
                    sharedAt = now,
                    expiresAt = now + 48.hours,
                    availableOn = setOf("server-a", "gdrive-b"),
                    connectorRefs = mapOf(
                        "server-a" to RemoteBlobRef("srv-blob-id-1"),
                        "gdrive-b" to RemoteBlobRef("key-1"),
                    ),
                ),
            ),
        )
        val serialized = json.encodeToString(FileShareInfo.serializer(), original)
        val deserialized = json.decodeFromString(FileShareInfo.serializer(), serialized)
        deserialized shouldBe original
        deserialized.files[0].connectorRefs shouldBe mapOf(
            "server-a" to RemoteBlobRef("srv-blob-id-1"),
            "gdrive-b" to RemoteBlobRef("key-1"),
        )
    }

    @Test
    fun `connectorRefs wire format unchanged after RemoteBlobRef migration`() {
        // Regression guard: a pre-migration JSON payload with bare-string refs must still
        // decode cleanly, and the resulting RemoteBlobRef values must equal the wire strings.
        val jsonString = """
            {
                "files": [
                    {
                        "name": "legacy.pdf",
                        "mimeType": "application/pdf",
                        "size": 42,
                        "blobKey": "logical-1",
                        "checksum": "abc",
                        "sharedAt": "2026-01-01T00:00:00Z",
                        "expiresAt": "2026-01-03T00:00:00Z",
                        "availableOn": ["server-a"],
                        "connectorRefs": {"server-a": "srv-blob-id-1"}
                    }
                ]
            }
        """
        val deserialized = json.decodeFromString(FileShareInfo.serializer(), jsonString)
        deserialized.files[0].connectorRefs shouldBe mapOf("server-a" to RemoteBlobRef("srv-blob-id-1"))
    }

    @Test
    fun `missing connectorRefs deserializes with empty map - backward compat`() {
        val jsonString = """
            {
                "files": [
                    {
                        "name": "old-file.txt",
                        "mimeType": "text/plain",
                        "size": 100,
                        "blobKey": "key-old",
                        "checksum": "xyz",
                        "sharedAt": "2026-01-01T00:00:00Z",
                        "expiresAt": "2026-01-03T00:00:00Z",
                        "availableOn": ["conn-1"]
                    }
                ]
            }
        """
        val deserialized = json.decodeFromString(FileShareInfo.serializer(), jsonString)
        deserialized.files[0].connectorRefs shouldBe emptyMap()
    }
}
