package eu.darken.octi.sync.ui.add.gdrive

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncAddNewGdriveAppdataFragmentBinding

@AndroidEntryPoint
class AddGDriveFragment : Fragment3(R.layout.sync_add_new_gdrive_appdata_fragment) {

    override val vm: AddGDriveVM by viewModels()
    override val ui: SyncAddNewGdriveAppdataFragmentBinding by viewBinding()

    private val googleSignInHandler = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        vm.onGoogleSignIn(result)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.setupWithNavController(findNavController())
        ui.signInAction.setOnClickListener { vm.startSignIn() }

        vm.events.observe2(ui) {
            when (it) {
                is AddGDriveEvents.SignInStart -> {
                    googleSignInHandler.launch(it.intent)
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
