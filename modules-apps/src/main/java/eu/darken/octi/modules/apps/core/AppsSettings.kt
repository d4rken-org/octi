package eu.darken.octi.modules.apps.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.PreferenceScreenData
import eu.darken.octi.common.datastore.PreferenceStoreMapper
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) : PreferenceScreenData, ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_apps_settings")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.apps.enabled", true)

    val includeInstaller = dataStore.createValue("module.apps.include.installer", false)

    val sortMode = dataStore.createValue("module.apps.sort.mode", AppsSortMode.NAME, moshi)

    override val mapper: PreferenceStoreMapper = PreferenceStoreMapper(
        isEnabled,
        includeInstaller,
        sortMode,
    )

    companion object {
        internal val TAG = logTag("Module", "Apps", "Settings")
    }
}