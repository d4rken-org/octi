package eu.darken.octi.sync.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.preference.Preference
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings.backgroundSyncEnabled.flow.asLiveData().observe2 {
            findPreference<Preference>(settings.backgroundSyncInterval.keyName)?.isEnabled = it
            findPreference<Preference>(settings.backgroundSyncOnMobile.keyName)?.isEnabled = it
        }
        super.onViewCreated(view, savedInstanceState)
    }

}