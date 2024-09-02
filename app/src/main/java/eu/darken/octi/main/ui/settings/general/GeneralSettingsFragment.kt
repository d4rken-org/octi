package eu.darken.octi.main.ui.settings.general

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.MainDirections
import eu.darken.octi.R
import eu.darken.octi.common.preferences.ListPreference2
import eu.darken.octi.common.preferences.setupWithEnum
import eu.darken.octi.common.uix.PreferenceFragment3
import eu.darken.octi.main.core.GeneralSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class GeneralSettingsFragment : PreferenceFragment3() {

    override val vm: GeneralSettingsFragmentVM by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings

    override val settings: GeneralSettings by lazy { generalSettings }
    override val preferenceFile: Int = R.xml.preferences_general

    private val themeModePref: ListPreference2
        get() = findPreference(settings.themeMode.keyName)!!
    private val themeStylePref: ListPreference2
        get() = findPreference(settings.themeStyle.keyName)!!

    override fun onPreferencesCreated() {

        themeModePref.setupWithEnum(settings.themeMode)
        themeStylePref.setupWithEnum(settings.themeStyle)

        super.onPreferencesCreated()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2 { state ->
            themeModePref.alternativeClickListener = when {
                state.isPro -> null

                else -> {
                    {
                        MainDirections.goToUpgradeFragment().navigate()
                    }
                }
            }
            themeStylePref.alternativeClickListener = when {
                state.isPro -> null

                else -> {
                    {
                        MainDirections.goToUpgradeFragment().navigate()
                    }
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }
}