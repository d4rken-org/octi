package eu.darken.octi.sync.core.interop

import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.sync.core.blob.StreamingPayloadCipher
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Always-on gate that pins what app-main has committed under
 * `sync-core/src/test/resources/interop/` as the canonical cross-repo wire fixture.
 *
 * Runs on every `./gradlew :sync-core:testDebugUnitTest`. Fails if:
 *   - The manifest's sha256 disagrees with the actual fixture bytes (someone
 *     hand-edited a fixture without updating the manifest, or vice versa).
 *   - Any committed ciphertext fails to decrypt under the committed keyset + AAD.
 *   - A vector's decoded sizes drift from the declared `plaintextSize` / `ciphertextSize`.
 *   - The Tink AEAD wire prefix is wrong.
 *   - Tampered ciphertext / wrong AAD / truncated bytes don't reject as expected.
 *
 * This is the contract octi-web and octi-desktop pin against.
 */
class InteropFixtureVerifyTest : BaseTest() {

    private val resourceLoader: ClassLoader = checkNotNull(InteropFixtureVerifyTest::class.java.classLoader) {
        "no classloader available for ${InteropFixtureVerifyTest::class.java}"
    }

    private fun loadResource(name: String): ByteArray {
        val full = "${InteropFixtures.RESOURCE_ROOT}/$name"
        val stream = resourceLoader.getResourceAsStream(full)
            ?: error("missing committed fixture: $full (run `./gradlew :sync-core:generateInteropFixtures`)")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `manifest sha256 matches every committed fixture file`() {
        val manifestBytes = loadResource(InteropFixtures.MANIFEST_FILE)
        val manifest = InteropFixtures.json.decodeFromString(
            FixtureManifest.serializer(),
            manifestBytes.toString(Charsets.UTF_8),
        )
        manifest.schemaVersion shouldBe InteropFixtures.SCHEMA_VERSION
        manifest.files.keys shouldBe setOf(InteropFixtures.TINK_FILE, InteropFixtures.STREAMING_FILE)

        for ((name, entry) in manifest.files) {
            val actual = InteropFixtures.sha256Hex(loadResource(name))
            withClue("manifest sha256 mismatch for $name", entry.sha256, actual) {
                actual shouldBe entry.sha256
            }
        }
    }

    @Test
    fun `tink-vectors schema pins expected shape`() {
        val fixture = loadTinkFixture()
        fixture.gcmsiv.keysetType shouldBe "AES256_GCM_SIV"
        fixture.siv.keysetType shouldBe "AES256_SIV"
        // Pin vector name sets so a contributor can't silently drop or rename one without
        // regenerating consumer expectations. New vectors require a deliberate edit here.
        fixture.gcmsiv.vectors.assertUniqueNames() shouldBe
            setOf("empty", "hello-world", "with-special-chars", "approx-10kb")
        fixture.siv.vectors.assertUniqueNames() shouldBe
            setOf("empty", "hello-world", "with-special-chars", "approx-10kb")
        // SIV is contractually AAD-less; a non-empty aad here would be misleading data.
        for (v in fixture.siv.vectors) v.aad shouldBe ""
    }

    @Test
    fun `tink-vectors gcmsiv decrypts every committed ciphertext`() {
        val fixture = loadTinkFixture()
        val crypti = cryptiFor(fixture.gcmsiv)
        for (v in fixture.gcmsiv.vectors) {
            val plaintext = decodeBase64Required(v.plaintextBase64)
            val aad = v.aad.toByteArray(Charsets.UTF_8)
            val ciphertext = decodeBase64Required(v.ciphertextBase64)
            ciphertext.assertTinkAeadPrefix("gcmsiv:${v.name}")

            val decrypted = crypti.decrypt(ciphertext, aad).fromGzip()
            withClue("gcmsiv decrypt mismatch on vector '${v.name}'", plaintext, decrypted) {
                decrypted shouldBe plaintext
            }
        }
    }

    @Test
    fun `tink-vectors siv decrypts every committed ciphertext`() {
        val fixture = loadTinkFixture()
        val crypti = cryptiFor(fixture.siv)
        for (v in fixture.siv.vectors) {
            val plaintext = decodeBase64Required(v.plaintextBase64)
            val ciphertext = decodeBase64Required(v.ciphertextBase64)
            ciphertext.assertTinkAeadPrefix("siv:${v.name}")

            // Legacy SIV ignores AD by construction.
            val decrypted = crypti.decrypt(ciphertext).fromGzip()
            withClue("siv decrypt mismatch on vector '${v.name}'", plaintext, decrypted) {
                decrypted shouldBe plaintext
            }
        }
    }

    @Test
    fun `gcmsiv decrypt rejects wrong associated data`() {
        val fixture = loadTinkFixture()
        val crypti = cryptiFor(fixture.gcmsiv)
        val v = fixture.gcmsiv.vectors.first { it.plaintextBase64.isNotEmpty() }
        val ct = decodeBase64Required(v.ciphertextBase64)
        val wrongAad = "${v.aad}-tampered".toByteArray(Charsets.UTF_8)
        shouldThrowAny { crypti.decrypt(ct, wrongAad) }
    }

    @Test
    fun `gcmsiv decrypt rejects tampered ciphertext`() {
        val fixture = loadTinkFixture()
        val crypti = cryptiFor(fixture.gcmsiv)
        val v = fixture.gcmsiv.vectors.first { it.plaintextBase64.isNotEmpty() }
        val ct = decodeBase64Required(v.ciphertextBase64).toByteArray()
        // Flip a bit deep inside the body (past Tink's 5-byte key prefix) so we exercise
        // the AEAD tag verification path rather than the prefix-mismatch path.
        ct[ct.size - 1] = (ct[ct.size - 1].toInt() xor 0x01).toByte()
        val aad = v.aad.toByteArray(Charsets.UTF_8)
        shouldThrowAny { crypti.decrypt(ct.toByteString(), aad) }
    }

    @Test
    fun `siv decrypt rejects tampered ciphertext`() {
        val fixture = loadTinkFixture()
        val crypti = cryptiFor(fixture.siv)
        val v = fixture.siv.vectors.first { it.plaintextBase64.isNotEmpty() }
        val ct = decodeBase64Required(v.ciphertextBase64).toByteArray()
        ct[ct.size - 1] = (ct[ct.size - 1].toInt() xor 0x01).toByte()
        // Legacy SIV ignores AAD by construction — passing AAD here doesn't change the outcome.
        shouldThrowAny { crypti.decrypt(ct.toByteString()) }
    }

    @Test
    fun `siv decrypt rejects truncated ciphertext`() {
        val fixture = loadTinkFixture()
        val crypti = cryptiFor(fixture.siv)
        val v = fixture.siv.vectors.first { it.plaintextBase64.isNotEmpty() }
        val ct = decodeBase64Required(v.ciphertextBase64).toByteArray()
        val truncated = ct.copyOf(maxOf(6, ct.size - 8))
        shouldThrowAny { crypti.decrypt(truncated.toByteString()) }
    }

    @Test
    fun `gcmsiv decrypt rejects truncated ciphertext`() {
        val fixture = loadTinkFixture()
        val crypti = cryptiFor(fixture.gcmsiv)
        val v = fixture.gcmsiv.vectors.first { it.plaintextBase64.isNotEmpty() }
        val ct = decodeBase64Required(v.ciphertextBase64).toByteArray()
        // Lop off the trailing AEAD tag bytes — must fail rather than return a partial.
        val truncated = ct.copyOf(maxOf(6, ct.size - 8))
        val aad = v.aad.toByteArray(Charsets.UTF_8)
        shouldThrowAny { crypti.decrypt(truncated.toByteString(), aad) }
    }

    @Test
    fun `streaming-vectors schema pins expected shape`() {
        val fixture = loadStreamingFixture()
        fixture.keysetType shouldBe "AES256_GCM_SIV"
        fixture.vectors.map { it.name }.assertNoDuplicates("streaming") shouldBe
            listOf("empty", "short", "two-segments")
        // The pattern's size must match the declared plaintextSize (else materialize
        // and ciphertext interpretation disagree).
        for (v in fixture.vectors) {
            val pattern = v.plaintextPattern
            if (pattern != null) {
                withClue("plaintextPattern.size mismatch on '${v.name}'", pattern.size, v.plaintextSize) {
                    pattern.size shouldBe v.plaintextSize
                }
            }
        }
    }

    @Test
    fun `streaming-vectors decrypts every committed ciphertext`() {
        val fixture = loadStreamingFixture()
        val keyset = PayloadEncryption.KeySet(
            type = fixture.keysetType,
            key = decodeBase64Required(fixture.keysetBase64),
        )
        val cipher = StreamingPayloadCipher(keyset)
        for (v in fixture.vectors) {
            val plaintext = expectedPlaintext(v)
            val ciphertext = decodeBase64Required(v.ciphertextBase64).toByteArray()
            ciphertext.size shouldBe v.ciphertextSize

            val out = Buffer()
            cipher.decrypt(Buffer().write(ciphertext), out, v.aad.toByteArray(Charsets.UTF_8))
            val decrypted = out.readByteArray()
            decrypted.size shouldBe v.plaintextSize
            plaintext.size shouldBe v.plaintextSize
            withClue("streaming decrypt mismatch on vector '${v.name}'", plaintext.toByteString(), decrypted.toByteString()) {
                decrypted.toByteString() shouldBe plaintext.toByteString()
            }
        }
    }

    @Test
    fun `streaming decrypt rejects wrong aad`() {
        val fixture = loadStreamingFixture()
        val keyset = PayloadEncryption.KeySet(
            type = fixture.keysetType,
            key = decodeBase64Required(fixture.keysetBase64),
        )
        val cipher = StreamingPayloadCipher(keyset)
        val v = fixture.vectors.first { it.plaintextSize > 0 }
        val ct = decodeBase64Required(v.ciphertextBase64).toByteArray()
        val wrongAad = "${v.aad}-tampered".toByteArray(Charsets.UTF_8)
        shouldThrowAny {
            cipher.decrypt(Buffer().write(ct), Buffer(), wrongAad)
        }
    }

    @Test
    fun `streaming decrypt rejects truncated ciphertext`() {
        val fixture = loadStreamingFixture()
        val keyset = PayloadEncryption.KeySet(
            type = fixture.keysetType,
            key = decodeBase64Required(fixture.keysetBase64),
        )
        val cipher = StreamingPayloadCipher(keyset)
        val v = fixture.vectors.first { it.plaintextSize > 0 }
        val ct = decodeBase64Required(v.ciphertextBase64).toByteArray()
        val truncated = ct.copyOf(maxOf(1, ct.size - 4))
        shouldThrowAny {
            cipher.decrypt(Buffer().write(truncated), Buffer(), v.aad.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun `streaming decrypt rejects corrupt header byte`() {
        val fixture = loadStreamingFixture()
        val keyset = PayloadEncryption.KeySet(
            type = fixture.keysetType,
            key = decodeBase64Required(fixture.keysetBase64),
        )
        val cipher = StreamingPayloadCipher(keyset)
        // Use the multi-segment vector so the corrupt-header path is exercised, not
        // just an "empty input" reject.
        val v = fixture.vectors.first { it.plaintextSize > 1024 }
        val ct = decodeBase64Required(v.ciphertextBase64).toByteArray()
        ct[0] = (ct[0].toInt() xor 0xFF).toByte()
        shouldThrowAny {
            cipher.decrypt(Buffer().write(ct), Buffer(), v.aad.toByteArray(Charsets.UTF_8))
        }
    }

    private fun expectedPlaintext(v: StreamingVector): ByteArray {
        val inline = v.plaintextBase64
        val pattern = v.plaintextPattern
        check((inline != null) xor (pattern != null)) {
            "vector '${v.name}' must declare exactly one of plaintextBase64 / plaintextPattern"
        }
        return when {
            inline != null -> decodeBase64Required(inline).toByteArray()
            pattern != null -> InteropFixtures.materializePattern(pattern)
            else -> error("unreachable")
        }
    }

    private fun loadTinkFixture(): TinkVectorsFixture {
        val raw = loadResource(InteropFixtures.TINK_FILE)
        val fixture = InteropFixtures.json.decodeFromString(
            TinkVectorsFixture.serializer(),
            raw.toString(Charsets.UTF_8),
        )
        fixture.schemaVersion shouldBe InteropFixtures.SCHEMA_VERSION
        return fixture
    }

    private fun loadStreamingFixture(): StreamingVectorsFixture {
        val raw = loadResource(InteropFixtures.STREAMING_FILE)
        val fixture = InteropFixtures.json.decodeFromString(
            StreamingVectorsFixture.serializer(),
            raw.toString(Charsets.UTF_8),
        )
        fixture.schemaVersion shouldBe InteropFixtures.SCHEMA_VERSION
        return fixture
    }

    private fun cryptiFor(block: KeysetBlock): PayloadEncryption = PayloadEncryption(
        keySet = PayloadEncryption.KeySet(
            type = block.keysetType,
            key = decodeBase64Required(block.keysetBase64),
        ),
    )

    private fun ByteString.assertTinkAeadPrefix(label: String) {
        // Tink AEAD wire layout: 1-byte prefix (0x01) + 4-byte key id + nonce + ciphertext + tag.
        // The prefix byte rotates on a wire-incompatible Tink upgrade, so pinning it
        // protects against silent shifts. Empty plaintexts still produce a non-empty
        // ciphertext (prefix + key id + nonce + tag), so size 5 is the strict minimum.
        check(size >= 5) { "ciphertext for $label too short: $size bytes" }
        val first = this[0]
        withClue("Tink AEAD prefix byte for $label", InteropFixtures.TINK_PREFIX_BYTE, first) {
            first shouldBe InteropFixtures.TINK_PREFIX_BYTE
        }
    }

    private fun List<PayloadVector>.assertUniqueNames(): Set<String> {
        val names = map { it.name }
        check(names.size == names.toSet().size) { "duplicate vector names: $names" }
        return names.toSet()
    }

    private fun List<String>.assertNoDuplicates(label: String): List<String> {
        check(size == toSet().size) { "duplicate $label vector names: $this" }
        return this
    }

    private fun decodeBase64Required(s: String): ByteString =
        s.decodeBase64() ?: error("invalid base64 in committed fixture")

    private fun <T> withClue(label: String, expected: T, actual: T, block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("$label\nexpected: $expected\nactual:   $actual", e)
        }
    }
}
