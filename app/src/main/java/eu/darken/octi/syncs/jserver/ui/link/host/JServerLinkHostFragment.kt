package eu.darken.octi.syncs.jserver.ui.link.host

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.error.asErrorDialogBuilder
import eu.darken.octi.common.navigation.popBackStack
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncJserverLinkHostFragmentBinding
import eu.darken.octi.syncs.jserver.ui.link.JServerLinkOption


@AndroidEntryPoint
class JServerLinkHostFragment : Fragment3(R.layout.sync_jserver_link_host_fragment) {

    override val vm: JServerLinkHostVM by viewModels()
    override val ui: SyncJserverLinkHostFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
        }

        ui.linkOptions.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.link_option_direct -> vm.onLinkOptionSelected(JServerLinkOption.DIRECT)
                R.id.link_option_qrcode -> vm.onLinkOptionSelected(JServerLinkOption.QRCODE)
                R.id.link_option_nfc -> vm.onLinkOptionSelected(JServerLinkOption.NFC)
            }
        }

        ui.linkCodeInputAction.setOnClickListener { vm.shareLinkCode(requireActivity()) }

        vm.state.observe2(ui) { state ->
            linkContainerDirect.isGone = state.linkOption != JServerLinkOption.DIRECT
            linkContainerQrcode.isGone = state.linkOption != JServerLinkOption.QRCODE
            linkContainerNfc.isGone = state.linkOption != JServerLinkOption.NFC

            when (state.linkOption) {
                JServerLinkOption.DIRECT -> {
                    linkOptions.check(R.id.link_option_direct)
                    linkCodeActual.text = state.encodedLinkCode
                }

                JServerLinkOption.QRCODE -> {
                    linkOptions.check(R.id.link_option_qrcode)
                    try {
                        val size = ui.root.width
                        val qrcode = state.encodedLinkCode?.let {
                            BarcodeEncoder().encodeBitmap(it, BarcodeFormat.QR_CODE, size, size)
                        }
                        qrcodeImage.setImageBitmap(qrcode)
                    } catch (e: Exception) {
                        e.asErrorDialogBuilder(requireContext()).show()
                    }
                }

                JServerLinkOption.NFC -> {
                    linkOptions.check(R.id.link_option_nfc)
                    // TODO NOOP?
                }
            }
        }

        vm.autoNavOnNewDevice.observe2(ui) {
            Toast.makeText(
                requireActivity(),
                R.string.sync_jserver_link_host_device_linked_message,
                Toast.LENGTH_LONG
            ).show()
            popBackStack()
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
