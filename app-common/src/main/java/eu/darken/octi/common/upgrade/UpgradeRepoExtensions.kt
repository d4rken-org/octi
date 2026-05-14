package eu.darken.octi.common.upgrade

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart


suspend fun UpgradeRepo.isPro(): Boolean = upgradeInfo.first().isPro

/**
 * Maps [UpgradeRepo.upgradeInfo] to a [ProState] stream suitable for UI gating. Seeds
 * [ProState.Checking] until the first emission lands so callers can render a loading state
 * instead of momentarily showing a Locked UI to paying users during cold-start billing queries.
 *
 * Collection errors translate into [ProState.Error]; the upstream is left intact so any
 * downstream `.catch { … }` still observes the throwable if needed.
 */
fun UpgradeRepo.proState(): Flow<ProState> = upgradeInfo
    .map { info -> if (info.isPro) ProState.Unlocked else ProState.Locked }
    .onStart { emit(ProState.Checking) }
    .catch { emit(ProState.Error(it)) }