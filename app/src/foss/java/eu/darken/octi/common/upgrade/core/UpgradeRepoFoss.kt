package eu.darken.octi.common.upgrade.core

import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class UpgradeRepoFoss @Inject constructor(
    private val fossCache: FossCache,
    private val webpageTool: WebpageTool,
) : UpgradeRepo {
    override val storeSite: String = STORE_SITE
    override val upgradeSite: String = UPGRADE_SITE
    override val betaSite: String = BETA_SITE

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    override val upgradeInfo: Flow<UpgradeRepo.Info> = combine(
        fossCache.upgrade.flow,
        refreshTrigger
    ) { data, _ ->
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
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }

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
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS

        // FOSS reads a synchronous local cache — every emission is a definitive entitlement result.
        override val isSettled: Boolean = true
        override val error: Throwable? = null
    }

    companion object {
        private const val STORE_SITE = "https://github.com/d4rken-org/octi"
        private const val UPGRADE_SITE = "https://github.com/sponsors/d4rken"
        private const val BETA_SITE = "https://github.com/d4rken-org/octi/releases"
        private val TAG = logTag("Upgrade", "Foss", "Repo")
    }
}