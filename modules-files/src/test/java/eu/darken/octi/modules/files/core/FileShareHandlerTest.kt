package eu.darken.octi.modules.files.core

import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FileShareHandlerTest : BaseTest() {

    private val syncSettings = mockk<SyncSettings>()
    private val cache = mockk<FileShareCache>()
    private val settings = mockk<FileShareSettings>(relaxed = true)

    @BeforeEach
    fun setup() {
        every { syncSettings.deviceId } returns DeviceId("self")
        coEvery { cache.get(any()) } returns null
    }

    private fun makeHandler(scope: kotlinx.coroutines.CoroutineScope) = FileShareHandler(
        appScope = scope,
        settings = settings,
        cache = cache,
        syncSettings = syncSettings,
    )

    private fun makeRequest(
        targetDeviceId: String = "target",
        blobKey: String = "blob-1",
        retainUntil: kotlin.time.Instant = Clock.System.now() + 2.hours,
    ) = FileShareInfo.DeleteRequest(
        targetDeviceId = targetDeviceId,
        blobKey = blobKey,
        requestedAt = Clock.System.now(),
        retainUntil = retainUntil,
    )

    @Nested
    inner class `upsertDeleteRequest` {

        @Test
        fun `inserts a new request`() = runTest2 {
            val handler = makeHandler(backgroundScope)
            val request = makeRequest(blobKey = "new-blob")

            handler.upsertDeleteRequest(request)

            handler.currentOwn().deleteRequests shouldBe listOf(request)
        }

        @Test
        fun `deduplicates on targetDeviceId and blobKey`() = runTest2 {
            val handler = makeHandler(backgroundScope)
            val original = makeRequest(blobKey = "dup-blob", retainUntil = Clock.System.now() + 1.hours)
            val updated = makeRequest(blobKey = "dup-blob", retainUntil = Clock.System.now() + 2.hours)

            handler.upsertDeleteRequest(original)
            handler.upsertDeleteRequest(updated)

            val requests = handler.currentOwn().deleteRequests
            requests.size shouldBe 1
            requests.single().retainUntil shouldBe updated.retainUntil
        }

        @Test
        fun `ignores already-expired inserts`() = runTest2 {
            val handler = makeHandler(backgroundScope)
            val expired = makeRequest(retainUntil = Clock.System.now() - 1.hours)

            handler.upsertDeleteRequest(expired)

            handler.currentOwn().deleteRequests shouldBe emptyList()
        }

        @Test
        fun `drops existing expired entries on insert`() = runTest2 {
            val handler = makeHandler(backgroundScope)
            val stale = makeRequest(blobKey = "stale", retainUntil = Clock.System.now() - 1.hours)
            // Insert stale entry directly via mutateDeleteRequests to bypass the expiry guard
            handler.mutateDeleteRequests { it + stale }

            val fresh = makeRequest(blobKey = "fresh")
            handler.upsertDeleteRequest(fresh)

            val requests = handler.currentOwn().deleteRequests
            requests.size shouldBe 1
            requests.single().blobKey shouldBe "fresh"
        }
    }
}
