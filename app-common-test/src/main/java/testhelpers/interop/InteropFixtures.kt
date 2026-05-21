package testhelpers.interop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Shared schemas + helpers for the cross-repo wire-format fixture system.
 *
 * Lives in app-common-test/src/main so each modules-X/src/test can call into it via the
 * existing `testImplementation(project(":app-common-test"))` wiring. Consumer tests
 * cannot live in sync-core — modules-{meta,clipboard,files} depend on sync-core, so the
 * shared sync code has to sit one layer above.
 *
 * **Trust boundary.** [InteropFixtureSync] fetches manifest + fixture files from upstream
 * over HTTPS, then verifies each file's sha256 against the manifest. The manifest's own
 * sha256 is pinned in `fixture-lock.json` at repo root, so a single committed file (the
 * lockfile) anchors the whole tree. [SyncRefResolver] mediates the optional CI override
 * (`INTEROP_FIXTURE_OVERRIDES`) that lets the upstream-gating workflow point at a PR's
 * head SHA without rewriting the lockfile.
 *
 * Counterparts on the other repos (kept in lockstep):
 *  - octi-web/tools/sync-fixtures.ts + octi-web/src/__interop__/sync-ref-resolver.ts
 *  - octi-desktop/src/test/kotlin/.../interop/InteropFixtureSync.kt + SyncRefResolver.kt
 */
object InteropFixtures {

    /** Lockfile schema. v2 introduces multi-source. App-main starts on v2; web + desktop migrate later. */
    const val LOCK_SCHEMA_VERSION = 2

    /** Per-source manifest schema. Pinned at v1 across producers; bumps require coordinated rollout. */
    const val FIXTURE_SCHEMA_VERSION = 1

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (b in digest) append("%02x".format(b))
        }
    }

    /**
     * Each [PublishedVector] carries its own per-vector `sha256` + `byteLength`. The producer's
     * self-check enforces them at generate time; we re-verify on the consumer side so a
     * hand-edit to one of these JSON files (without bumping the producer's manifest) trips here
     * rather than slipping past with a misleading green decode result.
     */
    fun verifyVectorIntegrity(vector: PublishedVector) {
        val bytes = vector.payloadJson.toByteArray(Charsets.UTF_8)
        check(vector.byteLength == bytes.size) {
            "vector '${vector.name}': declared byteLength ${vector.byteLength} disagrees with payloadJson bytes ${bytes.size}"
        }
        val actualSha = sha256Hex(bytes)
        check(vector.sha256 == actualSha) {
            "vector '${vector.name}': declared sha256 ${vector.sha256} disagrees with payloadJson bytes (${actualSha})"
        }
    }
}

/**
 * Multi-source lockfile entry. App-main consumes both `d4rken-org/octi-web` and
 * `d4rken-org/octi-desktop` today; new sources land here by adding both the lockfile entry
 * and the matching path in [SyncRefResolver.SOURCE_PATHS].
 */
@Serializable
data class FixtureLock(
    val schemaVersion: Int,
    val sources: Map<String, LockedSource>,
)

@Serializable
data class LockedSource(
    val ref: String,
    @SerialName("manifest_sha256") val manifestSha256: String,
)

/**
 * Per-source manifest as committed by each producer. App-main reads — never writes — these.
 * `generator` and `byteLength` are optional on the wire because app-main's own historical
 * manifest in sync-core (Phase A) omits them; octi-web's includes both.
 */
@Serializable
data class FixtureManifest(
    val schemaVersion: Int,
    val source: String,
    val generator: String? = null,
    val files: Map<String, FileEntry>,
)

@Serializable
data class FileEntry(
    val sha256: String,
    val byteLength: Int? = null,
)

/**
 * Per-module fixture file shape. One file per module under each producer's published dir.
 * `payloadJson` is the literal byte output of the producer's serializer for that vector;
 * consumer tests parse it through their production decoder and assert field-level shape.
 */
@Serializable
data class PublishedModuleFixture(
    val schemaVersion: Int,
    val module: String,
    val producer: String,
    val note: String,
    val vectors: List<PublishedVector>,
)

@Serializable
data class PublishedVector(
    val name: String,
    val payloadJson: String,
    val sha256: String,
    val byteLength: Int,
)
