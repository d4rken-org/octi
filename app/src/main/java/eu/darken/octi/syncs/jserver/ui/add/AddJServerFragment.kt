package eu.darken.octi.syncs.jserver.ui.add

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
import eu.darken.octi.databinding.SyncAddNewJserverFragmentBinding
import eu.darken.octi.syncs.jserver.core.JServer
import javax.inject.Inject


@AndroidEntryPoint
class AddJServerFragment : Fragment3(R.layout.sync_add_new_jserver_fragment) {

    override val vm: AddJServerVM by viewModels()
    override val ui: SyncAddNewJserverFragmentBinding by viewBinding()
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
                R.id.server_jserver_prod_item -> vm.selectType(JServer.Official.PROD)
                R.id.server_jserver_grylls_item -> vm.selectType(JServer.Official.GRYLLS)
                R.id.server_jserver_dev_item -> vm.selectType(JServer.Official.DEV)
            }
        }
        ui.serverJserverProdItem.apply {
            text = "${JServer.Official.PROD.address.domain} (Production)"
        }
        ui.serverJserverGryllsItem.apply {
            text = "${JServer.Official.GRYLLS.address.domain} (Beta)"
            isGone = BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE
        }
        ui.serverJserverDevItem.apply {
            text = "${JServer.Official.DEV.address.domain} (dev)"
            isGone = !BuildConfigWrap.DEBUG
        }

        vm.state.observe2(ui) { state ->
            when (state.serverType) {
                JServer.Official.PROD -> serverGroup.check(R.id.server_jserver_prod_item)
                JServer.Official.GRYLLS -> serverGroup.check(R.id.server_jserver_grylls_item)
                JServer.Official.DEV -> serverGroup.check(R.id.server_jserver_dev_item)
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
        setTitle(R.string.sync_jserver_about_title)
        setMessage(R.string.sync_jserver_about_desc)
        setPositiveButton(R.string.general_gotit_action) { _, _ ->

        }
        setNegativeButton(R.string.sync_jserver_about_author_action) { _, _ ->
            webpageTool.open("https://www.linkedin.com/in/jakob-m%C3%B6ller/")
        }
        setNeutralButton(R.string.sync_jserver_about_source_action) { _, _ ->
            webpageTool.open("https://github.com/jakob-moeller-cloud/octi-sync-server")
        }
    }.show()
}
