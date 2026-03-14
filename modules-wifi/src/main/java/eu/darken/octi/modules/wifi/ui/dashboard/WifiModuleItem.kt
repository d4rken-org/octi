package eu.darken.octi.modules.wifi.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.modules.wifi.R as WifiR
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.ui.receptIcon

data class WifiDashState(
    val info: WifiInfo,
    val showPermissionAction: Boolean,
)

@Composable
fun WifiModuleItem(
    state: WifiDashState,
    onDetailClicked: () -> Unit,
    onGrantPermission: (Permission) -> Unit,
) {
    val wifi = state.info
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = wifi.receptIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val freqText = when (wifi.currentWifi?.freqType) {
                WifiInfo.Wifi.Type.FIVE_GHZ -> "5 Ghz"
                WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ -> "2.4 Ghz"
                else -> stringResource(CommonR.string.general_na_label)
            }
            val sig = wifi.currentWifi?.reception ?: 0f
            val reception = when {
                sig > 0.65f -> stringResource(WifiR.string.module_wifi_reception_good_label)
                sig > 0.3f -> stringResource(WifiR.string.module_wifi_reception_okay_label)
                sig > 0.0f -> stringResource(WifiR.string.module_wifi_reception_bad_label)
                else -> stringResource(CommonR.string.general_na_label)
            }
            Text(
                text = "$freqText - $reception",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = wifi.currentWifi?.ssid ?: stringResource(WifiR.string.module_wifi_unknown_ssid_label),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.showPermissionAction) {
            IconButton(
                onClick = { onGrantPermission(Permission.ACCESS_FINE_LOCATION) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun WifiModuleItemPreview() = PreviewWrapper {
    WifiModuleItem(
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
