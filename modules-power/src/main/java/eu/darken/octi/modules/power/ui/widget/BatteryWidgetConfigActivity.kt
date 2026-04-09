package eu.darken.octi.modules.power.ui.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.theming.ThemeSettings
import eu.darken.octi.common.theming.ThemeState
import eu.darken.octi.common.widget.WidgetConfigDevice
import eu.darken.octi.common.widget.WidgetConfigScreen
import eu.darken.octi.common.widget.WidgetInstanceConfig
import eu.darken.octi.common.widget.WidgetTheme
import eu.darken.octi.common.widget.applyWidgetConfig
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.modules.power.R
import eu.darken.octi.modules.power.core.PowerRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class BatteryWidgetConfigActivity : androidx.activity.ComponentActivity() {

    @Inject lateinit var themeSettings: ThemeSettings
    @Inject lateinit var metaRepo: MetaRepo
    @Inject lateinit var powerRepo: PowerRepo

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch {
            val currentOptions = AppWidgetManager.getInstance(this@BatteryWidgetConfigActivity)
                .getAppWidgetOptions(appWidgetId)
            val instanceConfig = WidgetInstanceConfig.parse(currentOptions)

            val availableDevices = withTimeoutOrNull(DEVICE_LOAD_TIMEOUT) {
                val metaById = metaRepo.state.first().all.associateBy { it.deviceId }
                val now = System.currentTimeMillis()
                powerRepo.state.first().all.mapNotNull { p ->
                    val m = metaById[p.deviceId] ?: return@mapNotNull null
                    WidgetConfigDevice(
                        id = p.deviceId.id,
                        label = m.data.labelOrFallback,
                        subtitle = lastSeenSubtitle(m.modifiedAt.toEpochMilliseconds(), now),
                        icon = composeIconFor(m.data.deviceType),
                    )
                }
            }
                .orEmpty()
                .distinctBy { it.id }
                .sortedBy { it.label.lowercase() }

            setContent {
                val themeState by themeSettings.themeState.collectAsState(ThemeState())
                OctiTheme(state = themeState) {
                    WidgetConfigScreen(
                        initialMode = if (instanceConfig.isMaterialYou) {
                            WidgetInstanceConfig.MODE_MATERIAL_YOU
                        } else {
                            WidgetInstanceConfig.MODE_CUSTOM
                        },
                        initialPresetName = instanceConfig.presetName,
                        initialBgColor = instanceConfig.customBg,
                        initialAccentColor = instanceConfig.customAccent,
                        availableDevices = availableDevices,
                        initialSelectedDeviceIds = instanceConfig.allowedDeviceIds,
                        onClose = { finish() },
                        onApply = { isMy, preset, bg, accent, ids ->
                            val appContext = applicationContext
                            applyWidgetConfig(
                                appWidgetId = appWidgetId,
                                newConfig = WidgetInstanceConfig(
                                    isMaterialYou = isMy,
                                    presetName = preset,
                                    customBg = bg,
                                    customAccent = accent,
                                    allowedDeviceIds = ids,
                                ),
                                tag = TAG,
                            ) {
                                val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
                                BatteryGlanceWidget().update(appContext, glanceId)
                            }
                        },
                        previewContent = { colors -> BatteryWidgetPreview(colors = colors) },
                    )
                }
            }
        }
    }

    private fun lastSeenSubtitle(modifiedAtMillis: Long, nowMillis: Long): String {
        val relative = DateUtils.getRelativeTimeSpanString(
            modifiedAtMillis,
            nowMillis,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
        return getString(eu.darken.octi.sync.R.string.sync_device_last_seen_label, relative.toString())
    }

    companion object {
        private val TAG = logTag("Module", "Power", "Widget", "Config")
        private val DEVICE_LOAD_TIMEOUT = 2.seconds
    }
}

private fun composeIconFor(type: MetaInfo.DeviceType): ImageVector = when (type) {
    MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
    MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
    MetaInfo.DeviceType.UNKNOWN -> Icons.TwoTone.QuestionMark
}

@Composable
private fun BatteryWidgetPreview(colors: WidgetTheme.Colors?) {
    val previewColors = colors ?: WidgetTheme.Colors(
        containerBg = colorResource(CommonR.color.widgetContainerBackground).toArgb(),
        barFill = colorResource(CommonR.color.widgetBarFill).toArgb(),
        barTrack = colorResource(CommonR.color.widgetBarTrack).toArgb(),
        icon = colorResource(CommonR.color.widgetBarIcon).toArgb(),
        onContainer = colorResource(CommonR.color.widgetOnContainer).toArgb(),
    )

    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(previewColors.containerBg))
                .padding(8.dp),
        ) {
            PreviewWidgetRow(
                name = "Pixel 8",
                lastSeen = "5 min ago",
                percent = 75,
                colors = previewColors,
            )
        }
    }
}

@Composable
private fun PreviewWidgetRow(
    name: String,
    lastSeen: String,
    percent: Int,
    colors: WidgetTheme.Colors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(colors.barTrack)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(colors.barFill)),
            )
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.widget_battery_full_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(colors.icon),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(colors.icon),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "\u00b7 $lastSeen",
                    fontSize = 11.sp,
                    color = Color(colors.icon),
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$percent%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(colors.onContainer),
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Preview2
@Composable
private fun WidgetConfigScreenPreview() = PreviewWrapper {
    WidgetConfigScreen(
        initialMode = null,
        initialPresetName = null,
        initialBgColor = null,
        initialAccentColor = null,
        onClose = {},
        onApply = { _, _, _, _, _ -> },
        previewContent = { colors -> BatteryWidgetPreview(colors = colors) },
    )
}
