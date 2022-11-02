package eu.darken.octi.common.debug.autoreport

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.datastore.createValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.dataStore by preferencesDataStore(name = "debug_settings")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val isAutoReportingEnabled = dataStore.createValue(
        "debug.bugreport.automatic.enabled",
        BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.GPLAY
    )

}