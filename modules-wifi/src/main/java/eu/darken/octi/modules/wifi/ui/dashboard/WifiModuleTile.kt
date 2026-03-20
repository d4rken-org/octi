package eu.darken.octi.modules.wifi.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
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
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.ui.receptIcon
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.modules.wifi.R as WifiR

@Composable
fun WifiModuleTile(
    state: WifiDashState,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onDetailClicked: () -> Unit,
    onGrantPermission: (Permission) -> Unit,
) {
    val wifi = state.info
    val freqText = when (wifi.currentWifi?.freqType) {
        WifiInfo.Wifi.Type.FIVE_GHZ -> "5 GHz"
        WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ -> "2.4 GHz"
        else -> stringResource(CommonR.string.general_na_label)
    }
    val sig = wifi.currentWifi?.reception ?: 0f
    val receptionText = when {
        sig > 0.65f -> stringResource(WifiR.string.module_wifi_reception_good_label)
        sig > 0.3f -> stringResource(WifiR.string.module_wifi_reception_okay_label)
        sig > 0.0f -> stringResource(WifiR.string.module_wifi_reception_bad_label)
        else -> stringResource(CommonR.string.general_na_label)
    }
    val ssid = wifi.currentWifi?.ssid ?: stringResource(WifiR.string.module_wifi_unknown_ssid_label)

    val tileColor = if (isWide) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val tileDescription = "$freqText $receptionText $ssid"

    Surface(
        onClick = onDetailClicked,
        modifier = modifier.semantics { contentDescription = tileDescription },
        shape = RoundedCornerShape(12.dp),
        color = tileColor,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = wifi.receptIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = freqText,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (state.showPermissionAction) {
                    IconButton(
                        onClick = { onGrantPermission(Permission.ACCESS_FINE_LOCATION) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalBars(reception = sig)
                Text(
                    text = receptionText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = ssid,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SignalBars(
    reception: Float,
    modifier: Modifier = Modifier,
) {
    val barCount = 4
    val filledBars = when {
        reception > 0.75f -> 4
        reception > 0.5f -> 3
        reception > 0.25f -> 2
        reception > 0.0f -> 1
        else -> 0
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0 until barCount) {
            val barHeight = (4 + i * 3).dp
            Surface(
                modifier = Modifier
                    .size(width = 4.dp, height = barHeight),
                shape = RoundedCornerShape(1.dp),
                color = if (i < filledBars) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ) {}
        }
    }
}

@Preview2
@Composable
private fun WifiModuleTilePreview() = PreviewWrapper {
    WifiModuleTile(
        state = WifiDashState(
            info = WifiInfo(
                currentWifi = WifiInfo.Wifi(
                    ssid = "HomeNetwork",
                    reception = 0.8f,
                    freqType = WifiInfo.Wifi.Type.FIVE_GHZ,
                ),
            ),
            showPermissionAction = false,
        ),
        onDetailClicked = {},
        onGrantPermission = {},
    )
}
