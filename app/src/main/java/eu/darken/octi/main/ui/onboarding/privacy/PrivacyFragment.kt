package eu.darken.octi.main.ui.onboarding.privacy

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.setChecked2
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.OnboardingPrivacyFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class PrivacyFragment : Fragment3(R.layout.onboarding_privacy_fragment) {

    override val vm: PrivacyFragmentVM by viewModels()
    override val ui: OnboardingPrivacyFragmentBinding by viewBinding()

    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(ui) { state ->
            updateContainer.isGone = !state.isUpdateCheckSupported
            updateToggle.setChecked2(state.isUpdateCheckEnabled, false)
        }

        ui.updateContainer.setOnClickListener { vm.toggleUpdateCheck() }

        ui.goPrivacyPolicy.setOnClickListener { webpageTool.open(PrivacyPolicy.URL) }
        ui.continueAction.setOnClickListener { vm.finishScreen() }
        super.onViewCreated(view, savedInstanceState)
    }
}
