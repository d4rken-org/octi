package eu.darken.octi.modules.connectivity.ui.widget

import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
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
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.modules.connectivity.R
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.disambiguateDeviceLabels
import eu.darken.octi.common.R as CommonR

internal object NetworkWidgetSizing {
    const val TWO_COLUMN_MIN_WIDTH_DP = 220
    const val OUTER_PADDING_DP = 6f
    const val MAX_VISIBLE_ITEMS = 10

    val OUTER_PADDING = OUTER_PADDING_DP.dp
    val ROW_SPACING = 4.dp
    val TILE_HEIGHT = 76.dp
    val SINGLE_ROW_HEIGHT = 28.dp
    val INTER_ROW_SPACER = 2.dp

    val TILE_SLOT_DP: Float
        get() = TILE_HEIGHT.value + ROW_SPACING.value
    val ROW_SLOT_DP: Float
        get() = SINGLE_ROW_HEIGHT.value + INTER_ROW_SPACER.value

    /**
     * Two-column tile mode requires enough width (≥220dp) AND enough height to fit at least one
     * 76dp tile vertically. Wide-short placements (e.g. 220dp × 50dp on a 4×1 cell) fall back to
     * the 28dp compact row layout to avoid clipping.
     */
    fun shouldUseTwoColumn(widthDp: Float, heightDp: Float): Boolean {
        if (widthDp < TWO_COLUMN_MIN_WIDTH_DP) return false
        if (!heightDp.isFinite() || heightDp <= 0f) return true
        return heightDp >= 2f * OUTER_PADDING_DP + TILE_HEIGHT.value
    }

    fun maxItemsForSize(widthDp: Float, heightDp: Float): Int {
        if (heightDp <= 0f || !heightDp.isFinite()) return 1

        val isTwoColumn = shouldUseTwoColumn(widthDp = widthDp, heightDp = heightDp)
        val rowHeightDp = if (isTwoColumn) TILE_HEIGHT.value else SINGLE_ROW_HEIGHT.value
        val rowSpacingDp = if (isTwoColumn) ROW_SPACING.value else INTER_ROW_SPACER.value
        val rows = ((heightDp - (2f * OUTER_PADDING_DP - rowSpacingDp)) / (rowHeightDp + rowSpacingDp))
            .toInt()
            .coerceAtLeast(1)
            .coerceAtMost(MAX_VISIBLE_ITEMS)

        return if (isTwoColumn) (rows * 2).coerceAtMost(MAX_VISIBLE_ITEMS) else rows
    }

    fun computeVisibleSlots(totalItemCount: Int, maxItems: Int): VisibleSlots {
        val cappedMaxItems = maxItems.coerceAtMost(MAX_VISIBLE_ITEMS)
        return when {
            cappedMaxItems <= 0 || totalItemCount <= 0 -> VisibleSlots(0, false)
            totalItemCount <= cappedMaxItems -> VisibleSlots(totalItemCount, false)
            cappedMaxItems == 1 -> VisibleSlots(0, true)
            else -> VisibleSlots(cappedMaxItems - 1, true)
        }
    }

    data class VisibleSlots(
        val visibleItemCount: Int,
        val showOverflow: Boolean,
    )
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
    heightDp: Float = 0f,
    allowedDeviceIds: Set<String>? = null,
) {
    val allDevices = buildDeviceTiles(metaState, connectivityState, allowedDeviceIds)
    val containerBg = themeColors?.containerBg
    val context = LocalContext.current
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }
    val useTwoColumn = NetworkWidgetSizing.shouldUseTwoColumn(widthDp = widthDp, heightDp = heightDp)
    val onContainer = colorOrDefault(themeColors?.onContainer, CommonR.color.widgetOnContainer)
    val showEmptyState = allDevices.isEmpty() && maxRows > 0 && metaState != null && connectivityState != null

    val slots = NetworkWidgetSizing.computeVisibleSlots(allDevices.size, maxRows)
    val visibleDevices = allDevices.take(slots.visibleItemCount)
    val overflowCount = allDevices.size - visibleDevices.size
    val displayItems = buildList {
        visibleDevices.forEach { add(NetworkDisplayItem.Device(it)) }
        if (slots.showOverflow) add(NetworkDisplayItem.Overflow(overflowCount))
    }
    val tileSpacing = NetworkWidgetSizing.ROW_SPACING
    val compactSpacing = NetworkWidgetSizing.INTER_ROW_SPACER

    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .widgetCornerRadius(16.dp)
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
            if (showEmptyState) {
                val emptyStateRes = if (allowedDeviceIds.isNullOrEmpty()) {
                    CommonR.string.widget_no_sync_devices_label
                } else {
                    CommonR.string.widget_empty_label
                }
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = context.getString(emptyStateRes),
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = onContainer,
                        ),
                        maxLines = 1,
                    )
                }
            } else Column(modifier = GlanceModifier.fillMaxSize()) {
                if (useTwoColumn) {
                    val pairs = displayItems.chunked(2)
                    pairs.forEachIndexed { rowIndex, pair ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(bottom = if (rowIndex < pairs.lastIndex) tileSpacing else 0.dp),
                        ) {
                            NetworkDisplayTileContent(
                                item = pair[0],
                                themeColors = themeColors,
                                overflowClick = openApp,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Spacer(modifier = GlanceModifier.width(tileSpacing))
                            if (pair.size == 2) {
                                NetworkDisplayTileContent(
                                    item = pair[1],
                                    themeColors = themeColors,
                                    overflowClick = openApp,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                            } else {
                                Box(modifier = GlanceModifier.defaultWeight()) {}
                            }
                        }
                    }
                } else {
                    displayItems.forEachIndexed { index, item ->
                        NetworkDisplayCompactRow(
                            item = item,
                            themeColors = themeColors,
                            overflowClick = openApp,
                            bottomSpacing = if (index < displayItems.lastIndex) compactSpacing else 0.dp,
                        )
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
    allowedDeviceIds: Set<String>?,
): List<NetworkDeviceTile> {
    if (metaState == null || connectivityState == null) return emptyList()

    val metaAll = metaState.all as? Collection<ModuleData<MetaInfo>>
        ?: return emptyList()
    val connectivityAll = connectivityState.all as? Collection<ModuleData<ConnectivityInfo>>
        ?: return emptyList()

    val pairs = connectivityAll
        .mapNotNull { connData ->
            val metaData = metaAll.firstOrNull { it.deviceId == connData.deviceId }
            metaData?.let { connData to it }
        }
        .let { pairs ->
            if (allowedDeviceIds.isNullOrEmpty()) pairs
            else pairs.filter { (connData, _) -> connData.deviceId.id in allowedDeviceIds }
        }
    val labelsByDevice = disambiguateDeviceLabels(pairs.associate { (_, metaData) ->
        metaData.deviceId to metaData.data.labelOrFallback
    })

    return pairs
        .sortedWith(
            compareBy<Pair<ModuleData<ConnectivityInfo>, ModuleData<MetaInfo>>, String>(String.CASE_INSENSITIVE_ORDER) {
                labelsByDevice[it.second.deviceId] ?: it.second.data.labelOrFallback
            }.thenBy { it.second.deviceId.id }
        )
        .map { (connData, metaData) ->
            NetworkDeviceTile(
                deviceId = connData.deviceId.id,
                deviceName = labelsByDevice[metaData.deviceId] ?: metaData.data.labelOrFallback,
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

private sealed interface NetworkDisplayItem {
    data class Device(val device: NetworkDeviceTile) : NetworkDisplayItem
    data class Overflow(val hiddenCount: Int) : NetworkDisplayItem
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
private fun NetworkDisplayTileContent(
    item: NetworkDisplayItem,
    themeColors: WidgetTheme.Colors?,
    overflowClick: Action?,
    modifier: GlanceModifier = GlanceModifier,
) {
    when (item) {
        is NetworkDisplayItem.Device -> NetworkDeviceTileContent(
            device = item.device,
            themeColors = themeColors,
            modifier = modifier,
        )

        is NetworkDisplayItem.Overflow -> NetworkOverflowTileContent(
            hiddenCount = item.hiddenCount,
            themeColors = themeColors,
            onClick = overflowClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun NetworkDeviceTileContent(
    device: NetworkDeviceTile,
    themeColors: WidgetTheme.Colors?,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val tileBg = colorOrDefault(themeColors?.tileBg, CommonR.color.widgetTileBackground)
    val titleColor = colorOrDefault(themeColors?.onTile, CommonR.color.widgetOnTile)
    val detailColor = colorOrDefault(themeColors?.onTileVariant, CommonR.color.widgetOnTileVariant)
    val iconRes = connectionIconRes(device.connectionType)

    val copyAction = actionRunCallback<CopyNetworkAddressesCallback>(
        actionParametersOf(CopyNetworkAddressesCallback.KEY_DEVICE_ID to device.deviceId),
    )

    Box(
        modifier = modifier
            .height(NetworkWidgetSizing.TILE_HEIGHT)
            .widgetCornerRadius(12.dp)
            .background(tileBg)
            .clickable(copyAction),
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
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(titleColor),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = device.deviceName,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = titleColor,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "${connectionTypeLabel(context, device.connectionType)} \u00b7 ${device.lastSeen}",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = detailColor,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = device.localIp,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = detailColor,
                ),
                maxLines = 1,
            )
            Text(
                text = device.publicIp,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = detailColor,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NetworkOverflowTileContent(
    hiddenCount: Int,
    themeColors: WidgetTheme.Colors?,
    onClick: Action?,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val tileBg = colorOrDefault(themeColors?.tileBg, CommonR.color.widgetTileBackground)
    val titleColor = colorOrDefault(themeColors?.onTile, CommonR.color.widgetOnTile)

    Box(
        modifier = modifier
            .height(NetworkWidgetSizing.TILE_HEIGHT)
            .widgetCornerRadius(12.dp)
            .background(tileBg)
            .then(if (onClick != null) GlanceModifier.clickable(onClick) else GlanceModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(CommonR.string.widget_more_items, hiddenCount),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = titleColor,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun NetworkDisplayCompactRow(
    item: NetworkDisplayItem,
    themeColors: WidgetTheme.Colors?,
    overflowClick: Action?,
    bottomSpacing: Dp,
) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = bottomSpacing)) {
        when (item) {
            is NetworkDisplayItem.Device -> NetworkDeviceCompactRow(
                device = item.device,
                themeColors = themeColors,
            )

            is NetworkDisplayItem.Overflow -> NetworkOverflowCompactRow(
                hiddenCount = item.hiddenCount,
                themeColors = themeColors,
                onClick = overflowClick,
            )
        }
    }
}

@Composable
private fun NetworkDeviceCompactRow(
    device: NetworkDeviceTile,
    themeColors: WidgetTheme.Colors?,
) {
    val tileBg = colorOrDefault(themeColors?.tileBg, CommonR.color.widgetTileBackground)
    val titleColor = colorOrDefault(themeColors?.onTile, CommonR.color.widgetOnTile)
    val detailColor = colorOrDefault(themeColors?.onTileVariant, CommonR.color.widgetOnTileVariant)
    val iconRes = connectionIconRes(device.connectionType)

    val copyAction = actionRunCallback<CopyNetworkAddressesCallback>(
        actionParametersOf(CopyNetworkAddressesCallback.KEY_DEVICE_ID to device.deviceId),
    )

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(NetworkWidgetSizing.SINGLE_ROW_HEIGHT)
            .widgetCornerRadius(12.dp)
            .background(tileBg)
            .clickable(copyAction),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(titleColor),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = device.deviceName,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = titleColor,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "${device.localIp} \u00b7 ${device.publicIp}",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = detailColor,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun NetworkOverflowCompactRow(
    hiddenCount: Int,
    themeColors: WidgetTheme.Colors?,
    onClick: Action?,
) {
    val context = LocalContext.current
    val tileBg = colorOrDefault(themeColors?.tileBg, CommonR.color.widgetTileBackground)
    val titleColor = colorOrDefault(themeColors?.onTile, CommonR.color.widgetOnTile)

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(NetworkWidgetSizing.SINGLE_ROW_HEIGHT)
            .widgetCornerRadius(12.dp)
            .background(tileBg)
            .then(if (onClick != null) GlanceModifier.clickable(onClick) else GlanceModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(CommonR.string.widget_more_items, hiddenCount),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = titleColor,
            ),
            maxLines = 1,
        )
    }
}

private fun colorOrDefault(value: Int?, @ColorRes defaultRes: Int): ColorProvider =
    value?.let { ColorProvider(Color(it)) } ?: ColorProvider(defaultRes)

private fun GlanceModifier.widgetCornerRadius(radius: Dp): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(radius) else this
