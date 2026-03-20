package eu.darken.octi.modules.connectivity.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.ui.icon
import eu.darken.octi.modules.connectivity.R as ConnectivityR

@Composable
fun ConnectivityModuleTile(
    info: ConnectivityInfo,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onDetailClicked: () -> Unit,
) {
    val typeLabel = when (info.connectionType) {
        ConnectivityInfo.ConnectionType.WIFI -> stringResource(ConnectivityR.string.module_connectivity_type_wifi_label)
        ConnectivityInfo.ConnectionType.CELLULAR -> stringResource(ConnectivityR.string.module_connectivity_type_cellular_label)
        ConnectivityInfo.ConnectionType.ETHERNET -> stringResource(ConnectivityR.string.module_connectivity_type_ethernet_label)
        ConnectivityInfo.ConnectionType.NONE, null -> stringResource(ConnectivityR.string.module_connectivity_type_none_label)
    }
    val isConnected = info.connectionType != null && info.connectionType != ConnectivityInfo.ConnectionType.NONE
    val statusText = if (isConnected) {
        stringResource(ConnectivityR.string.module_connectivity_status_connected_label)
    } else {
        stringResource(ConnectivityR.string.module_connectivity_status_disconnected_label)
    }
    val ipText = info.localAddressIpv4
        ?: stringResource(ConnectivityR.string.module_connectivity_unknown_local_ip_label)

    val tileColor = if (isWide) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val tileDescription = "$typeLabel $statusText $ipText"

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
                    imageVector = info.connectionType.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            shape = CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = ipText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview2
@Composable
private fun ConnectivityModuleTilePreview() = PreviewWrapper {
    ConnectivityModuleTile(
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
