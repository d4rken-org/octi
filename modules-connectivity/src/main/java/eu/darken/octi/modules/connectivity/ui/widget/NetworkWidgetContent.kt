package eu.darken.octi.modules.connectivity.ui.widget

import android.text.format.DateUtils
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.darken.octi.common.widget.WidgetTheme
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.modules.connectivity.R
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.common.R as CommonR

private data class NetworkDeviceRow(
    val deviceName: String,
    val lastSeen: CharSequence,
    val connectionType: ConnectivityInfo.ConnectionType?,
    val localIp: String,
    val publicIp: String,
)

@Composable
fun NetworkWidgetContent(
    metaState: ModuleRepo.State<*>?,
    connectivityState: ModuleRepo.State<*>?,
    themeColors: WidgetTheme.Colors?,
    maxRows: Int,
) {
    val devices = buildDeviceRows(metaState, connectivityState, maxRows)
    val containerBg = themeColors?.containerBg
    val context = LocalContext.current
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }

    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .then(
                    if (containerBg != null) {
                        GlanceModifier.background(ColorProvider(Color(containerBg)))
                    } else {
                        GlanceModifier.background(ColorProvider(CommonR.color.widgetContainerBackground))
                    }
                )
                .then(
                    if (openApp != null) GlanceModifier.clickable(openApp) else GlanceModifier
                )
                .padding(8.dp),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                devices.forEachIndexed { index, device ->
                    NetworkDeviceRowContent(
                        device = device,
                        themeColors = themeColors,
                    )
                    if (index < devices.lastIndex) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun buildDeviceRows(
    metaState: ModuleRepo.State<*>?,
    connectivityState: ModuleRepo.State<*>?,
    maxRows: Int,
): List<NetworkDeviceRow> {
    if (connectivityState == null) return emptyList()

    val metaAll = metaState?.all as? Collection<eu.darken.octi.module.core.ModuleData<eu.darken.octi.modules.meta.core.MetaInfo>>
    val connectivityAll = connectivityState.all as? Collection<eu.darken.octi.module.core.ModuleData<ConnectivityInfo>>
        ?: return emptyList()

    return connectivityAll
        .map { connData ->
            val metaData = metaAll?.firstOrNull { it.deviceId == connData.deviceId }
            connData to metaData
        }
        .sortedBy { (_, metaData) -> (metaData?.data?.labelOrFallback ?: "Unknown").lowercase() }
        .take(maxRows)
        .map { (connData, metaData) ->
            NetworkDeviceRow(
                deviceName = metaData?.data?.labelOrFallback ?: "Unknown",
                lastSeen = DateUtils.getRelativeTimeSpanString(
                    (metaData?.modifiedAt ?: connData.modifiedAt).toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ),
                connectionType = connData.data.connectionType,
                localIp = connData.data.localAddressIpv4 ?: "\u2014",
                publicIp = connData.data.publicIp ?: "\u2014",
            )
        }
}

@Composable
private fun NetworkDeviceRowContent(
    device: NetworkDeviceRow,
    themeColors: WidgetTheme.Colors?,
) {
    val iconColor = colorOrDefault(themeColors?.icon, CommonR.color.widgetBarIcon)
    val barTrack = colorOrDefault(themeColors?.barTrack, CommonR.color.widgetBarTrack)

    val iconRes = when (device.connectionType) {
        ConnectivityInfo.ConnectionType.WIFI -> R.drawable.widget_network_wifi_24
        ConnectivityInfo.ConnectionType.CELLULAR -> R.drawable.widget_network_cellular_24
        ConnectivityInfo.ConnectionType.ETHERNET -> R.drawable.widget_network_ethernet_24
        ConnectivityInfo.ConnectionType.NONE, null -> R.drawable.widget_network_off_24
    }

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(44.dp)
            .cornerRadius(12.dp)
            .background(barTrack),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(iconColor),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = device.deviceName,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = iconColor,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "\u00b7 ${device.lastSeen}",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = iconColor,
                    ),
                    maxLines = 1,
                )
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${device.localIp} \u00b7 ${device.publicIp}",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = iconColor,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun colorOrDefault(value: Int?, @ColorRes defaultRes: Int): ColorProvider =
    value?.let { ColorProvider(Color(it)) } ?: ColorProvider(defaultRes)
