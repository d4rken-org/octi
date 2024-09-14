package eu.darken.octi.module.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.preference.CheckBoxPreference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.observe2
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

    private val moduleApps: CheckBoxPreference
        get() = findPreference("module.apps.enabled")!!

    private val moduleAppsIncludeInstaller: CheckBoxPreference
        get() = findPreference("module.apps.include.installer")!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appsSettings.isEnabled.flow.asLiveData().observe2(this) {
            moduleAppsIncludeInstaller.isEnabled = moduleApps.isChecked
        }
    }
}