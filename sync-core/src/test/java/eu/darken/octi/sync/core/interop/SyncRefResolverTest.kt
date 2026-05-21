package eu.darken.octi.sync.core.interop

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.interop.FixtureLock
import testhelpers.interop.InteropFixtures
import testhelpers.interop.LockedSource
import testhelpers.interop.SyncRefResolver

/**
 * Pin the validation surface of [SyncRefResolver]. The multi-source resolver introduces
 * symmetric per-source checks plus a lockfile schemaVersion guard — easy to mis-evolve
 * across the three repos that share this contract (web/desktop/app-main).
 *
 * Lives in sync-core/src/test because sync-core already has app-common-test on its test
 * classpath and hosts the sibling cross-repo fixture infrastructure.
 */
class SyncRefResolverTest {

    private val validSourceA = "d4rken-org/octi-web"
    private val validRefA = "84b9e57ac72e9b113de4c0c661e81938f8588f9d"
    private val validShaA = "d5037e9c9ef02e7b36e6f69c2081a1cdd6adc398b8203e9e8c9409a5c2810bd0"

    private fun validLock(): FixtureLock = FixtureLock(
        schemaVersion = InteropFixtures.LOCK_SCHEMA_VERSION,
        sources = mapOf(validSourceA to LockedSource(validRefA, validShaA)),
    )

    @Test
    fun `validateLock accepts a well-formed v2 lock`() {
        SyncRefResolver.validateLock(validLock())
    }

    @Test
    fun `validateLock rejects an empty sources map`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.validateLock(validLock().copy(sources = emptyMap()))
        }
    }

    @Test
    fun `validateLock rejects an unsupported schemaVersion`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.validateLock(validLock().copy(schemaVersion = 1))
        }
    }

    @Test
    fun `validateLock rejects a source not in SOURCE_PATHS`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.validateLock(
                validLock().copy(
                    sources = mapOf("d4rken-org/unknown-repo" to LockedSource(validRefA, validShaA)),
                ),
            )
        }
    }

    @Test
    fun `validateLock rejects a ref that is not a 40-char SHA`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.validateLock(
                validLock().copy(
                    sources = mapOf(validSourceA to LockedSource("not-a-sha", validShaA)),
                ),
            )
        }
    }

    @Test
    fun `validateLock rejects a manifest_sha256 that is not 64 hex chars`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.validateLock(
                validLock().copy(
                    sources = mapOf(validSourceA to LockedSource(validRefA, "abc")),
                ),
            )
        }
    }

    @Test
    fun `parseOverrides returns empty for null or blank`() {
        SyncRefResolver.parseOverrides(null) shouldBe emptyMap()
        SyncRefResolver.parseOverrides("") shouldBe emptyMap()
        SyncRefResolver.parseOverrides("   ") shouldBe emptyMap()
    }

    @Test
    fun `parseOverrides accepts a well-formed JSON map`() {
        val overrides = SyncRefResolver.parseOverrides("""{"$validSourceA":"$validRefA"}""")
        overrides shouldBe mapOf(validSourceA to validRefA)
    }

    @Test
    fun `parseOverrides rejects non-object JSON`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.parseOverrides("[]")
        }
    }

    @Test
    fun `parseOverrides rejects an unknown source key`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.parseOverrides("""{"d4rken-org/some-other":"$validRefA"}""")
        }
    }

    @Test
    fun `parseOverrides rejects a non-string value`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.parseOverrides("""{"$validSourceA":42}""")
        }
    }

    @Test
    fun `parseOverrides rejects a value that is not a 40-char SHA`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.parseOverrides("""{"$validSourceA":"deadbeef"}""")
        }
    }

    @Test
    fun `parseOverrides rejects invalid JSON`() {
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.parseOverrides("{not json")
        }
    }

    @Test
    fun `resolveAll with no overrides keeps the locked ref + manifestSha256`() {
        val resolved = SyncRefResolver.resolveAll(validLock(), emptyMap())
        resolved.size shouldBe 1
        val entry = resolved.getValue(validSourceA)
        entry.source shouldBe validSourceA
        entry.ref shouldBe validRefA
        entry.manifestSha256 shouldBe validShaA
    }

    @Test
    fun `resolveAll with override drops the manifestSha256 trust anchor`() {
        // Override is intentional: there's no committed sha that could pin against an
        // arbitrary upstream commit. The manifest's per-file shas become the only anchor.
        val overrideRef = "0000000000000000000000000000000000000001"
        val resolved = SyncRefResolver.resolveAll(
            validLock(),
            mapOf(validSourceA to overrideRef),
        )
        val entry = resolved.getValue(validSourceA)
        entry.ref shouldBe overrideRef
        entry.manifestSha256 shouldBe null
    }

    @Test
    fun `resolveAllFromEnv throws when override targets a source not present in the lock`() {
        // Workflow misconfiguration guard: an override for an allowlisted source that isn't
        // present in this repo's lock must fail loudly, not silently fall back. Goes through
        // the full env → parseOverrides → resolveAll path so SOURCE_PATHS validation is also
        // exercised on the way in.
        val env = mapOf(
            "INTEROP_FIXTURE_OVERRIDES" to """{"d4rken-org/octi-desktop":"${"a".repeat(40)}"}""",
        )
        // validLock() only carries octi-web; the desktop override is allowlisted by SOURCE_PATHS
        // but not present in the lock → resolveAll's missing-source guard fires.
        shouldThrow<IllegalArgumentException> {
            SyncRefResolver.resolveAllFromEnv(validLock(), env = env)
        }
    }

    @Test
    fun `resolveAllFromEnv applies an override to one source of a multi-source lock and leaves the other anchored`() {
        // Phase C2 onward: app-main's lock has both octi-web and octi-desktop. An override
        // workflow (e.g. octi-desktop's symmetric gate) pins only its own source; the other
        // must keep its locked ref + manifestSha256 anchor. End-to-end test via the env path
        // — the same path the production gate exercises.
        val secondSource = "d4rken-org/octi-desktop"
        val secondRef = "1e00e71fc60841fda80d7db4f630aa99b1112c9d"
        val secondSha = "ce2fd860ff124599bfc61a94f824c17c521cf79d744f06bc3551e284e6a37fe4"
        val multiSourceLock = FixtureLock(
            schemaVersion = InteropFixtures.LOCK_SCHEMA_VERSION,
            sources = mapOf(
                validSourceA to LockedSource(validRefA, validShaA),
                secondSource to LockedSource(secondRef, secondSha),
            ),
        )
        val override = "1111111111111111111111111111111111111111"
        val resolved = SyncRefResolver.resolveAllFromEnv(
            multiSourceLock,
            env = mapOf("INTEROP_FIXTURE_OVERRIDES" to """{"$secondSource":"$override"}"""),
        )

        val webEntry = resolved.getValue(validSourceA)
        webEntry.ref shouldBe validRefA
        webEntry.manifestSha256 shouldBe validShaA

        val desktopEntry = resolved.getValue(secondSource)
        desktopEntry.ref shouldBe override
        desktopEntry.manifestSha256 shouldBe null
    }

    @Test
    fun `resolveAllFromEnv reads INTEROP_FIXTURE_OVERRIDES from the supplied env map`() {
        val overrideRef = "0000000000000000000000000000000000000002"
        val resolved = SyncRefResolver.resolveAllFromEnv(
            validLock(),
            env = mapOf("INTEROP_FIXTURE_OVERRIDES" to """{"$validSourceA":"$overrideRef"}"""),
        )
        resolved.getValue(validSourceA).ref shouldBe overrideRef
        resolved.getValue(validSourceA).manifestSha256 shouldBe null
    }
}
