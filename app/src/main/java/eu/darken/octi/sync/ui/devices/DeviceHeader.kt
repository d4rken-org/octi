package eu.darken.octi.sync.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.octi.modules.meta.core.MetaInfo

@Composable
internal fun DeviceHeader(
    item: SyncDevicesVM.DeviceItem,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    iconSize: Dp = 28.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (item.metaInfo?.deviceType) {
                MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
                MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
                else -> Icons.TwoTone.QuestionMark
            },
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayLabel,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.metaInfo?.let { meta ->
                Text(
                    text = "${meta.deviceManufacturer} · Android ${meta.androidVersionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            item.serverVersion?.let { version ->
                Text(
                    text = version,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.serverPlatform?.let { platform ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (platform.lowercase()) {
                            "android" -> Icons.TwoTone.Android
                            else -> Icons.TwoTone.QuestionMark
                        },
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = platform.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
