package eu.darken.octi.modules.clipboard

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
class ClipboardSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_clipboard_settings")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.clipboard.enabled", true)

    val lastClipboard = dataStore.createValue("module.clipboard.last", null as String?)

    companion object {
        internal val TAG = logTag("Module", "Clipboard", "Settings")
    }
}
