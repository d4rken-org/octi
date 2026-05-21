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
import kotlin.time.Instant

/**
 * Verify app-main's MetaInfo decoder can consume what octi-desktop publishes.
 *
 * Loads `octi-desktop-meta.json` from `.cache/interop-fixtures/d4rken-org/octi-desktop/<ref>/`,
 * parses each `payloadJson` through the production [MetaInfo] decoder, and asserts field
 * values match the canonical inputs declared in octi-desktop's `InteropFixtureGenerator.kt`.
 *
 * Sister test: [WebMetaInteropTest] for octi-web's same module. Same shape, different source +
 * vector contents (`deviceType=DESKTOP` instead of `BROWSER`, `deviceBootedAt` set instead of
 * always null, etc.).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopMetaInteropTest {

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
    fun `desktop meta fixture schema sanity`() {
        val fixture = loadFixture()
        fixture.schemaVersion shouldBe InteropFixtures.FIXTURE_SCHEMA_VERSION
        fixture.module shouldBe "eu.darken.octi.module.core.meta"
        fixture.producer shouldBe "d4rken-org/octi-desktop"
        fixture.vectors.map { it.name } shouldBe listOf("full", "minimal", "unicode-label")
    }

    @Test
    fun `desktop meta 'full' vector decodes to expected MetaInfo`() {
        val info = decode(vector("full"))
        info.deviceLabel shouldBe "Test Desktop"
        info.deviceId.id shouldBe "22222222-3333-4444-5555-666666666666"
        info.octiVersionName shouldBe "0.0.0-test"
        info.octiGitSha shouldBe "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        info.deviceManufacturer shouldBe "Eclipse Adoptium"
        info.deviceName shouldBe "octi-test-host"
        info.deviceType shouldBe MetaInfo.DeviceType.DESKTOP
        // Desktop's MetaWriter calls ProcessHandle.startInstant() — emits a real Instant, never
        // null (unlike web). Consumers must accept this.
        info.deviceBootedAt shouldBe Instant.parse("2026-05-01T10:00:00Z")
        info.androidVersionName shouldBe null
        info.androidApiLevel shouldBe null
        info.androidSecurityPatch shouldBe null
        info.osType shouldBe "Linux"
        info.osVersionName shouldBe "6.8.0"
    }

    @Test
    fun `desktop meta 'minimal' vector decodes with absent optionals as null`() {
        // Schema-shape vector. The pinned `minimal` payload omits `deviceBootedAt` entirely
        // (explicitNulls=false strips the field on the producer side); the decoder must
        // re-materialize the absent field as null. Real desktop writer always emits a
        // non-null Instant — this vector is forward-compat for that absence rather than a
        // production-output snapshot.
        val v = vector("minimal")
        // Pin wire-shape absence too, not just the decoded null. If the producer ever
        // started emitting `"deviceBootedAt": null` explicitly, this would catch it.
        (v.payloadJson.contains("deviceBootedAt")) shouldBe false

        val info = decode(v)
        info.deviceLabel shouldBe null
        info.deviceId.id shouldBe "22222222-3333-4444-5555-666666666666"
        info.octiVersionName shouldBe "0.0.0-test"
        info.octiGitSha shouldBe "desktop-dev"
        info.deviceManufacturer shouldBe "Eclipse Adoptium"
        info.deviceName shouldBe "octi-desktop"
        info.deviceType shouldBe MetaInfo.DeviceType.DESKTOP
        info.deviceBootedAt shouldBe null
        info.osType shouldBe null
        info.osVersionName shouldBe null
    }

    @Test
    fun `desktop meta 'unicode-label' vector decodes every field including non-ASCII deviceLabel`() {
        val info = decode(vector("unicode-label"))
        // Mixed UTF-8: Japanese katakana + emoji + Arabic.
        info.deviceLabel shouldBe "デスクトップ 🖥 العربية"
        info.deviceId.id shouldBe "22222222-3333-4444-5555-666666666666"
        info.octiVersionName shouldBe "0.0.0-test"
        info.octiGitSha shouldBe "desktop-dev"
        info.deviceManufacturer shouldBe "Eclipse Adoptium"
        info.deviceName shouldBe "octi-desktop"
        info.deviceType shouldBe MetaInfo.DeviceType.DESKTOP
        info.deviceBootedAt shouldBe Instant.parse("2026-05-01T10:00:00Z")
        info.osType shouldBe "Linux"
        info.osVersionName shouldBe null
    }

    private fun loadFixture(): PublishedModuleFixture {
        val bytes = Files.readAllBytes(cacheDir.resolve("octi-desktop-meta.json"))
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

    private fun decode(v: PublishedVector): MetaInfo {
        return decoder.decodeFromString(MetaInfo.serializer(), v.payloadJson)
    }
}
