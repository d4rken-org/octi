package eu.darken.octi.modules.connectivity.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.CopyableDetailRow
import eu.darken.octi.common.compose.DetailRow
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.ui.icon

@Composable
fun ConnectivityDetailSheet(
    info: ConnectivityInfo,
    onDismiss: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val unknownLocal = stringResource(ConnectivityR.string.module_connectivity_unknown_local_ip_label)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ConnectivityDetailContent(info, unknownLocal, showMessage)
    }
}

@Composable
private fun ConnectivityDetailContent(
    info: ConnectivityInfo,
    unknownLocal: String = "Unknown",
    showMessage: (String) -> Unit = {},
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = info.connectionType.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(ConnectivityR.string.module_connectivity_label),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            label = stringResource(ConnectivityR.string.module_connectivity_detail_connection_type_label),
            value = when (info.connectionType) {
                ConnectivityInfo.ConnectionType.WIFI -> stringResource(ConnectivityR.string.module_connectivity_type_wifi_label)
                ConnectivityInfo.ConnectionType.CELLULAR -> stringResource(ConnectivityR.string.module_connectivity_type_cellular_label)
                ConnectivityInfo.ConnectionType.ETHERNET -> stringResource(ConnectivityR.string.module_connectivity_type_ethernet_label)
                ConnectivityInfo.ConnectionType.NONE, null -> stringResource(ConnectivityR.string.module_connectivity_type_none_label)
            },
        )
        CopyableDetailRow(
            label = stringResource(ConnectivityR.string.module_connectivity_detail_public_ip_label),
            value = info.publicIp ?: stringResource(ConnectivityR.string.module_connectivity_unknown_public_ip_label),
            copyable = info.publicIp != null,
            showMessage = showMessage,
        )
        CopyableDetailRow(
            label = stringResource(ConnectivityR.string.module_connectivity_detail_local_ipv4_label),
            value = info.localAddressIpv4 ?: unknownLocal,
            copyable = info.localAddressIpv4 != null,
            showMessage = showMessage,
        )
        CopyableDetailRow(
            label = stringResource(ConnectivityR.string.module_connectivity_detail_local_ipv6_label),
            value = info.localAddressIpv6 ?: unknownLocal,
            copyable = info.localAddressIpv6 != null,
            showMessage = showMessage,
        )
        DetailRow(
            label = stringResource(ConnectivityR.string.module_connectivity_detail_gateway_label),
            value = info.gatewayIp ?: unknownLocal,
        )
        DetailRow(
            label = stringResource(ConnectivityR.string.module_connectivity_detail_dns_label),
            value = info.dnsServers?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: unknownLocal,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview2
@Composable
private fun ConnectivityDetailContentPreview() = PreviewWrapper {
    ConnectivityDetailContent(
        info = ConnectivityInfo(
            connectionType = ConnectivityInfo.ConnectionType.WIFI,
            publicIp = "203.0.113.42",
            localAddressIpv4 = "192.168.1.100",
            localAddressIpv6 = "fe80::1",
            gatewayIp = "192.168.1.1",
            dnsServers = listOf("8.8.8.8", "8.8.4.4"),
        ),
    )
}
