package testhelpers.interop

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Resolved fetch target for one source after override merge.
 *
 * `manifestSha256` is null when an override is in effect — there's no committed SHA we
 * could pin against an arbitrary upstream commit, so the manifest's per-file sha256s
 * become the sole trust anchor for that run.
 */
data class ResolvedSource(
    val source: String,
    val ref: String,
    val manifestSha256: String?,
)

/**
 * Multi-source resolver. Shared between [InteropFixtureSync] (fetch + cache write) and any
 * future cache reader. Both call [resolveAllFromEnv] so they always agree on effective refs
 * + cache directories after `INTEROP_FIXTURE_OVERRIDES` is applied.
 *
 * Mirrors octi-web's `src/__interop__/sync-ref-resolver.ts` and octi-desktop's
 * `SyncRefResolver.kt`. Each consumer lists only the producers it consumes; the
 * invariant is that for any shared source, the path string must agree across consumers.
 */
object SyncRefResolver {

    /**
     * Code-owned allowlist of upstream sources THIS REPO consumes. Adding a new source
     * requires a coordinated rollout: producer commits + ships its fixtures, then each
     * consumer adds it here. Cross-repo trust is never a runtime config.
     *
     * Value is the path under the source repo root that hosts `manifest.json` + fixture files.
     */
    val SOURCE_PATHS: Map<String, String> = mapOf(
        "d4rken-org/octi-web" to "src/__interop__/published",
        "d4rken-org/octi-desktop" to "src/test/resources/interop/published",
    )

    private val SHA40_RE = Regex("""^[a-f0-9]{40}$""")
    private val SHA256_RE = Regex("""^[a-f0-9]{64}$""")
    private val REPO_OWNER_RE = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")

    /** Throws [IllegalArgumentException] on shape drift. */
    fun validateLock(lock: FixtureLock) {
        require(lock.schemaVersion == InteropFixtures.LOCK_SCHEMA_VERSION) {
            "fixture-lock.json schemaVersion ${lock.schemaVersion} not supported; expected ${InteropFixtures.LOCK_SCHEMA_VERSION}"
        }
        require(lock.sources.isNotEmpty()) {
            "fixture-lock.json sources must not be empty"
        }
        for ((source, locked) in lock.sources) {
            require(REPO_OWNER_RE.matches(source)) {
                "fixture-lock.json sources key must be \"<owner>/<repo>\", got: $source"
            }
            require(source in SOURCE_PATHS) {
                "fixture-lock.json source \"$source\" not in code-owned SOURCE_PATHS registry; " +
                    "add it to SyncRefResolver if this is a new trusted upstream."
            }
            require(SHA40_RE.matches(locked.ref)) {
                "fixture-lock.json sources[$source].ref must be a 40-char lowercase commit SHA, got: ${locked.ref}"
            }
            require(SHA256_RE.matches(locked.manifestSha256)) {
                "fixture-lock.json sources[$source].manifest_sha256 must be 64 lowercase hex chars"
            }
        }
    }

    /**
     * Parse and validate `INTEROP_FIXTURE_OVERRIDES`. Empty/unset → empty map.
     * Every value validation throws — loud failure when the workflow sends a malformed
     * override, not silent fallback to the locked SHA.
     */
    fun parseOverrides(envValue: String?): Map<String, String> {
        if (envValue.isNullOrBlank()) return emptyMap()
        val parsed = try {
            InteropFixtures.json.parseToJsonElement(envValue)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "INTEROP_FIXTURE_OVERRIDES is not valid JSON: ${e.message}",
                e,
            )
        }
        require(parsed is JsonObject) {
            "INTEROP_FIXTURE_OVERRIDES must be a JSON object"
        }
        val out = mutableMapOf<String, String>()
        for ((key, value) in parsed) {
            require(REPO_OWNER_RE.matches(key)) {
                "INTEROP_FIXTURE_OVERRIDES key must be \"<owner>/<repo>\", got: $key"
            }
            require(key in SOURCE_PATHS) {
                "INTEROP_FIXTURE_OVERRIDES references unknown source \"$key\"; " +
                    "must be one of: ${SOURCE_PATHS.keys.joinToString(", ")}"
            }
            require(value is JsonPrimitive && value.isString) {
                "INTEROP_FIXTURE_OVERRIDES value for \"$key\" must be a string"
            }
            val str = value.content
            require(SHA40_RE.matches(str)) {
                "INTEROP_FIXTURE_OVERRIDES value for \"$key\" must be a 40-char lowercase commit SHA, got: $str"
            }
            out[key] = str
        }
        return out
    }

    /**
     * Apply overrides on top of locked refs. Returns a resolved source per lock entry.
     *
     * Throws if an override targets a source that's allowlisted but not actually in this
     * repo's lockfile — that's a workflow misconfiguration we want to surface loudly. A
     * silent drop here would let a Phase C upstream-gating workflow pass green against
     * a lock that doesn't yet know about that source.
     */
    fun resolveAll(lock: FixtureLock, overrides: Map<String, String>): Map<String, ResolvedSource> {
        val unknown = overrides.keys - lock.sources.keys
        require(unknown.isEmpty()) {
            "INTEROP_FIXTURE_OVERRIDES targets source(s) not present in fixture-lock.json: " +
                "${unknown.joinToString(", ")}. Known: ${lock.sources.keys.joinToString(", ")}"
        }
        val resolved = LinkedHashMap<String, ResolvedSource>(lock.sources.size)
        for ((source, locked) in lock.sources) {
            val overrideRef = overrides[source]
            resolved[source] = if (overrideRef != null) {
                ResolvedSource(source, overrideRef, manifestSha256 = null)
            } else {
                ResolvedSource(source, locked.ref, manifestSha256 = locked.manifestSha256)
            }
        }
        return resolved
    }

    /**
     * One-shot: parse env, merge with lock, return resolved sources per lock entry.
     */
    fun resolveAllFromEnv(
        lock: FixtureLock,
        env: Map<String, String> = System.getenv(),
    ): Map<String, ResolvedSource> {
        return resolveAll(lock, parseOverrides(env["INTEROP_FIXTURE_OVERRIDES"]))
    }
}
