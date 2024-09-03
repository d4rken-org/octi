package eu.darken.octi.main.ui.onboarding.welcome

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.OnboardingWelcomeFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class WelcomeFragment : Fragment3(R.layout.onboarding_welcome_fragment) {

    override val vm: WelcomeFragmentVM by viewModels()
    override val ui: OnboardingWelcomeFragmentBinding by viewBinding()

    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.continueAction.setOnClickListener { vm.finishScreen() }
        ui.betaHint.isVisible = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE
        super.onViewCreated(view, savedInstanceState)
    }
}
