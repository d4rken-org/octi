package eu.darken.octi.modules.clipboard.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.uix.BottomSheetDialogFragment2
import eu.darken.octi.databinding.ClipboardDetailSheetBinding
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.R as ClipboardR

@AndroidEntryPoint
class ClipboardDetailFragment : BottomSheetDialogFragment2() {

    override val vm: ClipboardDetailVM by viewModels()
    override lateinit var ui: ClipboardDetailSheetBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = ClipboardDetailSheetBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.copyAction.setOnClickListener {
            vm.copyToClipboard()
            Toast.makeText(
                requireContext(),
                ClipboardR.string.module_clipboard_copied_octi_to_os,
                Toast.LENGTH_SHORT,
            ).show()
        }

        vm.state.observe2(ui) { state ->
            val clip = state.clipboardData?.data ?: return@observe2

            typeValue.text = when (clip.type) {
                ClipboardInfo.Type.EMPTY -> getString(ClipboardR.string.module_clipboard_detail_type_empty)
                ClipboardInfo.Type.SIMPLE_TEXT -> getString(ClipboardR.string.module_clipboard_detail_type_text)
            }

            contentValue.text = when (clip.type) {
                ClipboardInfo.Type.EMPTY -> ""
                ClipboardInfo.Type.SIMPLE_TEXT -> clip.data.utf8()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
