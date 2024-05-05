package eu.darken.octi.syncs.kserver.ui.add

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncAddNewKserverFragmentBinding
import eu.darken.octi.syncs.kserver.core.KServer
import javax.inject.Inject


@AndroidEntryPoint
class AddKServerFragment : Fragment3(R.layout.sync_add_new_kserver_fragment) {

    override val vm: AddKServerVM by viewModels()
    override val ui: SyncAddNewKserverFragmentBinding by viewBinding()
    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_info -> {
                        showAbout()
                        true
                    }

                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        ui.serverGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.server_kserver_prod_item -> vm.selectType(KServer.Official.PROD)
                R.id.server_kserver_beta_item -> vm.selectType(KServer.Official.BETA)
                R.id.server_kserver_dev_item -> vm.selectType(KServer.Official.DEV)
                R.id.server_kserver_local_item -> vm.selectType(KServer.Official.LOCAL)
            }
        }
        ui.serverKserverProdItem.apply {
            text = "${KServer.Official.PROD.address.domain} (Production)"
        }
        ui.serverKserverBetaItem.apply {
            text = "${KServer.Official.BETA.address.domain} (Beta)"
            isGone = BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE
        }
        ui.serverKserverDevItem.apply {
            text = "${KServer.Official.DEV.address.domain} (dev)"
            isGone = !BuildConfigWrap.DEBUG
        }
        ui.serverKserverLocalItem.apply {
            text = "${KServer.Official.LOCAL.address.domain} (local)"
            isGone = !BuildConfigWrap.DEBUG
        }

        vm.state.observe2(ui) { state ->
            when (state.serverType) {
                KServer.Official.PROD -> serverGroup.check(R.id.server_kserver_prod_item)
                KServer.Official.BETA -> serverGroup.check(R.id.server_kserver_beta_item)
                KServer.Official.DEV -> serverGroup.check(R.id.server_kserver_dev_item)
                KServer.Official.LOCAL -> serverGroup.check(R.id.server_kserver_local_item)
            }
            createNewAccount.isEnabled = !state.isBusy
            linkExistingAccount.isEnabled = !state.isBusy
            serverGroup.isEnabled = !state.isBusy
        }

        ui.createNewAccount.setOnClickListener { vm.createAccount() }
        ui.linkExistingAccount.setOnClickListener { vm.linkAccount() }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun showAbout() = MaterialAlertDialogBuilder(requireContext()).apply {
        setTitle(R.string.sync_kserver_about_title)
        setMessage(R.string.sync_kserver_about_desc)
        setPositiveButton(R.string.general_gotit_action) { _, _ ->

        }
        setNeutralButton(R.string.sync_kserver_about_source_action) { _, _ ->
            webpageTool.open("https://github.com/d4rken/octi-sync-server-kotlin")
        }
    }.show()
}
