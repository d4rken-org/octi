package eu.darken.octi.main.ui.onboarding.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.OnboardingFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class OnboardingFragment : Fragment3(R.layout.onboarding_fragment) {

    override val vm: OnboardingFragmentVM by viewModels()
    override val ui: OnboardingFragmentBinding by viewBinding()

    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.goPrivacyPolicy.setOnClickListener { webpageTool.open(PrivacyPolicy.URL) }
        ui.continueAction.setOnClickListener { vm.finishOnboarding() }
        super.onViewCreated(view, savedInstanceState)
    }
}
