package eu.darken.octi.modules.power.ui.widget

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
import androidx.glance.LocalSize
import androidx.glance.action.Action
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
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.modules.power.R
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.sync.core.disambiguateDeviceLabels

internal object BatteryWidgetSizing {
    /** 8dp outer padding × 2 sides + 4dp spacer between bar and percent text + 44dp percent text width. */
    const val BAR_HORIZONTAL_OVERHEAD_DP = 64f

    const val OUTER_PADDING_DP = 8f
    const val ROW_HEIGHT_DP = 30f
    const val ROW_SPACING_DP = 2f

    /** Glance `Column` truncates beyond ~10 direct children; cap to stay safely under that limit. */
    const val MAX_VISIBLE_ROWS = 10

    /**
     * Number of rows that fit in [heightDp] given outer padding + N rows + (N-1) spacers.
     *
     * For N rows: total = 2 * OUTER_PADDING + N * ROW_HEIGHT + (N - 1) * ROW_SPACING
     *                   = 16 + 32N - 2 = 32N + 14
     * → N ≤ (heightDp - 14) / 32
     */
    fun maxRowsForHeight(heightDp: Float): Int {
        if (heightDp <= 0f || !heightDp.isFinite()) return MAX_VISIBLE_ROWS
        val verticalOverheadDp = 2f * OUTER_PADDING_DP - ROW_SPACING_DP
        val perRowDp = ROW_HEIGHT_DP + ROW_SPACING_DP
        val rows = ((heightDp - verticalOverheadDp) / perRowDp).toInt()
        return rows.coerceIn(1, MAX_VISIBLE_ROWS)
    }

    /**
     * Splits the available [maxRows] slots between visible device rows and an optional
     * "+N more" overflow indicator.
     *
     * When [totalDeviceCount] exceeds [maxRows], the last slot becomes the overflow indicator
     * so the user sees that devices are hidden. If only one slot is available, the indicator
     * takes that slot — silent truncation was the reported bug.
     */
    fun computeVisibleSlots(totalDeviceCount: Int, maxRows: Int): VisibleSlots = when {
        maxRows <= 0 || totalDeviceCount <= 0 -> VisibleSlots(0, false)
        totalDeviceCount <= maxRows -> VisibleSlots(totalDeviceCount, false)
        maxRows == 1 -> VisibleSlots(0, true)
        else -> VisibleSlots(maxRows - 1, true)
    }

    data class VisibleSlots(
        val visibleDeviceCount: Int,
        val showOverflow: Boolean,
    )
}

private fun colorOrDefault(value: Int?, @ColorRes defaultRes: Int): ColorProvider =
    value?.let { ColorProvider(Color(it)) } ?: ColorProvider(defaultRes)

private data class BatteryDeviceRow(
    val deviceId: String,
    val deviceName: String,
    val lastSeen: CharSequence,
    val percent: Int,
    val isCharging: Boolean,
)

@Composable
fun BatteryWidgetContent(
    metaState: ModuleRepo.State<*>?,
    powerState: ModuleRepo.State<*>?,
    themeColors: WidgetTheme.Colors?,
    maxRows: Int,
    allowedDeviceIds: Set<String>? = null,
) {
    val allDevices = buildDeviceRows(metaState, powerState, allowedDeviceIds)
    val containerBg = themeColors?.containerBg
    val context = LocalContext.current
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }
    val barWidthDp = (LocalSize.current.width.value - BatteryWidgetSizing.BAR_HORIZONTAL_OVERHEAD_DP)
        .coerceAtLeast(0f)
    val onContainer = colorOrDefault(themeColors?.onContainer, CommonR.color.widgetOnContainer)
    val showEmptyState = allDevices.isEmpty() && maxRows > 0 && metaState != null && powerState != null

    val slots = BatteryWidgetSizing.computeVisibleSlots(allDevices.size, maxRows)
    val visibleDevices = allDevices.take(slots.visibleDeviceCount)
    val overflowCount = allDevices.size - visibleDevices.size
    val showOverflow = slots.showOverflow

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
                .padding(BatteryWidgetSizing.OUTER_PADDING_DP.dp),
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
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visibleDevices.forEachIndexed { index, device ->
                        val isLastSlot = !showOverflow && index == visibleDevices.lastIndex
                        BatteryDeviceRowContent(
                            device = device,
                            themeColors = themeColors,
                            barWidthDp = barWidthDp,
                            bottomPaddingDp = if (isLastSlot) 0f else BatteryWidgetSizing.ROW_SPACING_DP,
                        )
                    }
                    if (showOverflow) {
                        BatteryOverflowRowContent(
                            overflowCount = overflowCount,
                            themeColors = themeColors,
                            onClick = openApp,
                            bottomPaddingDp = 0f,
                        )
                    }
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun buildDeviceRows(
    metaState: ModuleRepo.State<*>?,
    powerState: ModuleRepo.State<*>?,
    allowedDeviceIds: Set<String>?,
): List<BatteryDeviceRow> {
    if (metaState == null || powerState == null) return emptyList()

    val metaAll = metaState.all as? Collection<ModuleData<MetaInfo>> ?: return emptyList()
    val powerAll = powerState.all as? Collection<ModuleData<PowerInfo>> ?: return emptyList()

    val pairs = powerAll
        .mapNotNull { powerData ->
            val metaData = metaAll.firstOrNull { it.deviceId == powerData.deviceId }
            metaData?.let { powerData to it }
        }
        .let { pairs ->
            if (allowedDeviceIds.isNullOrEmpty()) pairs
            else pairs.filter { (powerData, _) -> powerData.deviceId.id in allowedDeviceIds }
        }
    val labelsByDevice = disambiguateDeviceLabels(pairs.associate { (_, metaData) ->
        metaData.deviceId to metaData.data.labelOrFallback
    })

    return pairs
        .sortedWith(
            compareBy<Pair<ModuleData<PowerInfo>, ModuleData<MetaInfo>>, String>(String.CASE_INSENSITIVE_ORDER) {
                labelsByDevice[it.second.deviceId] ?: it.second.data.labelOrFallback
            }.thenBy { it.second.deviceId.id }
        )
        .map { (powerData, metaData) ->
            val rawPercent = powerData.data.battery.percent
            val safePercent = if (rawPercent.isFinite()) (rawPercent * 100).toInt() else 0
            BatteryDeviceRow(
                deviceId = powerData.deviceId.id,
                deviceName = labelsByDevice[metaData.deviceId] ?: metaData.data.labelOrFallback,
                lastSeen = DateUtils.getRelativeTimeSpanString(
                    metaData.modifiedAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ),
                percent = safePercent.coerceIn(0, 100),
                isCharging = powerData.data.isCharging,
            )
        }
}

@Composable
private fun BatteryDeviceRowContent(
    device: BatteryDeviceRow,
    themeColors: WidgetTheme.Colors?,
    barWidthDp: Float,
    bottomPaddingDp: Float,
) {
    val context = LocalContext.current
    val accentBg = colorOrDefault(themeColors?.accentBg, CommonR.color.widgetAccentBackground)
    val tileBg = colorOrDefault(themeColors?.tileBg, CommonR.color.widgetTileBackground)
    val accentContent = colorOrDefault(themeColors?.onAccent, CommonR.color.widgetOnAccent)
    val onContainer = colorOrDefault(themeColors?.onContainer, CommonR.color.widgetOnContainer)

    val rowClick = WidgetDeeplink.buildIntent(context, device.deviceId, WidgetDeeplink.ModuleType.POWER)
        ?.let { actionStartActivity(it) }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = bottomPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(BatteryWidgetSizing.ROW_HEIGHT_DP.dp)
                .cornerRadius(12.dp)
                .background(tileBg)
                .then(
                    if (rowClick != null) GlanceModifier.clickable(rowClick) else GlanceModifier
                ),
        ) {
            val fillWidth = (barWidthDp * device.percent / 100f).coerceAtLeast(0f)
            if (fillWidth > 0f) {
                Box(
                    modifier = GlanceModifier
                        .width(fillWidth.dp)
                        .height(BatteryWidgetSizing.ROW_HEIGHT_DP.dp)
                        .cornerRadius(12.dp)
                        .background(accentBg),
                ) {}
            }
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(
                        if (device.isCharging) R.drawable.widget_battery_charging_full_24
                        else R.drawable.widget_battery_full_24,
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(accentContent),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = device.deviceName,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = accentContent,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "\u00b7 ${device.lastSeen}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = accentContent,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = "${device.percent}%",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = onContainer,
            ),
            modifier = GlanceModifier.width(44.dp),
        )
    }
}

@Composable
private fun BatteryOverflowRowContent(
    overflowCount: Int,
    themeColors: WidgetTheme.Colors?,
    onClick: Action?,
    bottomPaddingDp: Float,
) {
    val context = LocalContext.current
    val tileBg = colorOrDefault(themeColors?.tileBg, CommonR.color.widgetTileBackground)
    val onContainer = colorOrDefault(themeColors?.onContainer, CommonR.color.widgetOnContainer)

    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = bottomPaddingDp.dp)) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(BatteryWidgetSizing.ROW_HEIGHT_DP.dp)
                .cornerRadius(12.dp)
                .background(tileBg)
                .then(if (onClick != null) GlanceModifier.clickable(onClick) else GlanceModifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = context.getString(R.string.module_power_widget_more_devices, overflowCount),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = onContainer,
                ),
                maxLines = 1,
            )
        }
    }
}
