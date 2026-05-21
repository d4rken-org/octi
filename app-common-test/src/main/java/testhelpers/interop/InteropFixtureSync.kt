package testhelpers.interop

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

/**
 * Fetch + verify the multi-source interop fixtures pinned in `fixture-lock.json`.
 * Idempotent — repeated calls with a populated cache skip the network entirely.
 *
 * Called from each per-module consumer test's `@BeforeAll`. Object-level singleton
 * with double-checked locking so multiple test classes (modules-meta, -clipboard, -files)
 * only sync once per JVM, regardless of test execution order or parallelism.
 *
 * Why HttpURLConnection instead of `java.net.http.HttpClient`: this object lives in
 * `app-common-test/src/main` of an Android library module. `java.net.http` is API 33+ on
 * Android and would trigger Lint complaints in src/main even though the tests run on a
 * JVM. `HttpURLConnection` has been on the platform since API 1 — zero noise.
 */
object InteropFixtureSync {

    private const val FETCH_TIMEOUT_MS = 15_000
    private const val FETCH_RETRIES = 1

    /** Manifest size cap. 64 KiB is far above the realistic ceiling (~1 KiB today). */
    private const val MAX_MANIFEST_BYTES = 64 * 1024

    /** Per-file size cap. 256 KiB covers the largest realistic fixture (~5 KiB today). */
    private const val MAX_FIXTURE_BYTES = 256 * 1024

    /** Cap on file count to bound iteration on a hostile manifest. */
    private const val MAX_MANIFEST_FILES = 32

    /** Lockfile size cap. The lockfile is user-owned but bound it anyway to avoid mistakes. */
    private const val MAX_LOCKFILE_BYTES = 16 * 1024

    private val FIXTURE_FILE_RE = Regex(
        """^(?:[A-Za-z0-9_-][A-Za-z0-9_.-]*/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*\.json$""",
    )
    private val RESERVED_FILENAMES = setOf(".sha", "manifest.json")
    private val SHA256_RE = Regex("""^[a-f0-9]{64}$""")

    private val cacheDirsRef = AtomicReference<Map<String, Path>?>(null)

    /**
     * Populate (or reuse) the cache for every source in the lockfile. Returns a map
     * `source -> cache directory`. Safe to call from multiple test classes — only the
     * first call does work.
     */
    fun ensureSynced(): Map<String, Path> {
        cacheDirsRef.get()?.let { return it }
        synchronized(this) {
            cacheDirsRef.get()?.let { return it }
            val dirs = runSync()
            cacheDirsRef.set(dirs)
            return dirs
        }
    }

    /** Convenience overload — return the cache dir for one specific source. */
    fun ensureSynced(source: String): Path {
        val dirs = ensureSynced()
        return dirs[source] ?: error(
            "source '$source' not present in fixture-lock.json (known: ${dirs.keys.joinToString(", ")})",
        )
    }

    private fun runSync(): Map<String, Path> {
        val repoRoot = resolveRepoRoot()
        val lockPath = repoRoot.resolve("fixture-lock.json")
        check(Files.isRegularFile(lockPath)) {
            "fixture-lock.json not found at $lockPath. Run via `./gradlew test` (which sets " +
                "`interopRepoRoot`) or set `-DinteropRepoRoot=<path>` on the test JVM."
        }
        check(Files.size(lockPath) <= MAX_LOCKFILE_BYTES) {
            "fixture-lock.json is unexpectedly large (${Files.size(lockPath)} bytes); cap is $MAX_LOCKFILE_BYTES"
        }
        val lock = parseLock(Files.readAllBytes(lockPath))
        val resolvedAll = SyncRefResolver.resolveAllFromEnv(lock)
        for ((source, resolved) in resolvedAll) {
            if (resolved.manifestSha256 == null) {
                println("using override for $source: ${resolved.ref}")
            }
        }

        val out = LinkedHashMap<String, Path>(resolvedAll.size)
        for ((source, resolved) in resolvedAll) {
            out[source] = syncOne(repoRoot, resolved)
        }
        return out
    }

    private fun syncOne(repoRoot: Path, resolved: ResolvedSource): Path {
        // Cache layout: .cache/interop-fixtures/<owner>/<repo>/<sha>/. Keeping owner+repo
        // in the path means multiple sources never collide and any later phase reading
        // the cache directly can find what it needs from the same convention.
        val (owner, repo) = resolved.source.split("/", limit = 2)
        val cacheDir = repoRoot
            .resolve(".cache").resolve("interop-fixtures")
            .resolve(owner).resolve(repo).resolve(resolved.ref)

        // Under override (no committed manifest sha to pin against), always re-fetch the
        // manifest. The cache may still have valid bytes, but the manifest must come from
        // the live upstream so it can't be a poisoned local copy.
        if (resolved.manifestSha256 != null && cacheIsValid(cacheDir, resolved)) {
            println("interop fixtures cache hit: ${resolved.source}@${resolved.ref}")
            return cacheDir
        }

        println("fetching interop fixtures from ${resolved.source}@${resolved.ref}...")
        Files.createDirectories(cacheDir)

        val manifestBytes = fetchBytes("${rawBaseUrl(resolved)}/manifest.json", MAX_MANIFEST_BYTES)
        val manifest = parseAndValidateManifest(manifestBytes, resolved)
        Files.write(cacheDir.resolve("manifest.json"), manifestBytes)

        for ((name, entry) in manifest.files) {
            val dest = cacheDir.resolve(name)
            // Under override, cached files for this ref may already be valid; skip
            // re-download to spare bandwidth on unchanged blobs. Apply the same size cap
            // here as on fresh fetches so a tampered local cache file can't be slurped whole.
            if (Files.isRegularFile(dest) &&
                Files.size(dest) <= MAX_FIXTURE_BYTES &&
                InteropFixtures.sha256Hex(Files.readAllBytes(dest)) == entry.sha256
            ) {
                println("  $name (cached, sha256 ok)")
                continue
            }
            val bytes = fetchBytes("${rawBaseUrl(resolved)}/$name", MAX_FIXTURE_BYTES)
            val actual = InteropFixtures.sha256Hex(bytes)
            check(actual == entry.sha256) {
                "$name sha256 mismatch — expected ${entry.sha256}, got $actual"
            }
            Files.createDirectories(dest.parent)
            Files.write(dest, bytes)
            println("  $name (${bytes.size} bytes, sha256 ok)")
        }

        // Marker written last so an interrupted run never produces a "valid" cache.
        // (Files.writeString is JDK 11+ and not desugared on Android library modules; encode by hand.)
        Files.write(cacheDir.resolve(".sha"), resolved.ref.toByteArray(Charsets.UTF_8))
        println("interop fixtures synced: $cacheDir")
        return cacheDir
    }

    /**
     * Single source of truth for validating fixture bytes against the lockfile (or, under
     * override, against the manifest's self-claimed shape only). Used on both cold-fetch
     * and warm-cache paths so a stale cache cannot pass weaker checks than a fresh
     * download.
     */
    private fun parseAndValidateManifest(bytes: ByteArray, resolved: ResolvedSource): FixtureManifest {
        if (resolved.manifestSha256 != null) {
            val actualSha = InteropFixtures.sha256Hex(bytes)
            check(actualSha == resolved.manifestSha256) {
                "manifest sha256 mismatch for ${resolved.source} — expected ${resolved.manifestSha256}, got $actualSha. " +
                    "Either fixture-lock.json is stale or upstream history was rewritten."
            }
        }
        val manifest = try {
            InteropFixtures.json.decodeFromString(FixtureManifest.serializer(), bytes.decodeToString())
        } catch (e: Exception) {
            error("manifest.json failed to parse: ${e.message}")
        }
        check(manifest.schemaVersion == InteropFixtures.FIXTURE_SCHEMA_VERSION) {
            "unsupported manifest schemaVersion ${manifest.schemaVersion}; this client knows v${InteropFixtures.FIXTURE_SCHEMA_VERSION}"
        }
        check(manifest.source == resolved.source) {
            "manifest source ${manifest.source} disagrees with resolved source ${resolved.source}"
        }
        check(manifest.files.size <= MAX_MANIFEST_FILES) {
            "manifest declares ${manifest.files.size} files; cap is $MAX_MANIFEST_FILES"
        }
        for ((name, entry) in manifest.files) {
            check(FIXTURE_FILE_RE.matches(name)) { "manifest contains invalid file name: $name" }
            check(name.split("/").none { it == "." || it == ".." }) {
                "manifest contains path-traversal file name: $name"
            }
            check(name !in RESERVED_FILENAMES) { "manifest references reserved file name: $name" }
            check(SHA256_RE.matches(entry.sha256)) { "manifest entry for $name has invalid sha256" }
        }
        return manifest
    }

    private fun parseLock(bytes: ByteArray): FixtureLock {
        val lock = try {
            InteropFixtures.json.decodeFromString(FixtureLock.serializer(), bytes.decodeToString())
        } catch (e: Exception) {
            error("fixture-lock.json failed to parse: ${e.message}")
        }
        SyncRefResolver.validateLock(lock)
        return lock
    }

    private fun cacheIsValid(cacheDir: Path, resolved: ResolvedSource): Boolean {
        val markerPath = cacheDir.resolve(".sha")
        if (!Files.isRegularFile(markerPath)) return false
        // The .sha marker holds a 40-byte SHA; a manually-bloated file is malicious. Cap.
        if (Files.size(markerPath) > 128) return false
        if (Files.readAllBytes(markerPath).decodeToString().trim() != resolved.ref) return false

        val manifestPath = cacheDir.resolve("manifest.json")
        if (!Files.isRegularFile(manifestPath)) return false
        // Same caps as the on-fetch path so a tampered local cache file can't be slurped whole.
        if (Files.size(manifestPath) > MAX_MANIFEST_BYTES) return false
        val manifestBytes = Files.readAllBytes(manifestPath)

        val manifest = try {
            parseAndValidateManifest(manifestBytes, resolved)
        } catch (_: IllegalStateException) {
            return false
        }

        for ((name, entry) in manifest.files) {
            val filePath = cacheDir.resolve(name)
            if (!Files.isRegularFile(filePath)) return false
            if (Files.size(filePath) > MAX_FIXTURE_BYTES) return false
            if (InteropFixtures.sha256Hex(Files.readAllBytes(filePath)) != entry.sha256) return false
        }
        return true
    }

    private fun rawBaseUrl(resolved: ResolvedSource): String {
        val path = SyncRefResolver.SOURCE_PATHS[resolved.source]
            ?: error("source \"${resolved.source}\" not in SOURCE_PATHS")
        return "https://raw.githubusercontent.com/${resolved.source}/${resolved.ref}/$path"
    }

    private fun fetchBytes(url: String, maxBytes: Int): ByteArray {
        var lastError: Throwable? = null
        repeat(FETCH_RETRIES + 1) { attempt ->
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = FETCH_TIMEOUT_MS
                    readTimeout = FETCH_TIMEOUT_MS
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }
                try {
                    val status = conn.responseCode
                    when {
                        status in 200..299 -> {
                            val bytes = conn.inputStream.use { stream ->
                                // Read with a hard cap so an unexpectedly huge response doesn't OOM the JVM.
                                val out = java.io.ByteArrayOutputStream()
                                val buf = ByteArray(8 * 1024)
                                var total = 0
                                while (true) {
                                    val n = stream.read(buf)
                                    if (n < 0) break
                                    total += n
                                    check(total <= maxBytes) {
                                        "response from $url exceeds $maxBytes bytes"
                                    }
                                    out.write(buf, 0, n)
                                }
                                out.toByteArray()
                            }
                            return bytes
                        }
                        // 4xx is deterministic (bad ref / typo'd path / private repo). Don't burn
                        // retries — surface the real cause.
                        status in 400..499 ->
                            throw IllegalStateException("GET $url → HTTP $status (4xx, not retried)")
                        else -> throw IOException("GET $url → HTTP $status")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt < FETCH_RETRIES) println("  fetch IO error (${e.message}); retrying...")
            }
        }
        throw IllegalStateException(
            "GET $url failed after ${FETCH_RETRIES + 1} attempts: ${lastError?.message}",
            lastError,
        )
    }

    private fun resolveRepoRoot(): Path {
        // Each consuming module's build.gradle.kts passes `-DinteropRepoRoot=<rootDir>`
        // on its test task. Fallback to user.dir for IDE-direct test runs (the IDE typically
        // runs tests from the module dir, in which case `user.dir` ends up wrong — module
        // authors set `interopRepoRoot` to be explicit).
        // Path.of is Java 11+ and not exposed by the Android compileSdk JAR; use Paths.get
        // (Java 7) which the Kotlin compiler resolves cleanly in an android.library module.
        System.getProperty("interopRepoRoot")?.let { return Paths.get(it).toAbsolutePath() }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    }
}
