package eu.darken.octi.modules.meta.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_meta_settings")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.meta.enabled", true)

    companion object {
        internal val TAG = logTag("Module", "Meta", "Settings")
    }
}