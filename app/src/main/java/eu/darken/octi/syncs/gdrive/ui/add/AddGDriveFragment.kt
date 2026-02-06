package eu.darken.octi.syncs.gdrive.ui.add

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.EdgeToEdgeHelper
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncAddNewGdriveFragmentBinding

@AndroidEntryPoint
class AddGDriveFragment : Fragment3(R.layout.sync_add_new_gdrive_fragment) {

    override val vm: AddGDriveVM by viewModels()
    override val ui: SyncAddNewGdriveFragmentBinding by viewBinding()

    private val googleSignInHandler = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        vm.onGoogleSignIn(result)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, bottom = true)
            insetsPadding(ui.toolbar, top = true, left = true, right = true)
        }

        ui.toolbar.setupWithNavController(findNavController())
        ui.signInAction.setOnClickListener { vm.startSignIn() }

        vm.events.observe2(ui) {
            when (it) {
                is AddGDriveEvents.SignInStart -> {
                    googleSignInHandler.launch(it.intent)
                }
                is AddGDriveEvents.NoGoogleAccount -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.sync_gdrive_error_no_account_on_device,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
