package eu.darken.octi.modules.connectivity.ui.widget

import android.content.Context
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
import eu.darken.octi.common.navigation.WidgetDeeplink
import eu.darken.octi.common.widget.WidgetTheme
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.modules.connectivity.R
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.common.R as CommonR

internal object NetworkWidgetSizing {
    const val TWO_COLUMN_MIN_WIDTH_DP = 220
    val OUTER_PADDING = 8.dp
    val ROW_SPACING = 4.dp
    val TILE_HEIGHT = 76.dp
    val SINGLE_ROW_HEIGHT = 44.dp
    val INTER_ROW_SPACER = 2.dp

    val TILE_SLOT_DP: Float
        get() = TILE_HEIGHT.value + ROW_SPACING.value
    val ROW_SLOT_DP: Float
        get() = SINGLE_ROW_HEIGHT.value + INTER_ROW_SPACER.value
    val FIXED_OVERHEAD_DP: Float
        get() = OUTER_PADDING.value * 2
}

private data class NetworkDeviceTile(
    val deviceId: String,
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
    widthDp: Float,
) {
    val devices = buildDeviceTiles(metaState, connectivityState, maxRows)
    val containerBg = themeColors?.containerBg
    val context = LocalContext.current
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }
    val useTwoColumn = widthDp >= NetworkWidgetSizing.TWO_COLUMN_MIN_WIDTH_DP

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
                .padding(NetworkWidgetSizing.OUTER_PADDING),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                if (useTwoColumn) {
                    val pairs = devices.chunked(2)
                    pairs.forEachIndexed { rowIndex, pair ->
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            NetworkDeviceTileContent(
                                device = pair[0],
                                themeColors = themeColors,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Spacer(modifier = GlanceModifier.width(NetworkWidgetSizing.ROW_SPACING))
                            if (pair.size == 2) {
                                NetworkDeviceTileContent(
                                    device = pair[1],
                                    themeColors = themeColors,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                            } else {
                                Box(modifier = GlanceModifier.defaultWeight()) {}
                            }
                        }
                        if (rowIndex < pairs.lastIndex) {
                            Spacer(modifier = GlanceModifier.height(NetworkWidgetSizing.ROW_SPACING))
                        }
                    }
                } else {
                    devices.forEachIndexed { index, device ->
                        NetworkDeviceCompactRow(
                            device = device,
                            themeColors = themeColors,
                        )
                        if (index < devices.lastIndex) {
                            Spacer(modifier = GlanceModifier.height(NetworkWidgetSizing.INTER_ROW_SPACER))
                        }
                    }
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun buildDeviceTiles(
    metaState: ModuleRepo.State<*>?,
    connectivityState: ModuleRepo.State<*>?,
    maxRows: Int,
): List<NetworkDeviceTile> {
    if (metaState == null || connectivityState == null) return emptyList()
    if (maxRows <= 0) return emptyList()

    val metaAll = metaState.all as? Collection<eu.darken.octi.module.core.ModuleData<eu.darken.octi.modules.meta.core.MetaInfo>>
        ?: return emptyList()
    val connectivityAll = connectivityState.all as? Collection<eu.darken.octi.module.core.ModuleData<ConnectivityInfo>>
        ?: return emptyList()

    return connectivityAll
        .mapNotNull { connData ->
            val metaData = metaAll.firstOrNull { it.deviceId == connData.deviceId }
            metaData?.let { connData to it }
        }
        .sortedBy { (_, metaData) -> metaData.data.labelOrFallback.lowercase() }
        .take(maxRows)
        .map { (connData, metaData) ->
            NetworkDeviceTile(
                deviceId = connData.deviceId.id,
                deviceName = metaData.data.labelOrFallback,
                lastSeen = DateUtils.getRelativeTimeSpanString(
                    metaData.modifiedAt.toEpochMilliseconds(),
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

private fun connectionIconRes(type: ConnectivityInfo.ConnectionType?): Int = when (type) {
    ConnectivityInfo.ConnectionType.WIFI -> R.drawable.widget_network_wifi_24
    ConnectivityInfo.ConnectionType.CELLULAR -> R.drawable.widget_network_cellular_24
    ConnectivityInfo.ConnectionType.ETHERNET -> R.drawable.widget_network_ethernet_24
    ConnectivityInfo.ConnectionType.NONE, null -> R.drawable.widget_network_off_24
}

private fun connectionTypeLabel(context: Context, type: ConnectivityInfo.ConnectionType?): String {
    val resId = when (type) {
        ConnectivityInfo.ConnectionType.WIFI -> R.string.module_connectivity_type_wifi_label
        ConnectivityInfo.ConnectionType.CELLULAR -> R.string.module_connectivity_type_cellular_label
        ConnectivityInfo.ConnectionType.ETHERNET -> R.string.module_connectivity_type_ethernet_label
        ConnectivityInfo.ConnectionType.NONE, null -> R.string.module_connectivity_type_none_label
    }
    return context.getString(resId)
}

@Composable
private fun NetworkDeviceTileContent(
    device: NetworkDeviceTile,
    themeColors: WidgetTheme.Colors?,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val iconColor = colorOrDefault(themeColors?.icon, CommonR.color.widgetBarIcon)
    val barTrack = colorOrDefault(themeColors?.barTrack, CommonR.color.widgetBarTrack)
    val iconRes = connectionIconRes(device.connectionType)

    val tileClick = WidgetDeeplink.buildIntent(context, device.deviceId, WidgetDeeplink.ModuleType.CONNECTIVITY)
        ?.let { actionStartActivity(it) }

    Box(
        modifier = modifier
            .height(NetworkWidgetSizing.TILE_HEIGHT)
            .cornerRadius(12.dp)
            .background(barTrack)
            .then(
                if (tileClick != null) GlanceModifier.clickable(tileClick) else GlanceModifier
            ),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
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
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "${connectionTypeLabel(context, device.connectionType)} \u00b7 ${device.lastSeen}",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = iconColor,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = device.localIp,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = iconColor,
                ),
                maxLines = 1,
            )
            Text(
                text = device.publicIp,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = iconColor,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NetworkDeviceCompactRow(
    device: NetworkDeviceTile,
    themeColors: WidgetTheme.Colors?,
) {
    val context = LocalContext.current
    val iconColor = colorOrDefault(themeColors?.icon, CommonR.color.widgetBarIcon)
    val barTrack = colorOrDefault(themeColors?.barTrack, CommonR.color.widgetBarTrack)
    val iconRes = connectionIconRes(device.connectionType)

    val rowClick = WidgetDeeplink.buildIntent(context, device.deviceId, WidgetDeeplink.ModuleType.CONNECTIVITY)
        ?.let { actionStartActivity(it) }

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(NetworkWidgetSizing.SINGLE_ROW_HEIGHT)
            .cornerRadius(12.dp)
            .background(barTrack)
            .then(
                if (rowClick != null) GlanceModifier.clickable(rowClick) else GlanceModifier
            ),
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

private fun colorOrDefault(value: Int?, @ColorRes defaultRes: Int): ColorProvider =
    value?.let { ColorProvider(Color(it)) } ?: ColorProvider(defaultRes)
