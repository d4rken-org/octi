package eu.darken.octi.common.compose

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R as CommonR

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun CopyableDetailRow(label: String, value: String, copyable: Boolean, showMessage: (String) -> Unit) {
    val context = LocalContext.current
    val copiedMsg = stringResource(CommonR.string.general_copy_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
        )
        if (copyable) {
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("IP", value))
                    showMessage(copiedMsg)
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.ContentCopy,
                    contentDescription = stringResource(CommonR.string.general_copy_action),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun DetailRowPreview() = PreviewWrapper {
    DetailRow(label = "Status", value = "Discharging")
}

@Preview2
@Composable
private fun CopyableDetailRowPreview() = PreviewWrapper {
    CopyableDetailRow(label = "IP Address", value = "192.168.1.100", copyable = true, showMessage = {})
}
