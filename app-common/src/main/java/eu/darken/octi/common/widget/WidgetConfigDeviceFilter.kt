package eu.darken.octi.common.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper

data class WidgetConfigDevice(
    val id: String,
    val label: String,
    val subtitle: String?,
    val icon: ImageVector,
)

@Composable
fun WidgetDeviceFilterCard(
    devices: List<WidgetConfigDevice>,
    selectedDeviceIds: Set<String>,
    onDeviceToggle: (id: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.widget_config_devices_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val hintRes = when {
                devices.isEmpty() -> R.string.widget_config_devices_none_known
                selectedDeviceIds.isEmpty() -> R.string.widget_config_devices_empty_hint
                else -> R.string.widget_config_devices_selected_hint
            }
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (devices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                devices.forEach { device ->
                    WidgetDeviceFilterRow(
                        device = device,
                        checked = device.id in selectedDeviceIds,
                        onCheckedChange = { checked -> onDeviceToggle(device.id, checked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetDeviceFilterRow(
    device: WidgetConfigDevice,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = device.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = device.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (device.subtitle != null) {
                Text(
                    text = device.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

internal fun widgetConfigPreviewDevices() = listOf(
    WidgetConfigDevice(
        id = "pixel-9-pro",
        label = "Pixel 9 Pro",
        subtitle = "Last seen 2m ago",
        icon = Icons.TwoTone.PhoneAndroid,
    ),
    WidgetConfigDevice(
        id = "pixel-tablet",
        label = "Pixel Tablet",
        subtitle = "Last seen 8m ago",
        icon = Icons.TwoTone.Tablet,
    ),
)

@Preview2
@Composable
private fun WidgetDeviceFilterCardPreview() = PreviewWrapper {
    WidgetDeviceFilterCard(
        devices = widgetConfigPreviewDevices(),
        selectedDeviceIds = setOf("pixel-9-pro"),
        onDeviceToggle = { _, _ -> },
        modifier = Modifier.padding(16.dp),
    )
}

@Preview2
@Composable
private fun WidgetDeviceFilterCardEmptyPreview() = PreviewWrapper {
    WidgetDeviceFilterCard(
        devices = emptyList(),
        selectedDeviceIds = emptySet(),
        onDeviceToggle = { _, _ -> },
        modifier = Modifier.padding(16.dp),
    )
}
