package eu.darken.octi.modules.power.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.createSetValue
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleSettings
import eu.darken.octi.modules.power.core.alert.PowerAlertRule
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_power_settings")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.power.enabled", true)

    val chargedFullAt = dataStore.createValue<Instant?>("module.power.status.full.at", null, json)

    val alertRules = dataStore.createSetValue<PowerAlertRule>("module.power.alert.rules", emptySet(), json)

    val alertEvents = dataStore.createSetValue<PowerAlertRule.Event>("module.power.alert.events", emptySet(), json)

    companion object {
        internal val TAG = logTag("Module", "Power", "Settings")
    }
}
