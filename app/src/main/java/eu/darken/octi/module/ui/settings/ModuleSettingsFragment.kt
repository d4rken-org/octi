package eu.darken.octi.module.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.uix.PreferenceFragment3
import eu.darken.octi.module.core.GeneralModuleSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class ModuleSettingsFragment : PreferenceFragment3() {

    override val vm: ModuleSettingsVM by viewModels()

    @Inject lateinit var _settings: GeneralModuleSettings
    override val settings: GeneralModuleSettings by lazy { _settings }
    override val preferenceFile: Int = R.xml.preferences_module

}