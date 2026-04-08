package eu.darken.octi.modules.connectivity.ui.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.theming.ThemeSettings
import eu.darken.octi.common.theming.ThemeState
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.widget.WidgetConfigScreen
import eu.darken.octi.common.widget.WidgetTheme
import eu.darken.octi.modules.connectivity.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NetworkWidgetConfigActivity : androidx.activity.ComponentActivity() {

    @Inject lateinit var themeSettings: ThemeSettings
    @Inject lateinit var upgradeRepo: UpgradeRepo

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
            if (!upgradeRepo.upgradeInfo.first().isPro) {
                setResult(RESULT_CANCELED)
                finish()
                return@launch
            }

            val currentOptions = AppWidgetManager.getInstance(this@NetworkWidgetConfigActivity)
                .getAppWidgetOptions(appWidgetId)
            val initialMode = currentOptions.getString(WidgetTheme.KEY_THEME_MODE)
            val initialPreset = currentOptions.getString(WidgetTheme.KEY_THEME_PRESET)
            val initialBg = if (currentOptions.containsKey(WidgetTheme.KEY_CUSTOM_BG)) {
                currentOptions.getInt(WidgetTheme.KEY_CUSTOM_BG)
            } else null
            val initialAccent = if (currentOptions.containsKey(WidgetTheme.KEY_CUSTOM_ACCENT)) {
                currentOptions.getInt(WidgetTheme.KEY_CUSTOM_ACCENT)
            } else null

            setContent {
                val themeState by themeSettings.themeState.collectAsState(ThemeState())
                OctiTheme(state = themeState) {
                    WidgetConfigScreen(
                        initialMode = initialMode,
                        initialPresetName = initialPreset,
                        initialBgColor = initialBg,
                        initialAccentColor = initialAccent,
                        onClose = { finish() },
                        onApply = { isMaterialYou, presetName, bgColor, accentColor ->
                            applyAndFinish(isMaterialYou, presetName, bgColor, accentColor)
                        },
                        previewContent = { colors -> NetworkWidgetPreview(colors = colors) },
                    )
                }
            }
        }
    }

    private fun applyAndFinish(
        isMaterialYou: Boolean,
        presetName: String?,
        bgColor: Int?,
        accentColor: Int?,
    ) {
        val widgetManager = AppWidgetManager.getInstance(this)
        val options = widgetManager.getAppWidgetOptions(appWidgetId)

        if (isMaterialYou) {
            options.putString(WidgetTheme.KEY_THEME_MODE, WidgetTheme.MODE_MATERIAL_YOU)
            options.putString(WidgetTheme.KEY_THEME_PRESET, WidgetTheme.MATERIAL_YOU.name)
            options.remove(WidgetTheme.KEY_CUSTOM_BG)
            options.remove(WidgetTheme.KEY_CUSTOM_ACCENT)
        } else {
            options.putString(WidgetTheme.KEY_THEME_MODE, WidgetTheme.MODE_CUSTOM)
            options.putString(WidgetTheme.KEY_THEME_PRESET, presetName ?: "")
            val bg = bgColor ?: run { finish(); return }
            val accent = accentColor ?: run { finish(); return }
            options.putInt(WidgetTheme.KEY_CUSTOM_BG, bg)
            options.putInt(WidgetTheme.KEY_CUSTOM_ACCENT, accent)
        }

        widgetManager.updateAppWidgetOptions(appWidgetId, options)

        val appContext = applicationContext
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
                NetworkGlanceWidget().update(appContext, glanceId)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to update widget: ${e.asLog()}" }
            }
        }

        setResult(
            RESULT_OK,
            android.content.Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    companion object {
        private val TAG = logTag("Module", "Connectivity", "Widget", "Config")
    }
}

@Composable
private fun NetworkWidgetPreview(colors: WidgetTheme.Colors?) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(previewColors.barTrack))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.widget_network_wifi_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(previewColors.icon),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pixel 8",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(previewColors.icon),
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "\u00b7 Wi-Fi",
                            fontSize = 10.sp,
                            color = Color(previewColors.icon),
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "192.168.1.5",
                        fontSize = 10.sp,
                        color = Color(previewColors.icon),
                        maxLines = 1,
                    )
                    Text(
                        text = "203.0.113.1",
                        fontSize = 10.sp,
                        color = Color(previewColors.icon),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
