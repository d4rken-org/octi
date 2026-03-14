package eu.darken.octi.modules.connectivity.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.ui.icon

@Composable
fun ConnectivityModuleItem(
    info: ConnectivityInfo,
    onDetailClicked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = info.connectionType.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val typeLabel = when (info.connectionType) {
                ConnectivityInfo.ConnectionType.WIFI -> stringResource(ConnectivityR.string.module_connectivity_type_wifi_label)
                ConnectivityInfo.ConnectionType.CELLULAR -> stringResource(ConnectivityR.string.module_connectivity_type_cellular_label)
                ConnectivityInfo.ConnectionType.ETHERNET -> stringResource(ConnectivityR.string.module_connectivity_type_ethernet_label)
                ConnectivityInfo.ConnectionType.NONE, null -> stringResource(ConnectivityR.string.module_connectivity_type_none_label)
            }
            Text(
                text = "${stringResource(ConnectivityR.string.module_connectivity_detail_connection_type_label)}: $typeLabel",
                style = MaterialTheme.typography.bodyMedium,
            )
            val localIp = info.localAddressIpv4 ?: stringResource(ConnectivityR.string.module_connectivity_unknown_local_ip_label)
            val publicIp = info.publicIp ?: stringResource(ConnectivityR.string.module_connectivity_unknown_public_ip_label)
            Text(
                text = "$localIp - $publicIp",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview2
@Composable
private fun ConnectivityModuleItemPreview() = PreviewWrapper {
    ConnectivityModuleItem(
        info = ConnectivityInfo(
            connectionType = ConnectivityInfo.ConnectionType.WIFI,
            publicIp = "203.0.113.42",
            localAddressIpv4 = "192.168.1.100",
            localAddressIpv6 = null,
            gatewayIp = "192.168.1.1",
            dnsServers = listOf("8.8.8.8"),
        ),
        onDetailClicked = {},
    )
}
