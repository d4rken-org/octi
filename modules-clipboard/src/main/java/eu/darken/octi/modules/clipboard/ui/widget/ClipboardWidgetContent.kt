package eu.darken.octi.modules.clipboard.ui.widget

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
import eu.darken.octi.common.navigation.WidgetDeeplink
import eu.darken.octi.common.widget.WidgetTheme
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.R
import eu.darken.octi.common.R as CommonR

internal object ClipboardWidgetSizing {
    val ROW_HEIGHT = 30.dp
    val ROW_SPACER = 2.dp
    val SELF_SECTION_SPACER = 4.dp
    val SHARE_BUTTON_HEIGHT = 36.dp
    val OUTER_PADDING = 8.dp

    /** Total fixed vertical space outside the scrolling device rows. */
    val FIXED_OVERHEAD_DP: Float
        get() = (OUTER_PADDING.value * 2) +
                SELF_SECTION_SPACER.value +
                ROW_HEIGHT.value +
                SELF_SECTION_SPACER.value +
                SHARE_BUTTON_HEIGHT.value
    val ROW_SLOT_DP: Float
        get() = ROW_HEIGHT.value + ROW_SPACER.value
}

private data class ClipboardDeviceRow(
    val deviceName: String,
    val lastSeen: CharSequence,
    val clipboardText: String?,
    val deviceId: String,
)

private data class SelfClipboardDisplay(
    val deviceId: String?,
    val text: String?,
)

@Composable
fun ClipboardWidgetContent(
    metaState: ModuleRepo.State<*>?,
    clipboardState: ModuleRepo.State<*>?,
    themeColors: WidgetTheme.Colors?,
    maxRows: Int,
) {
    val self = extractSelf(clipboardState)
    val devices = buildDeviceRows(metaState, clipboardState, self?.deviceId, maxRows)
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
                .padding(ClipboardWidgetSizing.OUTER_PADDING),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                devices.forEachIndexed { index, device ->
                    ClipboardDeviceRowContent(
                        device = device,
                        themeColors = themeColors,
                    )
                    if (index < devices.lastIndex) {
                        Spacer(modifier = GlanceModifier.height(ClipboardWidgetSizing.ROW_SPACER))
                    }
                }
                Spacer(modifier = GlanceModifier.height(ClipboardWidgetSizing.SELF_SECTION_SPACER))
                SelfClipboardRow(
                    self = self,
                    themeColors = themeColors,
                )
                Spacer(modifier = GlanceModifier.height(ClipboardWidgetSizing.SELF_SECTION_SPACER))
                ShareClipboardButton(themeColors = themeColors)
            }
        }
    }
}

@Composable
private fun ShareClipboardButton(themeColors: WidgetTheme.Colors?) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ClipboardWidgetSizing.SHARE_BUTTON_HEIGHT)
            .cornerRadius(8.dp)
            .then(
                if (themeColors?.barTrack != null) {
                    GlanceModifier.background(ColorProvider(Color(themeColors.barTrack)))
                } else {
                    GlanceModifier.background(ColorProvider(CommonR.color.widgetBarTrack))
                }
            )
            .clickable(actionRunCallback<PasteClipboardCallback>())
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_content_paste_24),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(
                colorOrDefault(themeColors?.icon, CommonR.color.widgetBarIcon),
            ),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = context.getString(R.string.module_clipboard_widget_paste_action),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = colorOrDefault(themeColors?.icon, CommonR.color.widgetBarIcon),
            ),
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun extractSelf(clipboardState: ModuleRepo.State<*>?): SelfClipboardDisplay? {
    if (clipboardState == null) return null
    val baseState = clipboardState as? BaseModuleRepo.State<*> ?: return null
    val selfData = (baseState.self as? ModuleData<ClipboardInfo>) ?: return null
    val deviceId = selfData.deviceId.id
    val text = when (selfData.data.type) {
        ClipboardInfo.Type.SIMPLE_TEXT -> {
            val full = selfData.data.data.utf8()
            if (full.length > 40) full.take(40) + "\u2026" else full
        }
        ClipboardInfo.Type.EMPTY -> null
    }
    return SelfClipboardDisplay(deviceId = deviceId, text = text)
}

@Suppress("UNCHECKED_CAST")
private fun buildDeviceRows(
    metaState: ModuleRepo.State<*>?,
    clipboardState: ModuleRepo.State<*>?,
    selfDeviceId: String?,
    maxRows: Int,
): List<ClipboardDeviceRow> {
    if (metaState == null || clipboardState == null) return emptyList()
    if (maxRows <= 0) return emptyList()

    val metaAll = metaState.all as? Collection<ModuleData<eu.darken.octi.modules.meta.core.MetaInfo>>
        ?: return emptyList()
    val clipboardAll = clipboardState.all as? Collection<ModuleData<ClipboardInfo>>
        ?: return emptyList()

    return clipboardAll
        .asSequence()
        .filter { it.deviceId.id != selfDeviceId }
        .mapNotNull { clipData ->
            val metaData = metaAll.firstOrNull { it.deviceId == clipData.deviceId }
            metaData?.let { clipData to it }
        }
        .sortedBy { (_, metaData) -> metaData.data.labelOrFallback.lowercase() }
        .take(maxRows)
        .map { (clipData, metaData) ->
            val text = when (clipData.data.type) {
                ClipboardInfo.Type.SIMPLE_TEXT -> {
                    val full = clipData.data.data.utf8()
                    if (full.length > 40) full.take(40) + "\u2026" else full
                }
                ClipboardInfo.Type.EMPTY -> null
            }
            ClipboardDeviceRow(
                deviceName = metaData.data.labelOrFallback,
                lastSeen = DateUtils.getRelativeTimeSpanString(
                    metaData.modifiedAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ),
                clipboardText = text,
                deviceId = clipData.deviceId.id,
            )
        }
        .toList()
}

@Composable
private fun ClipboardDeviceRowContent(
    device: ClipboardDeviceRow,
    themeColors: WidgetTheme.Colors?,
) {
    val context = LocalContext.current
    val iconColor = colorOrDefault(themeColors?.icon, CommonR.color.widgetBarIcon)
    val barTrack = colorOrDefault(themeColors?.barTrack, CommonR.color.widgetBarTrack)

    val rowClick = WidgetDeeplink.buildIntent(context, device.deviceId, WidgetDeeplink.ModuleType.CLIPBOARD)
        ?.let { actionStartActivity(it) }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ClipboardWidgetSizing.ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(ClipboardWidgetSizing.ROW_HEIGHT)
                .cornerRadius(12.dp)
                .background(barTrack)
                .then(
                    if (rowClick != null) GlanceModifier.clickable(rowClick) else GlanceModifier
                ),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_clipboard_24),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
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
                    text = device.clipboardText ?: "\u2014",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = iconColor,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Box(
            modifier = GlanceModifier
                .size(ClipboardWidgetSizing.ROW_HEIGHT)
                .cornerRadius(8.dp)
                .background(barTrack)
                .clickable(
                    actionRunCallback<CopyClipboardCallback>(
                        actionParametersOf(CopyClipboardCallback.KEY_DEVICE_ID to device.deviceId),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_content_copy_24),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(iconColor),
            )
        }
    }
}

@Composable
private fun SelfClipboardRow(
    self: SelfClipboardDisplay?,
    themeColors: WidgetTheme.Colors?,
) {
    val context = LocalContext.current
    val iconColor = colorOrDefault(themeColors?.icon, CommonR.color.widgetBarIcon)
    val barTrack = colorOrDefault(themeColors?.barTrack, CommonR.color.widgetBarTrack)

    val rowClick = self?.deviceId
        ?.let { WidgetDeeplink.buildIntent(context, it, WidgetDeeplink.ModuleType.CLIPBOARD) }
        ?.let { actionStartActivity(it) }

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ClipboardWidgetSizing.ROW_HEIGHT)
            .cornerRadius(12.dp)
            .background(barTrack)
            .then(
                if (rowClick != null) GlanceModifier.clickable(rowClick) else GlanceModifier
            ),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_clipboard_24),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(iconColor),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = self?.text ?: context.getString(R.string.module_clipboard_widget_self_empty),
                style = TextStyle(
                    fontStyle = if (self?.text == null) androidx.glance.text.FontStyle.Italic else androidx.glance.text.FontStyle.Normal,
                    fontSize = 11.sp,
                    color = iconColor,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun colorOrDefault(value: Int?, @ColorRes defaultRes: Int): ColorProvider =
    value?.let { ColorProvider(Color(it)) } ?: ColorProvider(defaultRes)
