package eu.darken.octi.modules.clipboard.interop

import eu.darken.octi.modules.clipboard.ClipboardInfo
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testhelpers.interop.InteropFixtureSync
import testhelpers.interop.InteropFixtures
import testhelpers.interop.PublishedModuleFixture
import testhelpers.interop.PublishedVector
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verify app-main's ClipboardInfo decoder can consume what octi-web publishes.
 *
 * Pin: type enum + base64-encoded data. ByteString equality must hold across the
 * encode boundary (web emits base64, app-main's ByteStringSerializer reads base64).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebClipboardInteropTest {

    private lateinit var cacheDir: Path

    private val decoder: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @BeforeAll
    fun setUp() {
        cacheDir = InteropFixtureSync.ensureSynced("d4rken-org/octi-web")
    }

    @Test
    fun `web clipboard fixture schema sanity`() {
        val fixture = loadFixture()
        fixture.schemaVersion shouldBe InteropFixtures.FIXTURE_SCHEMA_VERSION
        fixture.module shouldBe "eu.darken.octi.module.core.clipboard"
        fixture.producer shouldBe "d4rken-org/octi-web"
        fixture.vectors.map { it.name } shouldBe
            listOf("EMPTY", "SIMPLE_TEXT_short", "SIMPLE_TEXT_unicode")
    }

    @Test
    fun `web clipboard 'EMPTY' vector decodes to empty data`() {
        val info = decode(vector("EMPTY"))
        info.type shouldBe ClipboardInfo.Type.EMPTY
        info.data.size shouldBe 0
    }

    @Test
    fun `web clipboard 'SIMPLE_TEXT_short' vector decodes ASCII payload`() {
        val info = decode(vector("SIMPLE_TEXT_short"))
        info.type shouldBe ClipboardInfo.Type.SIMPLE_TEXT
        // Web encodes the UTF-8 bytes of "hello clipboard" as base64; round-trip back to the literal.
        info.data shouldBe "hello clipboard".encodeUtf8()
    }

    @Test
    fun `web clipboard 'SIMPLE_TEXT_unicode' vector decodes multi-codepoint payload`() {
        val info = decode(vector("SIMPLE_TEXT_unicode"))
        info.type shouldBe ClipboardInfo.Type.SIMPLE_TEXT
        // Latin-1 supplements + emoji + CJK + Arabic; pin the byte-for-byte round trip.
        info.data shouldBe "café 👋 你好 — العربية".encodeUtf8()
    }

    private fun loadFixture(): PublishedModuleFixture {
        val bytes = Files.readAllBytes(cacheDir.resolve("octi-web-clipboard.json"))
        return InteropFixtures.json.decodeFromString(
            PublishedModuleFixture.serializer(),
            bytes.decodeToString(),
        )
    }

    private fun vector(name: String): PublishedVector {
        val fixture = loadFixture()
        val v = fixture.vectors.firstOrNull { it.name == name }
            ?: error("vector '$name' missing in ${fixture.module}")
        InteropFixtures.verifyVectorIntegrity(v)
        return v
    }

    private fun decode(v: PublishedVector): ClipboardInfo {
        return decoder.decodeFromString(ClipboardInfo.serializer(), v.payloadJson)
    }
}
