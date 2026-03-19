package eu.darken.octi.modules.clipboard.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.R as ClipboardR
import okio.ByteString.Companion.encodeUtf8

@Composable
fun ClipboardModuleTile(
    state: ClipboardDashState,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onDetailClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val clip = state.info
    val clipText = when (clip.type) {
        ClipboardInfo.Type.EMPTY -> stringResource(CommonR.string.general_empty_label)
        ClipboardInfo.Type.SIMPLE_TEXT -> clip.data.utf8()
    }
    val copiedToOctiMsg = stringResource(ClipboardR.string.module_clipboard_copied_os_to_octi)
    val copiedToOsMsg = stringResource(ClipboardR.string.module_clipboard_copied_octi_to_os)

    val tileColor = if (isWide) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val tileDescription = stringResource(ClipboardR.string.module_clipboard_label)

    Surface(
        onClick = onDetailClicked,
        modifier = modifier.semantics { contentDescription = tileDescription },
        shape = RoundedCornerShape(12.dp),
        color = tileColor,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (state.isOurDevice) {
                    IconButton(
                        onClick = {
                            onShareClicked()
                            showMessage(copiedToOctiMsg)
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onCopyClicked()
                        showMessage(copiedToOsMsg)
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\"$clipText\"",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview2
@Composable
private fun ClipboardModuleTilePreview() = PreviewWrapper {
    ClipboardModuleTile(
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
