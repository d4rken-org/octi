package eu.darken.octi.modules.clipboard.ui.widget

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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.theming.ThemeSettings
import eu.darken.octi.common.theming.ThemeState
import eu.darken.octi.common.upgrade.ProState
import eu.darken.octi.common.upgrade.UpgradeLauncher
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.isPro
import eu.darken.octi.common.upgrade.proState
import eu.darken.octi.common.widget.WidgetConfigAction
import eu.darken.octi.common.widget.WidgetConfigDevice
import eu.darken.octi.common.widget.WidgetConfigScreen
import eu.darken.octi.common.widget.WidgetInstanceConfig
import eu.darken.octi.common.widget.WidgetSettings
import eu.darken.octi.common.widget.WidgetTheme
import eu.darken.octi.common.widget.applyWidgetConfig
import eu.darken.octi.common.widget.widgetDefaultColors
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.modules.clipboard.ClipboardRepo
import eu.darken.octi.modules.clipboard.R
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.disambiguateDeviceLabels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class ClipboardWidgetConfigActivity : androidx.activity.ComponentActivity() {

    @Inject lateinit var themeSettings: ThemeSettings
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject lateinit var upgradeLauncher: UpgradeLauncher
    @Inject lateinit var metaRepo: MetaRepo
    @Inject lateinit var clipboardRepo: ClipboardRepo
    @Inject lateinit var widgetSettings: WidgetSettings

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

        val proStateFlow = upgradeRepo.proState()

        setContent {
            val themeState by themeSettings.themeState.collectAsState(ThemeState())

            val initialInstanceConfig by produceState<WidgetInstanceConfig?>(initialValue = null) {
                value = widgetSettings.configValue(appWidgetId) {
                    AppWidgetManager.getInstance(this@ClipboardWidgetConfigActivity)
                        .getAppWidgetOptions(appWidgetId)
                }
            }

            val availableDevices by produceState<List<WidgetConfigDevice>?>(initialValue = null) {
                value = loadAvailableDevices()
            }

            val proState by proStateFlow.collectAsState(initial = ProState.Checking)

            OctiTheme(state = themeState) {
                val instanceConfig = initialInstanceConfig
                if (instanceConfig == null) {
                    WidgetConfigScreen(
                        initialMode = WidgetInstanceConfig.MODE_MATERIAL_YOU,
                        initialPresetName = null,
                        initialBgColor = null,
                        initialAccentColor = null,
                        availableDevices = null,
                        initialSelectedDeviceIds = emptySet(),
                        action = WidgetConfigAction.Loading,
                        onClose = { finish() },
                        onApply = { _, _, _, _, _ -> },
                        onUpgrade = { upgradeLauncher.launch(this@ClipboardWidgetConfigActivity) },
                        onRetry = {},
                        previewContent = { colors -> ClipboardWidgetPreview(colors = colors) },
                    )
                    return@OctiTheme
                }

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
                    action = when (proState) {
                        ProState.Checking -> WidgetConfigAction.Loading
                        ProState.Unlocked -> WidgetConfigAction.Apply
                        ProState.Locked -> WidgetConfigAction.Upgrade
                        is ProState.Error -> WidgetConfigAction.ErrorRetry(message = "")
                    },
                    onClose = { finish() },
                    onApply = { isMy, preset, bg, accent, ids ->
                        val appContext = applicationContext
                        lifecycleScope.launch {
                            // Defence-in-depth: re-check Pro before committing the bind.
                            if (!upgradeRepo.isPro()) {
                                upgradeLauncher.launch(this@ClipboardWidgetConfigActivity)
                                return@launch
                            }
                            applyWidgetConfig(
                                appWidgetId = appWidgetId,
                                newConfig = WidgetInstanceConfig(
                                    isMaterialYou = isMy,
                                    presetName = preset,
                                    customBg = bg,
                                    customAccent = accent,
                                    allowedDeviceIds = ids,
                                ),
                                widgetSettings = widgetSettings,
                                tag = TAG,
                            ) {
                                val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
                                ClipboardGlanceWidget().update(appContext, glanceId)
                            }
                        }
                    },
                    onUpgrade = { upgradeLauncher.launch(this@ClipboardWidgetConfigActivity) },
                    onRetry = { lifecycleScope.launch { upgradeRepo.refresh() } },
                    previewContent = { colors -> ClipboardWidgetPreview(colors = colors) },
                )
            }
        }
    }

    private suspend fun loadAvailableDevices(): List<WidgetConfigDevice> {
        val devices = withTimeoutOrNull(DEVICE_LOAD_TIMEOUT) {
            val metaById = metaRepo.state.first().all.associateBy { it.deviceId }
            val clipState = clipboardRepo.state.first()
            val selfId = (clipState as? BaseModuleRepo.State<*>)?.self?.deviceId?.id
            val now = System.currentTimeMillis()
            clipState.all
                .filter { it.deviceId.id != selfId }
                .mapNotNull { c ->
                    val m = metaById[c.deviceId] ?: return@mapNotNull null
                    WidgetConfigDevice(
                        id = c.deviceId.id,
                        label = m.data.labelOrFallback,
                        subtitle = lastSeenSubtitle(m.modifiedAt.toEpochMilliseconds(), now),
                        icon = composeIconFor(m.data.deviceType),
                    )
                }
        }
            .orEmpty()
            .distinctBy { it.id }

        val labelsByDevice = disambiguateDeviceLabels(devices.associate { DeviceId(it.id) to it.label })
        return devices
            .map { device -> device.copy(label = labelsByDevice[DeviceId(device.id)] ?: device.label) }
            .sortedWith(
                compareBy<WidgetConfigDevice, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
                    .thenBy { it.id },
            )
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
        private val TAG = logTag("Module", "Clipboard", "Widget", "Config")
        private val DEVICE_LOAD_TIMEOUT = 2.seconds
    }
}

private fun composeIconFor(type: MetaInfo.DeviceType): ImageVector = when (type) {
    MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
    MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
    MetaInfo.DeviceType.UNKNOWN -> Icons.TwoTone.QuestionMark
}

@Composable
private fun ClipboardWidgetPreview(colors: WidgetTheme.Colors?) {
    val previewColors = colors ?: widgetDefaultColors()

    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(previewColors.containerBg))
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(previewColors.tileBg)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.widget_device_phone_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(previewColors.onTile),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pixel 8",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(previewColors.onTile),
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hello world\u2026",
                        fontSize = 10.sp,
                        color = Color(previewColors.onTileVariant),
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(previewColors.accentBg)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.widget_clipboard_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(previewColors.onAccent),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.module_clipboard_widget_self_label),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(previewColors.onAccent),
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hello world",
                        fontSize = 11.sp,
                        color = Color(previewColors.onAccent),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
