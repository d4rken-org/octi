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
    fun `resolveAll throws when override targets a source not present in the lock`() {
        // Workflow misconfiguration guard: a override for an allowlisted-but-not-yet-locked
        // source (e.g. octi-desktop before Phase C lands it in app-main's lock) must fail loudly,
        // not silently fall back to the locked refs.
        shouldThrow<IllegalArgumentException> {
            // Use any SOURCE_PATHS-known key OTHER than what's in this lock. We only have one
            // SOURCE_PATHS entry today, so simulate a second one being added to overrides without
            // first being added to the lock by reaching for an arbitrary known string from the
            // override path's validation set. Since SOURCE_PATHS only has octi-web today, this
            // test uses parseOverrides validation as a precondition — any other key would be
            // rejected at parse-time, so the only way to exercise resolveAll's check is to call
            // it directly with a manually-constructed override map.
            SyncRefResolver.resolveAll(
                validLock(),
                mapOf("d4rken-org/octi-future" to "0000000000000000000000000000000000000003"),
            )
        }
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
