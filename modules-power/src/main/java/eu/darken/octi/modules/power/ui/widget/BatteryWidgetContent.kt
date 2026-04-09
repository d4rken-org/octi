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
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.modules.power.R

private object BatteryWidgetSizing {
    /** 8dp outer padding × 2 sides + 4dp spacer between bar and percent text + 44dp percent text width. */
    const val BAR_HORIZONTAL_OVERHEAD_DP = 64f
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
    val devices = buildDeviceRows(metaState, powerState, maxRows, allowedDeviceIds)
    val containerBg = themeColors?.containerBg
    val context = LocalContext.current
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }
    val barWidthDp = (LocalSize.current.width.value - BatteryWidgetSizing.BAR_HORIZONTAL_OVERHEAD_DP)
        .coerceAtLeast(0f)
    val onContainer = colorOrDefault(themeColors?.onContainer, CommonR.color.widgetOnContainer)
    val showEmptyState = devices.isEmpty() && maxRows > 0 && metaState != null && powerState != null

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
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    devices.forEachIndexed { index, device ->
                        BatteryDeviceRowContent(
                            device = device,
                            themeColors = themeColors,
                            barWidthDp = barWidthDp,
                        )
                        if (index < devices.lastIndex) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                        }
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
    maxRows: Int,
    allowedDeviceIds: Set<String>?,
): List<BatteryDeviceRow> {
    if (metaState == null || powerState == null) return emptyList()
    if (maxRows <= 0) return emptyList()

    val metaAll = metaState.all as? Collection<eu.darken.octi.module.core.ModuleData<eu.darken.octi.modules.meta.core.MetaInfo>> ?: return emptyList()
    val powerAll = powerState.all as? Collection<eu.darken.octi.module.core.ModuleData<eu.darken.octi.modules.power.core.PowerInfo>> ?: return emptyList()

    return powerAll
        .mapNotNull { powerData ->
            val metaData = metaAll.firstOrNull { it.deviceId == powerData.deviceId }
            metaData?.let { powerData to it }
        }
        .let { pairs ->
            if (allowedDeviceIds.isNullOrEmpty()) pairs
            else pairs.filter { (powerData, _) -> powerData.deviceId.id in allowedDeviceIds }
        }
        .sortedBy { (_, metaData) -> metaData.data.labelOrFallback.lowercase() }
        .take(maxRows)
        .map { (powerData, metaData) ->
            BatteryDeviceRow(
                deviceId = powerData.deviceId.id,
                deviceName = metaData.data.labelOrFallback,
                lastSeen = DateUtils.getRelativeTimeSpanString(
                    metaData.modifiedAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ),
                percent = (powerData.data.battery.percent * 100).toInt(),
                isCharging = powerData.data.isCharging,
            )
        }
}

@Composable
private fun BatteryDeviceRowContent(
    device: BatteryDeviceRow,
    themeColors: WidgetTheme.Colors?,
    barWidthDp: Float,
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
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(30.dp)
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
                        .height(30.dp)
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
