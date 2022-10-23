package eu.darken.octi.modules.apps.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.preferences.PreferenceStoreMapper
import eu.darken.octi.common.preferences.Settings
import eu.darken.octi.common.preferences.createFlowPreference
import eu.darken.octi.module.core.ModuleSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) : Settings(), ModuleSettings {

    override val preferences: SharedPreferences =
        context.getSharedPreferences("module_apps_settings", Context.MODE_PRIVATE)

    override val isEnabled = preferences.createFlowPreference("module.apps.enabled", true)

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        isEnabled
    )

    companion object {
        internal val TAG = logTag("Module", "Apps", "Settings")
    }
}