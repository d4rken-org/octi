package eu.darken.octi.sync.core.blob

import android.content.Context
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File

class BlobCacheDirsTest : BaseTest() {

    private val context = mockk<Context>()

    @TempDir
    lateinit var cacheDir: File

    private fun newDirs(): BlobCacheDirs {
        every { context.cacheDir } returns cacheDir
        return BlobCacheDirs(context)
    }

    @Test
    fun `subdir is recreated if deleted between accesses`() {
        val dirs = newDirs()

        val first = dirs.staging
        first.isDirectory shouldBe true
        first.deleteRecursively() shouldBe true
        first.exists() shouldBe false

        // Getter-backed val must re-mkdir on next access; lazy val would not.
        val second = dirs.staging
        second.isDirectory shouldBe true
        second.absolutePath shouldBe first.absolutePath
    }

    @Test
    fun `subdir throws when path already exists as a regular file`() {
        val dirs = newDirs()

        // Pre-create blob-staging as a regular file, blocking mkdirs().
        val conflicting = File(cacheDir, "blob-staging")
        conflicting.writeText("not a dir")

        shouldThrow<IllegalStateException> { dirs.staging }
    }
}
