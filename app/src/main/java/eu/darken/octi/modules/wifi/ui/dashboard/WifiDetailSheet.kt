package eu.darken.octi.modules.wifi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.DetailRow
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.wifi.R as WifiR
import eu.darken.octi.modules.wifi.core.WifiInfo

@Composable
internal fun WifiDetailSheet(
    info: WifiInfo,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WifiDetailContent(info)
    }
}

@Composable
private fun WifiDetailContent(info: WifiInfo) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = stringResource(WifiR.string.module_wifi_label),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            label = stringResource(WifiR.string.module_wifi_detail_ssid_label),
            value = info.currentWifi?.ssid
                ?: stringResource(WifiR.string.module_wifi_unknown_ssid_label),
        )
        DetailRow(
            label = stringResource(WifiR.string.module_wifi_detail_frequency_label),
            value = when (info.currentWifi?.freqType) {
                WifiInfo.Wifi.Type.FIVE_GHZ -> stringResource(WifiR.string.module_wifi_freq_5ghz)
                WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ -> stringResource(WifiR.string.module_wifi_freq_2_4ghz)
                else -> stringResource(CommonR.string.general_na_label)
            },
        )
        val sig = info.currentWifi?.reception ?: 0f
        DetailRow(
            label = stringResource(WifiR.string.module_wifi_detail_signal_label),
            value = when {
                sig > 0.65f -> stringResource(WifiR.string.module_wifi_reception_good_label)
                sig > 0.3f -> stringResource(WifiR.string.module_wifi_reception_okay_label)
                sig > 0.0f -> stringResource(WifiR.string.module_wifi_reception_bad_label)
                else -> stringResource(CommonR.string.general_na_label)
            },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview2
@Composable
private fun WifiDetailContentPreview() = PreviewWrapper {
    WifiDetailContent(
        info = WifiInfo(
            currentWifi = WifiInfo.Wifi(
                ssid = "HomeNetwork",
                reception = 0.8f,
                freqType = WifiInfo.Wifi.Type.FIVE_GHZ,
            ),
        ),
    )
}
