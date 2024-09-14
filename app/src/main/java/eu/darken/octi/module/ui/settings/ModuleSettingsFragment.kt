package eu.darken.octi.module.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.CheckBoxPreference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.MainDirections
import eu.darken.octi.R
import eu.darken.octi.common.uix.PreferenceFragment3
import eu.darken.octi.module.core.GeneralModuleSettings
import eu.darken.octi.modules.apps.core.AppsSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class ModuleSettingsFragment : PreferenceFragment3() {

    override val vm: ModuleSettingsVM by viewModels()

    @Inject lateinit var _settings: GeneralModuleSettings
    @Inject lateinit var appsSettings: AppsSettings
    override val settings: GeneralModuleSettings by lazy { _settings }
    override val preferenceFile: Int = R.xml.preferences_module

    private val moduleAppsIncludeInstaller: CheckBoxPreference
        get() = findPreference("module.apps.include.installer")!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        vm.state.observe2 { state ->
            moduleAppsIncludeInstaller.apply {
                isEnabled = state.isAppModuleEnabled
                setOnPreferenceClickListener {
                    if (!state.isPro) {
                        isChecked = false
                        MainDirections.goToUpgradeFragment().navigate()
                        true
                    } else {
                        false
                    }
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}