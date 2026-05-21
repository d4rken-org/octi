package eu.darken.octi.modules.meta.interop

import eu.darken.octi.modules.meta.core.MetaInfo
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
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
 * Verify app-main's MetaInfo decoder can consume what octi-web publishes.
 *
 * Loads `octi-web-meta.json` from `.cache/interop-fixtures/d4rken-org/octi-web/<ref>/`,
 * parses each `payloadJson` through the production `MetaInfo` decoder, and asserts
 * field-level values match the canonical inputs declared in octi-web's
 * `tools/generate-fixtures.ts`.
 *
 * Strength model: don't just "decode doesn't throw". Mirror every field of the
 * canonical input so a producer-side rename, retyping, or enum change fails *here*
 * with a useful diff — not silently because `ignoreUnknownKeys=true` swallowed it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebMetaInteropTest {

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
    fun `web meta fixture schema sanity`() {
        val fixture = loadFixture()
        fixture.schemaVersion shouldBe InteropFixtures.FIXTURE_SCHEMA_VERSION
        fixture.module shouldBe "eu.darken.octi.module.core.meta"
        fixture.producer shouldBe "d4rken-org/octi-web"
        fixture.vectors.map { it.name } shouldBe listOf("full", "minimal", "unicode-label")
    }

    @Test
    fun `web meta 'full' vector decodes to expected MetaInfo`() {
        val info = decode(vector("full"))
        info.deviceLabel shouldBe "Test Browser"
        info.deviceId.id shouldBe "11111111-2222-3333-4444-555555555555"
        info.octiVersionName shouldBe "0.0.0-test"
        info.octiGitSha shouldBe "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        info.deviceManufacturer shouldBe "Mozilla"
        info.deviceName shouldBe "Firefox 134.0 on Linux"
        info.deviceType shouldBe MetaInfo.DeviceType.BROWSER
        // Web hardcodes deviceBootedAt to null (no SystemClock.elapsedRealtime equivalent).
        info.deviceBootedAt shouldBe null
        // Web never sets the Android-specific fields — verifies serialize-strips-nulls + decode-default-null.
        info.androidVersionName shouldBe null
        info.androidApiLevel shouldBe null
        info.androidSecurityPatch shouldBe null
        info.osType shouldBe "linux"
        info.osVersionName shouldBe "6.8.0"
    }

    @Test
    fun `web meta 'minimal' vector decodes with absent optionals as null`() {
        val info = decode(vector("minimal"))
        // The wire-side serializer strips nulls; the consumer's strict decoder must
        // tolerate absent optional fields (explicitNulls=false / default=null).
        info.deviceLabel shouldBe null
        info.deviceId.id shouldBe "11111111-2222-3333-4444-555555555555"
        info.octiVersionName shouldBe "0.0.0-test"
        info.octiGitSha shouldBe "dev"
        info.deviceManufacturer shouldBe "Mozilla"
        info.deviceName shouldBe "Browser"
        info.deviceType shouldBe MetaInfo.DeviceType.BROWSER
        info.deviceBootedAt shouldBe null
        info.osType shouldBe null
        info.osVersionName shouldBe null
    }

    @Test
    fun `web meta 'unicode-label' vector decodes every field including non-ASCII deviceLabel`() {
        val info = decode(vector("unicode-label"))
        // Mixed UTF-8: Japanese katakana + emoji + Arabic. Pin the round-trip across
        // the JSON-string escape boundary.
        info.deviceLabel shouldBe "ブラウザ 👋 العربية"
        info.deviceId.id shouldBe "11111111-2222-3333-4444-555555555555"
        info.octiVersionName shouldBe "0.0.0-test"
        info.octiGitSha shouldBe "dev"
        info.deviceManufacturer shouldBe "Mozilla"
        info.deviceName shouldBe "Firefox"
        info.deviceType shouldBe MetaInfo.DeviceType.BROWSER
        info.deviceBootedAt shouldBe null
        info.osType shouldBe "linux"
        info.osVersionName shouldBe null
    }

    private fun loadFixture(): PublishedModuleFixture {
        val bytes = Files.readAllBytes(cacheDir.resolve("octi-web-meta.json"))
        return InteropFixtures.json.decodeFromString(
            PublishedModuleFixture.serializer(),
            bytes.decodeToString(),
        )
    }

    private fun vector(name: String): PublishedVector {
        val fixture = loadFixture()
        val v = fixture.vectors.firstOrNull { it.name == name }
            ?: error("vector '$name' missing in ${fixture.module}")
        // Re-verify the per-vector sha256+byteLength against payloadJson. The producer's
        // self-check pins these at generate time; we re-check on read so a hand-edit to one
        // of these files (without regenerating the manifest) fails here, not as a green test.
        InteropFixtures.verifyVectorIntegrity(v)
        return v
    }

    private fun decode(v: PublishedVector): MetaInfo {
        return decoder.decodeFromString(MetaInfo.serializer(), v.payloadJson)
    }
}
