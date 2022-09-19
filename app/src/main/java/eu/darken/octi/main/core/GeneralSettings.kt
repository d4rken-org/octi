package eu.darken.octi.main.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.preferences.PreferenceStoreMapper
import eu.darken.octi.common.preferences.Settings
import eu.darken.octi.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("core_settings", Context.MODE_PRIVATE)

    val isBugTrackingEnabled = preferences.createFlowPreference("core.bugtracking.enabled", true)

    val isWelcomeDismissed = preferences.createFlowPreference("onboarding.welcome.dismissed", false)

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        isBugTrackingEnabled
    )

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}