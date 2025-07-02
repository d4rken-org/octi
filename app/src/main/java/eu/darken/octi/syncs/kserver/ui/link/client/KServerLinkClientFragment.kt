package eu.darken.octi.syncs.kserver.ui.link.client

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
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
import eu.darken.octi.common.EdgeToEdgeHelper
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncKserverLinkClientFragmentBinding
import eu.darken.octi.syncs.kserver.ui.link.KServerLinkOption


@AndroidEntryPoint
class KServerLinkClientFragment : Fragment3(R.layout.sync_kserver_link_client_fragment) {

    override val vm: KServerLinkClientVM by viewModels()
    override val ui: SyncKserverLinkClientFragmentBinding by viewBinding()

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents == null) {
            log(TAG) { "QRCode scan was cancelled." }
        } else {
            log(TAG) { "QRCode scanned: $result" }
            vm.onCodeEntered(result.contents)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true, bottom = true)
            insetsPadding(ui.toolbar, top = true)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
        }

        ui.linkOptions.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.link_option_direct -> vm.onLinkOptionSelected(KServerLinkOption.DIRECT)
                R.id.link_option_qrcode -> vm.onLinkOptionSelected(KServerLinkOption.QRCODE)
                R.id.link_option_nfc -> vm.onLinkOptionSelected(KServerLinkOption.NFC)
            }
        }

        ui.apply {
            linkCodeInputAction.setOnClickListener { vm.onCodeEntered(ui.linkCodeActual.text.toString()) }
            linkCodeActual.setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_GO -> {
                        vm.onCodeEntered(ui.linkCodeActual.text.toString())
                        true
                    }

                    else -> false
                }
            }
        }

        ui.linkQrcodeCameraAction.setOnClickListener {
            val options = ScanOptions().apply {
                setOrientationLocked(false)
            }
            barcodeLauncher.launch(options)
        }

        vm.state.observe2(ui) { state ->
            linkContainerDirect.isGone = state.linkOption != KServerLinkOption.DIRECT
            linkContainerQrcode.isGone = state.linkOption != KServerLinkOption.QRCODE
            linkContainerNfc.isGone = state.linkOption != KServerLinkOption.NFC

            when (state.linkOption) {
                KServerLinkOption.DIRECT -> {
                    linkOptions.check(R.id.link_option_direct)
                    linkCodeActual.text = null
                }

                KServerLinkOption.QRCODE -> {
                    linkOptions.check(R.id.link_option_qrcode)
                }

                KServerLinkOption.NFC -> {
                    linkOptions.check(R.id.link_option_nfc)
                    // TODO NOOP?
                }
            }
            busyContainer.isVisible = state.isBusy
        }
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Sync", "KServer", "Link", "Client", "Fragment")
    }
}
