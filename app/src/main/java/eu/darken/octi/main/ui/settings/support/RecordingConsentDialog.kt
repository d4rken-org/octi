package eu.darken.octi.main.ui.settings.support

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.R as CommonR

@Composable
fun RecordingConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.support_debuglog_label)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.settings_debuglog_explanation))
                TextButton(onClick = onPrivacyPolicy) {
                    Text(text = stringResource(R.string.settings_privacy_policy_label))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(CommonR.string.general_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}

@Preview2
@Composable
private fun RecordingConsentDialogPreview() = PreviewWrapper {
    RecordingConsentDialog(
        onConfirm = {},
        onDismiss = {},
        onPrivacyPolicy = {},
    )
}
