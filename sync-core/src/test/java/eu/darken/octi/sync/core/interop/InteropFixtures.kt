package eu.darken.octi.sync.core.interop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Cross-repo wire-format fixtures. App-main is the canonical source; octi-web and
 * octi-desktop consume these via a per-source pinned commit SHA, never by copying.
 *
 * **Two halves:**
 *  - [InteropFixtureGeneratorTest] writes the committed JSON (opt-in, gated by
 *    `-DgenerateInteropFixtures=true`).
 *  - [InteropFixtureVerifyTest] reads it back and asserts every committed
 *    ciphertext decrypts to the recorded plaintext under the committed keyset.
 *    Runs unconditionally on every `./gradlew :sync-core:testDebugUnitTest`.
 *
 * **Why decrypt-and-compare instead of byte-equality.** Tink's AES-GCM-SIV uses
 * a random nonce per encrypt — re-running the generator with the same keyset
 * produces different bytes. So byte-stable regeneration is impossible. The
 * security property we depend on is "encrypt → decrypt round-trips under
 * keyset + AD", which is exactly what verify asserts.
 *
 * **Manifest sha256.** The verify gate also recomputes each fixture file's
 * sha256 against [FixtureManifest] so downstream consumers (octi-web, octi-desktop)
 * can verify-on-fetch using a single small file.
 */
internal object InteropFixtures {

    const val SCHEMA_VERSION = 1
    const val MANIFEST_FILE = "manifest.json"
    const val TINK_FILE = "tink-vectors.json"
    const val STREAMING_FILE = "streaming-vectors.json"

    /** Classpath-relative root of the committed fixtures (src/test/resources/interop). */
    const val RESOURCE_ROOT = "interop"

    /**
     * Canonical JSON encoder used for both generation and (debug) re-encoding.
     *
     * `explicitNulls = false` so optional fields (`plaintextBase64` / `plaintextPattern`)
     * serialize as absent, not `null` — cleaner consumer parsing in TS/JVM/other.
     * `encodeDefaults = true` so required fields always appear even when they equal
     * their default (e.g. an empty `aad` on legacy SIV vectors).
     */
    val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
        encodeDefaults = true
    }

    /** First byte of every Tink-AEAD-prefixed ciphertext. Pinned so wire drift fails loudly. */
    const val TINK_PREFIX_BYTE: Byte = 0x01

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (b in digest) append("%02x".format(b))
        }
    }

    /**
     * Reconstruct plaintext bytes for a [StreamingPlaintextPattern]. Kept in this shared
     * file so the verify test and any consumer port can reuse the exact algorithm.
     */
    fun materializePattern(pattern: StreamingPlaintextPattern): ByteArray = when (pattern.kind) {
        "sequential" -> ByteArray(pattern.size) { i -> (i and 0xFF).toByte() }
        else -> error("unknown plaintextPattern.kind=${pattern.kind}")
    }
}

@Serializable
internal data class FixtureManifest(
    val schemaVersion: Int,
    val source: String,
    val generator: String,
    val files: Map<String, FileEntry>,
)

@Serializable
internal data class FileEntry(val sha256: String)

@Serializable
internal data class TinkVectorsFixture(
    val schemaVersion: Int,
    val note: String,
    val gcmsiv: KeysetBlock,
    val siv: KeysetBlock,
)

@Serializable
internal data class KeysetBlock(
    val keysetType: String,
    val keysetBase64: String,
    val vectors: List<PayloadVector>,
)

@Serializable
internal data class PayloadVector(
    val name: String,
    val plaintextBase64: String,
    /**
     * Associated data passed to the AEAD. Empty for legacy SIV (which ignores AAD by
     * construction) — that's contract, not omission.
     */
    val aad: String,
    val ciphertextBase64: String,
)

@Serializable
internal data class StreamingVectorsFixture(
    val schemaVersion: Int,
    val note: String,
    val keysetType: String,
    val keysetBase64: String,
    val vectors: List<StreamingVector>,
)

@Serializable
internal data class StreamingVector(
    val name: String,
    val aad: String,
    /**
     * Inline plaintext bytes (base64). Set for small vectors where the bytes
     * themselves are part of the contract. Mutually exclusive with [plaintextPattern].
     */
    val plaintextBase64: String? = null,
    /**
     * Deterministic plaintext reference for large vectors — avoids committing
     * megabytes of base64 to git for plaintext that's algorithmically generated.
     * Mutually exclusive with [plaintextBase64].
     */
    val plaintextPattern: StreamingPlaintextPattern? = null,
    val plaintextSize: Int,
    val ciphertextBase64: String,
    val ciphertextSize: Int,
)

@Serializable
internal data class StreamingPlaintextPattern(
    /** "sequential": byte i = (i and 0xFF).toByte(). Trivially portable to TS/JVM/etc. */
    val kind: String,
    val size: Int,
)
