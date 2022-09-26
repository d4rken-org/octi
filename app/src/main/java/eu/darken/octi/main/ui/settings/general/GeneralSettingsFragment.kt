package eu.darken.octi.main.ui.settings.general

import android.content.SharedPreferences
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.uix.PreferenceFragment3
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.ThemeType
import eu.darken.octi.main.core.labelRes
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class GeneralSettingsFragment : PreferenceFragment3() {

    override val vm: GeneralSettingsFragmentVM by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings

    override val settings: GeneralSettings by lazy { generalSettings }
    override val preferenceFile: Int = R.xml.preferences_general

    private val themePref by lazy { findPreference<ListPreference>(settings.themeType.key)!! }

    override fun onPreferencesCreated() {
        themePref.apply {
            entries = ThemeType.values().map { getString(it.labelRes) }.toTypedArray()
            entryValues = ThemeType.values().map { it.name }.toTypedArray()
            setSummary(ThemeType.valueOf(settings.themeType.value).labelRes)
        }
        super.onPreferencesCreated()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            settings.themeType.key -> {
//                settings.themeType.value = ThemeType.valueOf(key)
                themePref.setSummary(ThemeType.valueOf(settings.themeType.value).labelRes)
            }
        }
        super.onSharedPreferenceChanged(sharedPreferences, key)
    }
}