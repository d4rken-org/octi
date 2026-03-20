package eu.darken.octi.modules.clipboard.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.DetailRow
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.R as ClipboardR
import okio.ByteString.Companion.encodeUtf8

@Composable
fun ClipboardDetailSheet(
    info: ClipboardInfo,
    onDismiss: () -> Unit,
    onCopy: (ClipboardInfo) -> Unit,
    showMessage: (String) -> Unit,
) {
    val copiedMsg = stringResource(ClipboardR.string.module_clipboard_copied_octi_to_os)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ClipboardDetailContent(info, copiedMsg, onCopy, showMessage)
    }
}

@Composable
private fun ClipboardDetailContent(
    info: ClipboardInfo,
    copiedMsg: String = "Copied",
    onCopy: (ClipboardInfo) -> Unit = {},
    showMessage: (String) -> Unit = {},
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.TwoTone.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(ClipboardR.string.module_clipboard_label),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            label = stringResource(ClipboardR.string.module_clipboard_detail_type_label),
            value = when (info.type) {
                ClipboardInfo.Type.EMPTY -> stringResource(ClipboardR.string.module_clipboard_detail_type_empty)
                ClipboardInfo.Type.SIMPLE_TEXT -> stringResource(ClipboardR.string.module_clipboard_detail_type_text)
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(ClipboardR.string.module_clipboard_detail_content_label),
            style = MaterialTheme.typography.labelMedium,
        )
        val content = when (info.type) {
            ClipboardInfo.Type.EMPTY -> ""
            ClipboardInfo.Type.SIMPLE_TEXT -> info.data.utf8()
        }
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .verticalScroll(rememberScrollState()),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = {
                onCopy(info)
                showMessage(copiedMsg)
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(ClipboardR.string.module_clipboard_copy_action))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview2
@Composable
private fun ClipboardDetailContentPreview() = PreviewWrapper {
    ClipboardDetailContent(
        info = ClipboardInfo(
            type = ClipboardInfo.Type.SIMPLE_TEXT,
            data = "Hello clipboard content!".encodeUtf8(),
        ),
    )
}
