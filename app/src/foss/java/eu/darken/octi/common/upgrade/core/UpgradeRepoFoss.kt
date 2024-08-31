package eu.darken.octi.common.upgrade.core

import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoFoss @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val fossCache: FossCache,
    private val webpageTool: WebpageTool,
) : UpgradeRepo {
    override val mainWebsite: String = SITE

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

    fun launchGithubSponsorsUpgrade() = appScope.launch {
        log(TAG) { "launchGithubSponsorsUpgrade()" }
        fossCache.upgrade.valueBlocking = FossUpgrade(
            upgradedAt = Instant.now(),
            upgradeType = FossUpgrade.Type.GITHUB_SPONSORS
        )
        webpageTool.open(mainWebsite)
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
    }

    companion object {
        private const val SITE = "https://github.com/sponsors/d4rken"
        private val TAG = logTag("Upgrade", "Foss", "Repo")
    }
}