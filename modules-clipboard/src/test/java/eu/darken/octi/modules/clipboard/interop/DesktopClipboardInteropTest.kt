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
 * Verify app-main's ClipboardInfo decoder can consume what octi-desktop publishes.
 * Same wire contract as web's clipboard fixtures (type enum + base64-encoded data); only the
 * canonical payload contents differ ("hello from desktop" vs web's "hello clipboard").
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopClipboardInteropTest {

    private lateinit var cacheDir: Path

    private val decoder: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @BeforeAll
    fun setUp() {
        cacheDir = InteropFixtureSync.ensureSynced("d4rken-org/octi-desktop")
    }

    @Test
    fun `desktop clipboard fixture schema sanity`() {
        val fixture = loadFixture()
        fixture.schemaVersion shouldBe InteropFixtures.FIXTURE_SCHEMA_VERSION
        fixture.module shouldBe "eu.darken.octi.module.core.clipboard"
        fixture.producer shouldBe "d4rken-org/octi-desktop"
        fixture.vectors.map { it.name } shouldBe
            listOf("EMPTY", "SIMPLE_TEXT_short", "SIMPLE_TEXT_unicode")
    }

    @Test
    fun `desktop clipboard 'EMPTY' vector decodes to empty data`() {
        val info = decode(vector("EMPTY"))
        info.type shouldBe ClipboardInfo.Type.EMPTY
        info.data.size shouldBe 0
    }

    @Test
    fun `desktop clipboard 'SIMPLE_TEXT_short' vector decodes ASCII payload`() {
        val info = decode(vector("SIMPLE_TEXT_short"))
        info.type shouldBe ClipboardInfo.Type.SIMPLE_TEXT
        info.data shouldBe "hello from desktop".encodeUtf8()
    }

    @Test
    fun `desktop clipboard 'SIMPLE_TEXT_unicode' vector decodes multi-codepoint payload`() {
        val info = decode(vector("SIMPLE_TEXT_unicode"))
        info.type shouldBe ClipboardInfo.Type.SIMPLE_TEXT
        info.data shouldBe "café 👋 你好 — العربية".encodeUtf8()
    }

    private fun loadFixture(): PublishedModuleFixture {
        val bytes = Files.readAllBytes(cacheDir.resolve("octi-desktop-clipboard.json"))
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
