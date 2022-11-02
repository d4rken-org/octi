package eu.darken.octi.main.ui.settings.general

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.datastore.valueBlocking
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

    private val themePref by lazy { findPreference<ListPreference>(settings.themeType.keyName)!! }

    override fun onPreferencesCreated() {
        themePref.apply {
            entries = ThemeType.values().map { getString(it.labelRes) }.toTypedArray()
            entryValues = ThemeType.values().map { it.name }.toTypedArray()
            setSummary(ThemeType.valueOf(settings.themeType.valueBlocking).labelRes)
        }
        findPreference<Preference>("debug.bugreport.automatic.enabled")?.isEnabled =
            BuildConfigWrap.FLAVOR != BuildConfigWrap.Flavor.FOSS
        super.onPreferencesCreated()
    }

    override fun onPreferencesChanged() {
        themePref.setSummary(ThemeType.valueOf(settings.themeType.valueBlocking).labelRes)
        super.onPreferencesChanged()
    }

}