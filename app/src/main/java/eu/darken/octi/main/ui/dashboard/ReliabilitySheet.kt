package eu.darken.octi.main.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR

@Composable
fun ReliabilitySheet(
    onGoToSettings: () -> Unit,
    onDismissPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.TwoTone.BatteryFull,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permission_reliability_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_reliability_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGoToSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.permission_reliability_settings_action))
            }
            TextButton(
                onClick = onDismissPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview2
@Composable
private fun ReliabilitySheetPreview() = PreviewWrapper {
    ReliabilitySheet(
        onGoToSettings = {},
        onDismissPermission = {},
        onDismiss = {},
    )
}
