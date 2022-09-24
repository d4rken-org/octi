package eu.darken.octi.syncs.jserver.ui.link.client

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncJserverLinkClientFragmentBinding
import eu.darken.octi.syncs.jserver.ui.link.LinkOption


@AndroidEntryPoint
class JServerLinkClientFragment : Fragment3(R.layout.sync_jserver_link_client_fragment) {

    override val vm: JServerLinkClientVM by viewModels()
    override val ui: SyncJserverLinkClientFragmentBinding by viewBinding()

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents == null) {
            log(TAG) { "QRCode scan was cancelled." }
        } else {
            log(TAG) { "QRCode scanned: $result" }
            vm.onCodeEntered(result.contents)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
        }

        ui.linkOptions.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.link_option_direct -> vm.onLinkOptionSelected(LinkOption.DIRECT)
                R.id.link_option_qrcode -> vm.onLinkOptionSelected(LinkOption.QRCODE)
                R.id.link_option_nfc -> vm.onLinkOptionSelected(LinkOption.NFC)
            }
        }

        ui.linkCodeInputAction.setOnClickListener { vm.onCodeEntered(ui.linkCodeActual.text.toString()) }
        ui.linkQrcodeCameraAction.setOnClickListener {
            val options = ScanOptions().apply {
                setOrientationLocked(false)
            }
            barcodeLauncher.launch(options)
        }

        vm.state.observe2(ui) { state ->
            linkContainerDirect.isGone = state.linkOption != LinkOption.DIRECT
            linkContainerQrcode.isGone = state.linkOption != LinkOption.QRCODE
            linkContainerNfc.isGone = state.linkOption != LinkOption.NFC

            when (state.linkOption) {
                LinkOption.DIRECT -> {
                    linkOptions.check(R.id.link_option_direct)
                    linkCodeActual.text = null
                }
                LinkOption.QRCODE -> {
                    linkOptions.check(R.id.link_option_qrcode)
                }
                LinkOption.NFC -> {
                    linkOptions.check(R.id.link_option_nfc)
                    // TODO NOOP?
                }
            }
            busyContainer.isVisible = state.isBusy
        }
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Link", "Client", "Fragment")
    }
}
