package eu.darken.octi.modules.connectivity.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceDataStore
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
class ConnectivitySettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) : PreferenceScreenData, ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_connectivity_settings")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.connectivity.enabled", true)

    override val mapper: PreferenceDataStore = PreferenceStoreMapper(
        isEnabled
    )

    companion object {
        internal val TAG = logTag("Module", "Connectivity", "Settings")
    }
}
