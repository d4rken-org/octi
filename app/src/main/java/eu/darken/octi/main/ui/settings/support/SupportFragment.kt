package eu.darken.octi.main.ui.settings.support

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.ClipboardHelper
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.observe2
import eu.darken.octi.common.uix.PreferenceFragment2
import eu.darken.octi.main.core.GeneralSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SupportFragment : PreferenceFragment2() {

    private val vm: SupportFragmentVM by viewModels()

    override val preferenceFile: Int = R.xml.preferences_support
    @Inject lateinit var generalSettings: GeneralSettings

    override val settings: GeneralSettings by lazy { generalSettings }

    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var webpageTool: WebpageTool

    private val debugLogPref by lazy { findPreference<Preference>("support.debuglog")!! }

    override fun onPreferencesCreated() {
        debugLogPref.setOnPreferenceClickListener {
            vm.toggleDebugLog()
            true
        }
        super.onPreferencesCreated()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.isRecording.observe2(this) {
            debugLogPref.apply {
                if (it != null) {
                    setIcon(R.drawable.ic_bug_stop_24)
                    setTitle(R.string.support_debuglog_inprogress_label)
                    summary = it.path
                } else {
                    setIcon(R.drawable.ic_baseline_bug_report_24)
                    setTitle(R.string.support_debuglog_label)
                    setSummary(R.string.support_debuglog_desc)
                }
            }
        }

        vm.events.observe2(this) {
            when (it) {
                SupportEvent.DebugLogInfo -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.support_debuglog_label)
                    setMessage(R.string.settings_debuglog_explanation)
                    setPositiveButton(R.string.general_continue) { _, _ -> vm.toggleDebugLog(consent = true) }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.settings_privacy_policy_label) { _, _ -> webpageTool.open(PrivacyPolicy.URL) }
                }.show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}