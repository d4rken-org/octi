package eu.darken.octi.common.upgrade.core

import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

// Serialization of the persisted upgrade is covered by FossUpgradeSerializationTest.
class UpgradeRepoFossTest : BaseTest() {

    private val record = FossUpgrade(
        upgradedAt = Instant.fromEpochMilliseconds(0L),
        upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
    )

    private fun createUpgradeValue(cacheFlow: Flow<FossUpgrade?>) = mockk<DataStoreValue<FossUpgrade?>>().apply {
        every { flow } returns cacheFlow
        coEvery { update(any()) } returns DataStoreValue.Updated(old = null, new = record)
    }

    // Real dispatchers, not a test scheduler: the shareIn sharing coroutine and the collectors have
    // to actually interleave here, and the whole point is that a failure settles instead of killing
    // the flow.
    private fun createRepo(appScope: CoroutineScope, upgradeValue: DataStoreValue<FossUpgrade?>) = UpgradeRepoFoss(
        appScope = appScope,
        fossCache = mockk<FossCache>().apply { every { upgrade } returns upgradeValue },
        webpageTool = mockk(),
    )

    @Test
    fun `test upgrade info pro status mapping`() {
        UpgradeRepoFoss.Info(
            isPro = false,
            upgradedAt = null,
        ).apply {
            type shouldBe UpgradeRepo.Type.FOSS
            isPro shouldBe false
            // A local cache read is authoritative from the first emission, there is no billing
            // handshake to wait out.
            isSettled shouldBe true
            error shouldBe null
        }

        UpgradeRepoFoss.Info(
            isPro = true,
            upgradedAt = Instant.fromEpochMilliseconds(0L),
            fossUpgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
        ).apply {
            isPro shouldBe true
            upgradedAt shouldBe Instant.fromEpochMilliseconds(0L)
            fossUpgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
        }
    }

    @Test
    fun `a failing cache read surfaces as a settled error Info instead of dying`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = createRepo(scope, createUpgradeValue(flow { throw IOException("cache broken") }))

            withTimeout(10_000) {
                repo.upgradeInfo.first().apply {
                    // Type and message: a bare non-null check would also pass on a swallow-and-wrap.
                    error.shouldBeInstanceOf<IOException>().message shouldBe "cache broken"
                    isPro shouldBe false
                    // The UI must be able to render this: an unsettled error is an endless spinner.
                    isSettled shouldBe true
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a late cache failure keeps the last known entitlement`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = createRepo(
                scope,
                createUpgradeValue(
                    flow {
                        emit(record)
                        throw IOException("cache broken later")
                    }
                ),
            )

            withTimeout(10_000) {
                val infos = repo.upgradeInfo.take(2).toList()

                infos[0].apply {
                    isPro shouldBe true
                    error shouldBe null
                }
                // The entitlement we already saw must survive the read failure — a revoked Pro
                // status would kick a supporter back to the pitch.
                infos[1].apply {
                    isPro shouldBe true
                    error.shouldBeInstanceOf<IOException>()
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a transient cache failure recovers automatically without refresh`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val subscriptions = AtomicInteger(0)
            val upgradeValue = createUpgradeValue(
                flow {
                    if (subscriptions.getAndIncrement() == 0) throw IOException("cache broken")
                    emit(record)
                }
            )
            val repo = createRepo(scope, upgradeValue)
            // Nobody is going to tap anything: the backoff loop itself has to bring the entitlement
            // back. Shrunk so the test doesn't sit out the shipped 30s first delay.
            repo.retryDelayMs = { 10L }

            // A single long-lived collector, exactly like the app-lifetime entitlement observer:
            // no resubscription happens on its own, so recovery can only come from the retry loop.
            val received = Channel<UpgradeRepo.Info>(Channel.UNLIMITED)
            scope.launch { repo.upgradeInfo.collect { received.send(it) } }

            withTimeout(10_000) {
                received.receive().error.shouldBeInstanceOf<IOException>()

                // No refresh(), no persistUpgrade() — the automatic attempt after the backoff did this.
                received.receive().apply {
                    isPro shouldBe true
                    error shouldBe null
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `refresh cancels an in-flight backoff`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val subscriptions = AtomicInteger(0)
            val upgradeValue = createUpgradeValue(
                flow {
                    if (subscriptions.getAndIncrement() == 0) throw IOException("cache broken")
                    emit(record)
                }
            )
            val repo = createRepo(scope, upgradeValue)
            // Longer than the test could ever wait: if refresh() did not cancel the in-flight
            // backoff, this test would time out — which is exactly the old shape's dead window.
            repo.retryDelayMs = { 10 * 60_000L }

            val received = Channel<UpgradeRepo.Info>(Channel.UNLIMITED)
            scope.launch { repo.upgradeInfo.collect { received.send(it) } }

            withTimeout(10_000) {
                received.receive().error.shouldBeInstanceOf<IOException>()

                repo.refresh()

                received.receive().apply {
                    isPro shouldBe true
                    error shouldBe null
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a successful persist revives an error-stuck upgradeInfo`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // First subscription fails, later ones read fine: the store recovered, but the shared
            // flow is still replaying the error Info to everyone.
            val subscriptions = AtomicInteger(0)
            val upgradeValue = createUpgradeValue(
                flow {
                    if (subscriptions.getAndIncrement() == 0) throw IOException("cache broken")
                    emit(record)
                }
            )
            val repo = createRepo(scope, upgradeValue)
            // Default backoff (30s and up): the revival must NOT be the retry loop waking up.

            val received = Channel<UpgradeRepo.Info>(Channel.UNLIMITED)
            scope.launch { repo.upgradeInfo.collect { received.send(it) } }

            withTimeout(10_000) {
                received.receive().error.shouldBeInstanceOf<IOException>()

                // No explicit refresh() from the test: persist has to do the reviving itself,
                // otherwise the user's unlock never reaches the screen they are looking at.
                repo.persistUpgrade() shouldBe true

                received.receive().apply {
                    isPro shouldBe true
                    error shouldBe null
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `retries emit the error dialog event only once per failure episode`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // Two consecutive failures, then a healthy read: the FOSS ViewModel raises an error
            // dialog for every non-Pro error emission, so the retry loop must stay silent after its
            // first report instead of re-raising the dialog on every backoff wake-up.
            val subscriptions = AtomicInteger(0)
            val upgradeValue = createUpgradeValue(
                flow {
                    if (subscriptions.getAndIncrement() < 2) throw IOException("cache broken")
                    emit(record)
                }
            )
            val repo = createRepo(scope, upgradeValue)
            repo.retryDelayMs = { 10L }

            withTimeout(10_000) {
                val infos = repo.upgradeInfo.take(2).toList()

                infos[0].error.shouldBeInstanceOf<IOException>()
                infos[1].apply {
                    isPro shouldBe true
                    error shouldBe null
                }
                // Both failures really happened (two throwing reads + the healthy one), yet only
                // the first one reached a collector.
                subscriptions.get() shouldBe 3
                infos.count { it.error != null } shouldBe 1
            }
        } finally {
            scope.cancel()
        }
    }
}
