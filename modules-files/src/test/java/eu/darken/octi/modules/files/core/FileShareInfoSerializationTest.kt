package eu.darken.octi.modules.files.core

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FileShareInfoSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
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
}
