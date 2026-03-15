package eu.darken.octi.modules.clipboard.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.R as ClipboardR
import okio.ByteString.Companion.encodeUtf8

data class ClipboardDashState(
    val info: ClipboardInfo,
    val isOurDevice: Boolean,
)

@Composable
fun ClipboardModuleItem(
    state: ClipboardDashState,
    onDetailClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val clip = state.info
    val copiedToOctiMsg = stringResource(ClipboardR.string.module_clipboard_copied_os_to_octi)
    val copiedToOsMsg = stringResource(ClipboardR.string.module_clipboard_copied_octi_to_os)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.ContentPaste,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val clipText = when (clip.type) {
                ClipboardInfo.Type.EMPTY -> stringResource(CommonR.string.general_empty_label)
                ClipboardInfo.Type.SIMPLE_TEXT -> clip.data.utf8()
            }
            Text(
                text = "\"$clipText\"",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.isOurDevice) {
            IconButton(
                onClick = {
                    onShareClicked()
                    showMessage(copiedToOctiMsg)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(
            onClick = {
                onCopyClicked()
                showMessage(copiedToOsMsg)
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview2
@Composable
private fun ClipboardModuleItemPreview() = PreviewWrapper {
    ClipboardModuleItem(
        state = ClipboardDashState(
            info = ClipboardInfo(
                type = ClipboardInfo.Type.SIMPLE_TEXT,
                data = "Hello clipboard!".encodeUtf8(),
            ),
            isOurDevice = true,
        ),
        onDetailClicked = {},
        onClearClicked = {},
        onShareClicked = {},
        onCopyClicked = {},
        showMessage = {},
    )
}
