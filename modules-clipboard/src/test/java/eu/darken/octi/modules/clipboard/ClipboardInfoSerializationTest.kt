package eu.darken.octi.modules.clipboard

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class ClipboardInfoSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `round-trip with text content`() {
        val info = ClipboardInfo(
            type = ClipboardInfo.Type.SIMPLE_TEXT,
            data = "Hello World".encodeUtf8(),
        )
        val encoded = json.encodeToString(info)
        val decoded = json.decodeFromString<ClipboardInfo>(encoded)
        decoded shouldBe info
    }

    @Test
    fun `round-trip with empty content`() {
        val info = ClipboardInfo()
        val encoded = json.encodeToString(info)
        val decoded = json.decodeFromString<ClipboardInfo>(encoded)
        decoded shouldBe info
    }

    @Test
    fun `wire format - ByteString as base64`() {
        val info = ClipboardInfo(
            type = ClipboardInfo.Type.SIMPLE_TEXT,
            data = "Hello".encodeUtf8(),
        )
        val encoded = json.encodeToString(info)
        // "Hello" in base64 is "SGVsbG8="
        encoded.toComparableJson() shouldBe """
            {
                "type": "SIMPLE_TEXT",
                "data": "SGVsbG8="
            }
        """.toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """{"type":"SIMPLE_TEXT","data":"SGVsbG8="}"""
        val decoded = json.decodeFromString<ClipboardInfo>(moshiJson)
        decoded.type shouldBe ClipboardInfo.Type.SIMPLE_TEXT
        decoded.data shouldBe "Hello".encodeUtf8()
    }

    @Test
    fun `Type enum wire names are stable`() {
        json.encodeToString(ClipboardInfo.Type.EMPTY) shouldBe "\"EMPTY\""
        json.encodeToString(ClipboardInfo.Type.SIMPLE_TEXT) shouldBe "\"SIMPLE_TEXT\""
    }

    @Test
    fun `ClipboardSerializer round-trip via ByteString`() {
        val serializer = ClipboardSerializer(json)
        val info = ClipboardInfo(
            type = ClipboardInfo.Type.SIMPLE_TEXT,
            data = "Hello World".encodeUtf8(),
        )
        val bytes = serializer.serialize(info)
        val deserialized = serializer.deserialize(bytes)
        deserialized shouldBe info
    }

    @Test
    fun `ClipboardSerializer deserializes Moshi-written ByteString payload`() {
        val moshiPayload = """{"type":"SIMPLE_TEXT","data":"SGVsbG8="}"""
        val serializer = ClipboardSerializer(json)
        val result = serializer.deserialize(moshiPayload.encodeUtf8())
        result.type shouldBe ClipboardInfo.Type.SIMPLE_TEXT
        result.data shouldBe "Hello".encodeUtf8()
    }

    @Test
    fun `ClipboardSerializer serialize output matches Moshi wire format`() {
        val serializer = ClipboardSerializer(json)
        val info = ClipboardInfo(
            type = ClipboardInfo.Type.SIMPLE_TEXT,
            data = "Hello".encodeUtf8(),
        )
        val bytes = serializer.serialize(info)
        bytes.utf8().toComparableJson() shouldBe """
            {"type":"SIMPLE_TEXT","data":"SGVsbG8="}
        """.toComparableJson()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """{"type":"EMPTY","data":"","mimeType":"text/plain"}"""
        val decoded = json.decodeFromString<ClipboardInfo>(futureJson)
        decoded.type shouldBe ClipboardInfo.Type.EMPTY
    }
}
