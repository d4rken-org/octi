package eu.darken.octi.sync.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.createSetValue
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Singleton
class SyncSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "sync_settings")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val deviceLabel = dataStore.createValue("sync.device.self.label", null as String?)

    val backgroundSyncEnabled = dataStore.createValue("sync.background.enabled", true)

    val backgroundSyncInterval = dataStore.createValue("sync.background.interval.minutes", 60)

    val backgroundSyncOnMobile = dataStore.createValue("sync.background.mobile.enabled", true)

    val backgroundSyncChargingEnabled = dataStore.createValue("sync.background.charging.enabled", false)

    val backgroundSyncChargingInterval = dataStore.createValue("sync.background.charging.interval.minutes", 15)

    val foregroundSyncEnabled = dataStore.createValue("sync.foreground.enabled", true)

    val showDashboardCard = dataStore.createValue("sync.dashboard.card.show", true)

    private val legacyPausedConnectors = dataStore.createSetValue<ConnectorId>(
        "sync.connectors.paused",
        emptySet(),
        json,
    )

    private val storedConnectorPauseStates = dataStore.createSetValue<ConnectorPauseState>(
        "sync.connectors.paused.states",
        emptySet(),
        json,
        onErrorFallbackToDefault = true,
    )

    val connectorPauseStates: Flow<Set<ConnectorPauseState>> = combine(
        storedConnectorPauseStates.flow,
        legacyPausedConnectors.flow,
    ) { storedStates, legacyIds ->
        val storedIds = storedStates.connectorIds
        storedStates + legacyIds
            .filterNot { it in storedIds }
            .map { ConnectorPauseState(connectorId = it, reason = ConnectorPauseReason.Manual) }
    }.distinctUntilChanged()

    val pausedConnectorIds: Flow<Set<ConnectorId>> = connectorPauseStates
        .map { it.connectorIds }
        .distinctUntilChanged()

    val clockSkewThreshold = dataStore.createValue("sync.clock.skew.threshold", 2.minutes, json)

    suspend fun migrateLegacyPauseStates() {
        val legacyIds = legacyPausedConnectors.value()
        if (legacyIds.isEmpty()) return

        storedConnectorPauseStates.update { storedStates ->
            val storedIds = storedStates.connectorIds
            storedStates + legacyIds
                .filterNot { it in storedIds }
                .map { ConnectorPauseState(connectorId = it, reason = ConnectorPauseReason.Manual) }
        }
        legacyPausedConnectors.update { emptySet() }
    }

    suspend fun pauseReason(connectorId: ConnectorId): ConnectorPauseReason? =
        connectorPauseStates.first().reasonFor(connectorId)

    suspend fun isPaused(connectorId: ConnectorId): Boolean = pauseReason(connectorId) != null

    suspend fun pauseConnector(connectorId: ConnectorId, reason: ConnectorPauseReason) {
        storedConnectorPauseStates.update { storedStates ->
            storedStates
                .filterNot { it.connectorId == connectorId }
                .toSet() + ConnectorPauseState(connectorId = connectorId, reason = reason)
        }
        legacyPausedConnectors.update { it - connectorId }
    }

    suspend fun resumeConnector(connectorId: ConnectorId) {
        storedConnectorPauseStates.update { storedStates ->
            storedStates.filterNot { it.connectorId == connectorId }.toSet()
        }
        legacyPausedConnectors.update { it - connectorId }
    }

    val deviceId by lazy {
        val key = stringPreferencesKey("sync.identifier.device")
        val rawId = runBlocking {
            dataStore.edit { prefs ->
                prefs[key] ?: kotlin.run {
                    UUID.randomUUID().toString().also {
                        prefs[key] = it
                    }
                }
            }[key]!!
        }
        DeviceId(rawId).also { log(TAG, INFO) { "Our DeviceId is $it" } }
    }

    companion object {
        internal val TAG = logTag("Sync", "Settings")
        val FIRST_SYNC_GRACE_PERIOD: Duration = 5.minutes
    }
}
