package eu.darken.octi.common.upgrade.core

import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class UpgradeRepoFoss @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val fossCache: FossCache,
    private val webpageTool: WebpageTool,
) : UpgradeRepo {
    override val storeSite: String = STORE_SITE
    override val upgradeSite: String = UPGRADE_SITE
    override val betaSite: String = BETA_SITE

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    // Written only from the sharing coroutine (single collector) — no synchronization needed.
    // Recorded INSIDE the flatMapLatest block, upstream of its channel buffer: a downstream onEach
    // can still be waiting on a buffered emission when the inner flow throws, and the retry below
    // would then read a stale (null) value and revoke an entitlement we already saw.
    private var lastKnownInfo: Info? = null

    // Integer, capped backoff. Overridable so tests can drive the retry loop without sleeping
    // through the real schedule.
    internal var retryDelayMs: (attempt: Long) -> Long = { (30_000L * (it + 1)).coerceAtMost(300_000L) }

    // The retry loop lives INSIDE the flatMapLatest, so a failed read completes only this inner
    // subscription and refresh() can still resubscribe — while the loop itself keeps trying. Both
    // halves are needed: before this, a cold unshared combine simply DIED on a throwing cache read
    // and took every collector with it, and the app-lifetime UpgradeEntitlementObserver keeps the
    // share hot for the whole process, so nothing ever resubscribes on its own — refresh-driven
    // recovery alone would never reach an idle user staring at an error screen. Cancellation is
    // rethrown, never retried: a cancelled subscription is not a failure. Last-known preservation:
    // a late read failure must not revoke an entitlement we already saw, so the error rides on the
    // previously seen Info instead of on a fresh (free) one.
    override val upgradeInfo: Flow<UpgradeRepo.Info> = refreshTrigger
        .flatMapLatest {
            fossCache.upgrade.flow
                .map { data ->
                    if (data == null) {
                        Info()
                    } else {
                        Info(
                            isPro = true,
                            upgradedAt = data.upgradedAt,
                            fossUpgradeType = data.upgradeType,
                        )
                    }
                }
                // Same coroutine as the throw below, so the ordering is guaranteed. Only
                // successfully mapped elements pass here — retry emissions go straight downstream
                // and never record themselves as a last known state.
                .onEach { lastKnownInfo = it }
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) throw cause
                    log(TAG, WARN) { "upgradeInfo read failed (attempt=$attempt): ${cause.asLog()}" }
                    // Once per failure episode, not once per attempt: the FOSS ViewModel raises an
                    // error dialog for every non-Pro error emission, and a per-attempt emission
                    // would re-raise that dialog on every backoff wake-up.
                    if (attempt == 0L) emit((lastKnownInfo ?: Info()).copy(error = cause))
                    delay(retryDelayMs(attempt))
                    true
                }
        }
        // persistUpgrade() refreshes unconditionally, including when it kept an existing record:
        // dedupe the identical re-emission that produces.
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }
        .shareIn(appScope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // Synchronous so the caller learns whether the page actually opened: the FOSS unlock heuristic
    // only arms on a successful launch, and a fire-and-forget coroutine can't report that back.
    fun openGithubSponsorsPage(): Boolean {
        log(TAG) { "openGithubSponsorsPage()" }
        return webpageTool.open(upgradeSite)
    }

    /**
     * Create-only-if-absent inside the store transaction: an existing record (and its upgradedAt —
     * the user-visible "supporter since" date) is never replaced. The VM-level isPro guard alone is
     * not race-free: it reads a shareIn replay that can be stale. Note the kept record is still
     * re-encoded through the current schema — decoded fields are preserved exactly.
     *
     * Caveat, verified for this app: the kotlinx `createValue` decode fallback DEFAULTS TO FALSE and
     * [FossCache] passes nothing, so a stored record that fails to decode makes this transaction
     * THROW instead of reading as absent. The persist then fails outright and the caller restores
     * its pending-return marker for a later retry — there is NO clobber path here, unlike the
     * fleet's leaves that enable the fallback.
     *
     * The accepted cost of that choice: a genuinely corrupt record fails permanently. Every armed
     * resume repeats the same sequence — the read throws, the marker is restored, the error surfaces
     * — instead of quietly healing itself. That is deliberate: an honest repeated signal, nothing
     * destroyed, and recovery stays an explicit user action.
     *
     * @return true if a new record was created, false if an existing record was kept.
     */
    suspend fun persistUpgrade(): Boolean {
        log(TAG) { "persistUpgrade()" }
        val updated = fossCache.upgrade.update { existing ->
            existing ?: FossUpgrade(
                upgradedAt = Clock.System.now(),
                upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
            )
        }
        // A returned transaction proves the store is readable again: revive a possibly error-stuck
        // inner flow so the record propagates to collectors still holding the error replay.
        refresh()
        // Cross-module property (app-common Updated.old): smart cast refused.
        val previous = updated.old
        return if (previous == null) {
            true
        } else {
            log(TAG, WARN) { "persistUpgrade(): Record already exists (upgradedAt=${previous.upgradedAt}), keeping it" }
            false
        }
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = UUID.randomUUID()
    }

    data class Info(
        override val isPro: Boolean = false,
        override val upgradedAt: Instant? = null,
        val fossUpgradeType: FossUpgrade.Type? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS

        // FOSS reads a synchronous local cache — every emission is a definitive entitlement result.
        override val isSettled: Boolean = true
    }

    companion object {
        private const val STORE_SITE = "https://github.com/d4rken-org/octi"
        private const val UPGRADE_SITE = "https://github.com/sponsors/d4rken"
        private const val BETA_SITE = "https://github.com/d4rken-org/octi/releases"
        private val TAG = logTag("Upgrade", "Foss", "Repo")
    }
}
