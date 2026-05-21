package eu.darken.octi.modules.files.interop

import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.sync.core.RemoteBlobRef
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
 * Verify app-main's FileShareInfo decoder can consume what octi-web publishes.
 *
 * Pin: SharedFile.size as Long (the 'files-large' vector trips any Int-typed field),
 * connectorRefs map keying, Instant ISO-8601 parsing, and the optional `deleteRequests`
 * branch.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebFilesInteropTest {

    private lateinit var cacheDir: Path

    private val decoder: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val prodConnector =
        "kserver-prod.kserver.octi.darken.eu-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val betaConnector =
        "kserver-beta.kserver.octi.darken.eu-ffffffff-1111-2222-3333-444444444444"

    @BeforeAll
    fun setUp() {
        cacheDir = InteropFixtureSync.ensureSynced("d4rken-org/octi-web")
    }

    @Test
    fun `web files fixture schema sanity`() {
        val fixture = loadFixture()
        fixture.schemaVersion shouldBe InteropFixtures.FIXTURE_SCHEMA_VERSION
        fixture.module shouldBe "eu.darken.octi.module.core.files"
        fixture.producer shouldBe "d4rken-org/octi-web"
        fixture.vectors.map { it.name } shouldBe listOf(
            "empty",
            "single-file",
            "with-multiple-files",
            "with-delete-requests",
            "multi-connector",
            "files-large",
        )
    }

    @Test
    fun `web files 'empty' vector decodes to empty lists`() {
        val info = decode(vector("empty"))
        info.files shouldBe emptyList()
        info.deleteRequests shouldBe emptyList()
    }

    @Test
    fun `web files 'single-file' vector decodes one SharedFile`() {
        val info = decode(vector("single-file"))
        info.files.size shouldBe 1
        info.deleteRequests shouldBe emptyList()

        val f = info.files[0]
        f.name shouldBe "notes.txt"
        f.mimeType shouldBe "text/plain"
        f.size shouldBe 1234L
        f.blobKey shouldBe "sha256:0000000000000000000000000000000000000000000000000000000000000001"
        f.checksum shouldBe "0000000000000000000000000000000000000000000000000000000000000001"
        f.sharedAt shouldBe Instant.parse("2026-05-01T12:00:00Z")
        f.expiresAt shouldBe Instant.parse("2026-05-31T12:00:00Z")
        f.availableOn shouldBe setOf(prodConnector)
        f.connectorRefs shouldBe mapOf(prodConnector to RemoteBlobRef("blob-id-aaaa"))
    }

    @Test
    fun `web files 'with-multiple-files' vector decodes both entries field-by-field`() {
        val info = decode(vector("with-multiple-files"))
        info.files.size shouldBe 2
        info.deleteRequests shouldBe emptyList()

        val alpha = info.files[0]
        alpha.name shouldBe "alpha.bin"
        alpha.mimeType shouldBe "application/octet-stream"
        alpha.size shouldBe 256L
        alpha.blobKey shouldBe "sha256:0000000000000000000000000000000000000000000000000000000000000002"
        alpha.checksum shouldBe "0000000000000000000000000000000000000000000000000000000000000002"
        alpha.sharedAt shouldBe Instant.parse("2026-05-01T12:00:00Z")
        alpha.expiresAt shouldBe Instant.parse("2026-05-31T12:00:00Z")
        alpha.availableOn shouldBe setOf(prodConnector)
        alpha.connectorRefs shouldBe mapOf(prodConnector to RemoteBlobRef("blob-id-bbbb"))

        val beta = info.files[1]
        beta.name shouldBe "beta.pdf"
        beta.mimeType shouldBe "application/pdf"
        beta.size shouldBe 4096L
        beta.blobKey shouldBe "sha256:0000000000000000000000000000000000000000000000000000000000000003"
        beta.checksum shouldBe "0000000000000000000000000000000000000000000000000000000000000003"
        beta.sharedAt shouldBe Instant.parse("2026-05-01T13:00:00Z")
        beta.expiresAt shouldBe Instant.parse("2026-05-31T13:00:00Z")
        beta.availableOn shouldBe setOf(prodConnector)
        beta.connectorRefs shouldBe mapOf(prodConnector to RemoteBlobRef("blob-id-cccc"))
    }

    @Test
    fun `web files 'with-delete-requests' vector decodes the deleteRequests branch field-by-field`() {
        val info = decode(vector("with-delete-requests"))
        info.files.size shouldBe 1
        info.deleteRequests.size shouldBe 1

        val f = info.files[0]
        f.name shouldBe "shared.txt"
        f.mimeType shouldBe "text/plain"
        f.size shouldBe 100L
        f.blobKey shouldBe "sha256:0000000000000000000000000000000000000000000000000000000000000004"
        f.checksum shouldBe "0000000000000000000000000000000000000000000000000000000000000004"
        f.sharedAt shouldBe Instant.parse("2026-05-01T12:00:00Z")
        f.expiresAt shouldBe Instant.parse("2026-05-31T12:00:00Z")
        f.availableOn shouldBe setOf(prodConnector)
        f.connectorRefs shouldBe mapOf(prodConnector to RemoteBlobRef("blob-id-dddd"))

        val req = info.deleteRequests[0]
        req.targetDeviceId shouldBe "99999999-8888-7777-6666-555555555555"
        req.blobKey shouldBe "sha256:0000000000000000000000000000000000000000000000000000000000000005"
        req.requestedAt shouldBe Instant.parse("2026-05-10T00:00:00Z")
        req.retainUntil shouldBe Instant.parse("2026-05-17T00:00:00Z")
    }

    @Test
    fun `web files 'multi-connector' vector decodes both connectorRefs entries field-by-field`() {
        // Pins the multi-connector wire shape — the dashboard's connector-merge code does
        // a per-connector lookup against this map. A single-connector vector wouldn't
        // catch a future bug hardcoding one key.
        val info = decode(vector("multi-connector"))
        info.files.size shouldBe 1
        info.deleteRequests shouldBe emptyList()

        val f = info.files[0]
        f.name shouldBe "shared-across.bin"
        f.mimeType shouldBe "application/octet-stream"
        f.size shouldBe 512L
        f.blobKey shouldBe "sha256:0000000000000000000000000000000000000000000000000000000000000007"
        f.checksum shouldBe "0000000000000000000000000000000000000000000000000000000000000007"
        f.sharedAt shouldBe Instant.parse("2026-05-01T12:00:00Z")
        f.expiresAt shouldBe Instant.parse("2026-05-31T12:00:00Z")
        f.availableOn shouldBe setOf(prodConnector, betaConnector)
        f.connectorRefs shouldBe mapOf(
            prodConnector to RemoteBlobRef("blob-id-prod-7777"),
            betaConnector to RemoteBlobRef("blob-id-beta-7777"),
        )
    }

    @Test
    fun `web files 'files-large' vector decodes size larger than Int MAX_VALUE`() {
        // Codex finding from B1: pin Long handling on the JVM consumer. If SharedFile.size
        // were typed `Int`, this 8e9 vector would fail decode in the right place.
        val info = decode(vector("files-large"))
        info.files.size shouldBe 1
        info.deleteRequests shouldBe emptyList()

        val f = info.files[0]
        f.name shouldBe "big.iso"
        f.mimeType shouldBe "application/octet-stream"
        f.size shouldBe 8_000_000_000L
        // Sanity: > Int.MAX_VALUE so we know we exercised the Long branch.
        check(f.size > Int.MAX_VALUE.toLong()) { "vector did not exceed Int.MAX_VALUE" }
        f.blobKey shouldBe "sha256:0000000000000000000000000000000000000000000000000000000000000006"
        f.checksum shouldBe "0000000000000000000000000000000000000000000000000000000000000006"
        f.sharedAt shouldBe Instant.parse("2026-05-01T12:00:00Z")
        f.expiresAt shouldBe Instant.parse("2026-05-31T12:00:00Z")
        f.availableOn shouldBe setOf(prodConnector)
        f.connectorRefs shouldBe mapOf(prodConnector to RemoteBlobRef("blob-id-eeee"))
    }

    private fun loadFixture(): PublishedModuleFixture {
        val bytes = Files.readAllBytes(cacheDir.resolve("octi-web-files.json"))
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

    private fun decode(v: PublishedVector): FileShareInfo {
        return decoder.decodeFromString(FileShareInfo.serializer(), v.payloadJson)
    }
}
