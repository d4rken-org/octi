package eu.darken.octi.sync.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.uix.PreferenceFragment3
import eu.darken.octi.sync.core.SyncSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SyncSettingsFragment : PreferenceFragment3() {

    override val vm: SyncSettingsVM by viewModels()

    @Inject lateinit var _syncSettings: SyncSettings
    override val settings: SyncSettings by lazy { _syncSettings }
    override val preferenceFile: Int = R.xml.preferences_sync

}