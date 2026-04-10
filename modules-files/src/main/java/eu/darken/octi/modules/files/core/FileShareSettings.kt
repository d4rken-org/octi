package eu.darken.octi.modules.files.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.module.core.ModuleSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileShareSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_files_settings")
    val dataStore: DataStore<Preferences> get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.files.enabled", true)
}
