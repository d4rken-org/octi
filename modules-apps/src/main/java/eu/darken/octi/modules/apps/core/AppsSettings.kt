package eu.darken.octi.modules.apps.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleSettings
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_apps_settings")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.apps.enabled", true)

    val includeInstaller = dataStore.createValue("module.apps.include.installer", false)

    val sortMode = dataStore.createValue("module.apps.sort.mode", AppsSortMode.NAME, json)

    companion object {
        internal val TAG = logTag("Module", "Apps", "Settings")
    }
}
