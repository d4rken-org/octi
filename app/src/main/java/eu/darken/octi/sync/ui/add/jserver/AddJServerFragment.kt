package eu.darken.octi.sync.ui.add.jserver

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncAddNewJserverFragmentBinding
import eu.darken.octi.sync.core.provider.jserver.JServer

@AndroidEntryPoint
class AddJServerFragment : Fragment3(R.layout.sync_add_new_jserver_fragment) {

    override val vm: AddJServerVM by viewModels()
    override val ui: SyncAddNewJserverFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.setupWithNavController(findNavController())

        ui.serverGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.server_jserver_grylls_item -> vm.selectType(JServer.Official.GRYLLS)
                R.id.server_jserver_dev_item -> vm.selectType(JServer.Official.DEV)
            }
        }
        ui.serverJserverGryllsItem.text = "${JServer.Official.GRYLLS.address} (prod)"
        ui.serverJserverDevItem.apply {
            text = "${JServer.Official.DEV.address} (dev)"
            isGone = BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE
        }

        vm.state.observe2(ui) { state ->
            when (state.serverType) {
                JServer.Official.GRYLLS -> serverGroup.check(R.id.server_jserver_grylls_item)
                JServer.Official.DEV -> serverGroup.check(R.id.server_jserver_dev_item)
            }
            createNewAccount.isEnabled = !state.isBusy
            linkExistingAccount.isEnabled = !state.isBusy
            serverGroup.isEnabled = !state.isBusy
        }

        ui.createNewAccount.setOnClickListener { vm.createAccount() }
        ui.linkExistingAccount.setOnClickListener {
            // TODO
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
