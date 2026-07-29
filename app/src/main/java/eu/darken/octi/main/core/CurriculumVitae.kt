package eu.darken.octi.main.core

import android.content.Context
import android.content.pm.PackageInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class CurriculumVitae @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "curriculum_vitae")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    private val usPkgInfo: PackageInfo by lazy { context.packageManager.getPackageInfo(context.packageName, 0) }

    private val _updateHistory = dataStore.createValue("stats.update.history", emptyList<String>(), json)
    private val _installedFirst = dataStore.createValue<Instant?>("stats.install.first", null, json)
    private val _launchedLast = dataStore.createValue<Instant?>("stats.launched.last", null, json)
    private val _launchedCount = dataStore.createValue("stats.launched.count", 0)
    private val _launchedCountBeta = dataStore.createValue("stats.launched.beta.count", 0)

    fun updateAppLaunch() = appScope.launch {
        log(TAG, VERBOSE) { "updateAppLaunch()" }
        updateInstalledAt()
        updateLaunchTime()
        updateLaunchCount()
        if (BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE) {
            updateLaunchCountBeta()
        }
        updateVersionHistory()
    }

    private suspend fun updateInstalledAt() {
        val installedAt = _installedFirst.value()
        if (installedAt != null) {
            log(TAG) { "Installed at: $installedAt" }
            return
        }
        val newInstalledAt = usPkgInfo.firstInstallTime.let { Instant.fromEpochMilliseconds(it) }
        log(TAG) { "Saving install time: $newInstalledAt" }
        _installedFirst.value(newInstalledAt)
    }

    private suspend fun updateLaunchTime() {
        val oldLaunchTime = _launchedLast.value()
        log(TAG) { "Last launch time was $oldLaunchTime" }
        _launchedLast.value(Clock.System.now())
    }

    private suspend fun updateLaunchCount() {
        val newLaunchCount = _launchedCount.value() + 1
        log(TAG) { "Launch count is $newLaunchCount" }
        _launchedCount.value(newLaunchCount)
    }

    private suspend fun updateLaunchCountBeta() {
        val newLaunchCount = _launchedCountBeta.value() + 1
        log(TAG) { "Launch BETA count is $newLaunchCount" }
        _launchedCountBeta.value(newLaunchCount)
    }

    val history = _updateHistory.flow.map { versions ->
        versions.mapNotNull {
            try {
                Version.parse(it, false)
            } catch (e: VersionFormatException) {
                log(TAG, WARN) { "Invalid version format: $it out of $versions" }
                null
            }
        }
    }

    private suspend fun updateVersionHistory() {
        val history = _updateHistory.value()
        log(TAG) { "Current version history is $history" }

        val lastVersion = history.lastOrNull()
        val current = usPkgInfo.versionName ?: BuildConfigWrap.VERSION_NAME
        if (lastVersion != current) {
            val versionHistory = history + current
            log(TAG) { "Update happened, new version history is $versionHistory" }
            _updateHistory.value(versionHistory)
        }
    }

    private val _openedLast = dataStore.createValue<Instant?>("stats.opened.last", null, json)
    private val _openedCount = dataStore.createValue("stats.opened.count", 0)

    fun updateAppOpened() = appScope.launch {
        log(TAG, VERBOSE) { "updateAppOpened()" }
        updateOpenedTime()
        updateOpenedCount()
    }

    private suspend fun updateOpenedTime() {
        val oldOpenedTime = _openedLast.value()
        log(TAG) { "Last open was $oldOpenedTime" }
        _openedLast.value(Clock.System.now())
    }

    private suspend fun updateOpenedCount() {
        val newOpenedcount = _openedCount.value() + 1
        log(TAG) { "Open count is $newOpenedcount" }
        _openedCount.value(newOpenedcount)
    }

    // Lifetime Pro-state history: how often the billing grace period had to save this install, and
    // whether/when Pro was actually lost. Written by the gplay UpgradeRepo from FRESH Play data
    // only; surfaced in every debug log recording so billing complaints arrive with context.
    // Raw preference keys (not DataStoreValues): a transition must update state, counter, and
    // timestamp in ONE transaction.
    private val proStateLastKey = stringPreferencesKey("stats.pro.state.last")
    private val proGraceCountKey = intPreferencesKey("stats.pro.grace.count")
    private val proGraceLastKey = longPreferencesKey("stats.pro.grace.last")
    private val proLostCountKey = intPreferencesKey("stats.pro.lost.count")
    private val proLostLastKey = longPreferencesKey("stats.pro.lost.last")

    enum class ProState { PURCHASED, GRACE, FREE }

    data class ProHistory(
        val lastState: ProState?,
        val graceEngagedCount: Int,
        val graceEngagedLast: Instant?,
        val proLostCount: Int,
        val proLostLast: Instant?,
    )

    // Suspend on purpose: the caller's collector is ordered (billing commit order) and a
    // fire-and-forget launch per update could apply rapid transitions out of order.
    suspend fun updateProState(state: ProState) {
        dataStore.edit { prefs ->
            val previous = parseProState(prefs[proStateLastKey])
            if (previous == state) return@edit
            log(TAG, INFO) { "updateProState(): $previous -> $state" }
            val now = Clock.System.now().toEpochMilliseconds()
            when (proTransitionOf(previous, state)) {
                ProTransition.GRACE_ENGAGED -> {
                    prefs[proGraceCountKey] = (prefs[proGraceCountKey] ?: 0) + 1
                    prefs[proGraceLastKey] = now
                }

                ProTransition.PRO_LOST -> {
                    prefs[proLostCountKey] = (prefs[proLostCountKey] ?: 0) + 1
                    prefs[proLostLastKey] = now
                }

                // First observation (or an unknown/corrupt stored value): baseline only.
                null -> {}
            }
            prefs[proStateLastKey] = state.name
        }
    }

    suspend fun proHistory(): ProHistory {
        val prefs = dataStore.data.first()
        return ProHistory(
            lastState = parseProState(prefs[proStateLastKey]),
            graceEngagedCount = prefs[proGraceCountKey] ?: 0,
            graceEngagedLast = prefs[proGraceLastKey]?.let { Instant.fromEpochMilliseconds(it) },
            proLostCount = prefs[proLostCountKey] ?: 0,
            proLostLast = prefs[proLostLastKey]?.let { Instant.fromEpochMilliseconds(it) },
        )
    }

    internal enum class ProTransition { GRACE_ENGAGED, PRO_LOST }

    companion object {
        internal val TAG = logTag("Debug", "CurriculumVitae")

        // Tolerant of blank/corrupt/future enum names: an unknown stored value must behave like a
        // fresh baseline, not kill the update job or the recorder's history read.
        internal fun parseProState(raw: String?): ProState? =
            raw?.let { r -> ProState.entries.firstOrNull { it.name == r } }

        // Which transitions count: grace only "engages" coming FROM a confirmed purchase, and Pro
        // is only "lost" when a previously Pro-ish state drops to FREE. Everything else (baseline,
        // recovery, unknown previous value) just moves the stored state. Pure and unit-tested.
        internal fun proTransitionOf(previous: ProState?, current: ProState): ProTransition? = when {
            previous == ProState.PURCHASED && current == ProState.GRACE -> ProTransition.GRACE_ENGAGED
            (previous == ProState.PURCHASED || previous == ProState.GRACE) && current == ProState.FREE ->
                ProTransition.PRO_LOST

            else -> null
        }
    }
}