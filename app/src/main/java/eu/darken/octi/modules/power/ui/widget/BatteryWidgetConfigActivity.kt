package eu.darken.octi.modules.power.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.R
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.theming.ThemeState
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.themeState
import javax.inject.Inject
import eu.darken.octi.modules.power.R as PowerR

@AndroidEntryPoint
class BatteryWidgetConfigActivity : androidx.activity.ComponentActivity() {

    @Inject lateinit var generalSettings: GeneralSettings

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

        val currentOptions = AppWidgetManager.getInstance(this).getAppWidgetOptions(appWidgetId)
        val initialMode = currentOptions.getString(WidgetTheme.KEY_THEME_MODE)
        val initialPreset = currentOptions.getString(WidgetTheme.KEY_THEME_PRESET)
        val initialBg = if (currentOptions.containsKey(WidgetTheme.KEY_CUSTOM_BG)) {
            currentOptions.getInt(WidgetTheme.KEY_CUSTOM_BG)
        } else null
        val initialAccent = if (currentOptions.containsKey(WidgetTheme.KEY_CUSTOM_ACCENT)) {
            currentOptions.getInt(WidgetTheme.KEY_CUSTOM_ACCENT)
        } else null

        setContent {
            val themeState by generalSettings.themeState.collectAsState(ThemeState())
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
                )
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

        sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            component = ComponentName(this@BatteryWidgetConfigActivity, BatteryWidgetProvider::class.java)
        })

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    companion object {
        fun parseHexColor(input: String?): Int? {
            if (input.isNullOrBlank()) return null
            val cleaned = input.trim().removePrefix("#").uppercase()
            if (cleaned.length != 6) return null
            if (!cleaned.matches(Regex("[0-9A-F]{6}"))) return null
            return try {
                (0xFF000000 or cleaned.toLong(16)).toInt()
            } catch (_: NumberFormatException) {
                null
            }
        }

        val SWATCH_COLORS = intArrayOf(
            0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
            0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
            0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(),
            0xFF795548.toInt(), 0xFF9E9E9E.toInt(), 0xFF607D8B.toInt(), 0xFFFFFFFF.toInt(),
            0xFF1E1E1E.toInt(), 0xFF263238.toInt(), 0xFF1B5E20.toInt(), 0xFF0D47A1.toInt(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WidgetConfigScreen(
    initialMode: String?,
    initialPresetName: String?,
    initialBgColor: Int?,
    initialAccentColor: Int?,
    onClose: () -> Unit,
    onApply: (isMaterialYou: Boolean, presetName: String?, bgColor: Int?, accentColor: Int?) -> Unit,
) {
    val initialTheme = WidgetTheme.fromName(initialPresetName)
    val isInitiallyCustom = initialMode == WidgetTheme.MODE_CUSTOM && initialTheme == null
    val isInitiallyPreset = initialMode == WidgetTheme.MODE_CUSTOM && initialTheme != null

    var selectedPreset by rememberSaveable { mutableStateOf(initialTheme ?: WidgetTheme.MATERIAL_YOU) }
    var isCustomMode by rememberSaveable { mutableStateOf(isInitiallyCustom) }
    var bgColor by rememberSaveable { mutableStateOf(initialBgColor) }
    var accentColor by rememberSaveable { mutableStateOf(initialAccentColor) }
    var bgHex by rememberSaveable { mutableStateOf(initialBgColor?.let { String.format("%06X", it and 0xFFFFFF) } ?: "") }
    var accentHex by rememberSaveable { mutableStateOf(initialAccentColor?.let { String.format("%06X", it and 0xFFFFFF) } ?: "") }

    // Initialize preset colors if coming from a preset (runs once)
    LaunchedEffect(Unit) {
        if (isInitiallyPreset && bgColor == null) {
            bgColor = initialTheme?.presetBg
            accentColor = initialTheme?.presetAccent
        }
    }

    val isMaterialYou = !isCustomMode && selectedPreset == WidgetTheme.MATERIAL_YOU
    val canApply = isMaterialYou || (bgColor != null && accentColor != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(PowerR.string.module_power_widget_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                HorizontalDivider()
                Button(
                    onClick = {
                        onApply(
                            isMaterialYou,
                            if (isCustomMode) null else selectedPreset.name,
                            bgColor,
                            accentColor,
                        )
                    },
                    enabled = canApply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(PowerR.string.module_power_widget_config_apply_action))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Preview
            Text(
                text = stringResource(PowerR.string.module_power_widget_config_preview_label),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            WidgetPreview(
                isMaterialYou = isMaterialYou,
                bgColor = bgColor,
                accentColor = accentColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Presets
            Text(
                text = stringResource(PowerR.string.module_power_widget_config_presets_label),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WidgetTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = !isCustomMode && selectedPreset == theme,
                        onClick = {
                            isCustomMode = false
                            selectedPreset = theme
                            if (theme != WidgetTheme.MATERIAL_YOU) {
                                bgColor = theme.presetBg
                                accentColor = theme.presetAccent
                                bgHex = theme.presetBg?.let { String.format("%06X", it and 0xFFFFFF) } ?: ""
                                accentHex = theme.presetAccent?.let { String.format("%06X", it and 0xFFFFFF) } ?: ""
                            }
                        },
                        label = { Text(stringResource(theme.labelRes)) },
                        leadingIcon = if (theme.presetBg != null) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(theme.presetBg)),
                                )
                            }
                        } else null,
                    )
                }

                FilterChip(
                    selected = isCustomMode,
                    onClick = {
                        isCustomMode = true
                        selectedPreset = WidgetTheme.MATERIAL_YOU // reset preset
                    },
                    label = { Text(stringResource(PowerR.string.module_power_widget_config_custom_label)) },
                )
            }

            // Custom colors
            if (isCustomMode) {
                Spacer(modifier = Modifier.height(16.dp))

                // Background color
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(PowerR.string.module_power_widget_config_background_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ColorSwatchGrid(
                            selectedColor = bgColor,
                            onColorSelected = { color ->
                                bgColor = color
                                bgHex = String.format("%06X", color and 0xFFFFFF)
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HexColorInput(
                            value = bgHex,
                            onValueChange = { hex ->
                                bgHex = hex
                                val color = BatteryWidgetConfigActivity.parseHexColor(hex)
                                if (color != null) bgColor = color
                            },
                            onClear = {
                                bgHex = ""
                                bgColor = null
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Accent color
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(PowerR.string.module_power_widget_config_accent_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ColorSwatchGrid(
                            selectedColor = accentColor,
                            onColorSelected = { color ->
                                accentColor = color
                                accentHex = String.format("%06X", color and 0xFFFFFF)
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HexColorInput(
                            value = accentHex,
                            onValueChange = { hex ->
                                accentHex = hex
                                val color = BatteryWidgetConfigActivity.parseHexColor(hex)
                                if (color != null) accentColor = color
                            },
                            onClear = {
                                accentHex = ""
                                accentColor = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchGrid(
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = 6,
    ) {
        BatteryWidgetConfigActivity.SWATCH_COLORS.forEach { color ->
            val isSelected = selectedColor == color
            val contrastColor = WidgetTheme.bestContrast(color)

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(color))
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                BorderStroke(3.dp, Color(contrastColor)),
                                RoundedCornerShape(8.dp),
                            )
                        } else {
                            Modifier.border(
                                BorderStroke(1.dp, Color(0x33000000)),
                                RoundedCornerShape(8.dp),
                            )
                        }
                    )
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(contrastColor),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HexColorInput(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
            onValueChange(filtered)
        },
        label = { Text(stringResource(PowerR.string.module_power_widget_config_hex_hint)) },
        prefix = { Text("#") },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WidgetPreview(
    isMaterialYou: Boolean,
    bgColor: Int?,
    accentColor: Int?,
) {
    val context = LocalContext.current

    val previewBg = when {
        isMaterialYou -> colorResource(R.color.widgetContainerBackground).toArgb()
        bgColor != null -> bgColor
        else -> return
    }

    val colors = when {
        isMaterialYou -> WidgetTheme.Colors(
            containerBg = previewBg,
            barFill = colorResource(R.color.widgetBarFill).toArgb(),
            barTrack = colorResource(R.color.widgetBarTrack).toArgb(),
            icon = colorResource(R.color.widgetBarIcon).toArgb(),
            onContainer = colorResource(R.color.widgetOnContainer).toArgb(),
        )
        bgColor != null && accentColor != null -> WidgetTheme.deriveColors(bgColor, accentColor)
        else -> null
    }

    // Use RemoteViews for accurate preview — matches actual widget rendering
    Card(shape = RoundedCornerShape(16.dp)) {
        if (LocalInspectionMode.current) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(previewBg))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Widget Preview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (colors != null) Color(colors.onContainer) else MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(previewBg))
                    .padding(8.dp),
                factory = { ctx ->
                    val row = RemoteViews(ctx.packageName, R.layout.module_power_widget_row)
                    row.apply {
                        val name = "Pixel 8"
                        val label = SpannableStringBuilder().apply {
                            append(name)
                            setSpan(
                                StyleSpan(Typeface.BOLD),
                                0,
                                name.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                            append(" \u00b7 5 min ago")
                        }
                        setTextViewText(R.id.device_label, label)
                        setTextViewText(R.id.charge_percent, "75%")
                        setProgressBar(R.id.battery_progressbar, 100, 75, false)
                    }
                    row.apply(ctx, android.widget.FrameLayout(ctx))
                },
                update = { view ->
                    if (colors != null) {
                        view.findViewById<TextView>(R.id.device_label)?.setTextColor(colors.icon)
                        view.findViewById<ImageView>(R.id.battery_icon)?.setColorFilter(colors.icon)
                        view.findViewById<TextView>(R.id.charge_percent)?.setTextColor(colors.onContainer)
                        view.findViewById<ProgressBar>(R.id.battery_progressbar)?.apply {
                            progressTintList =
                                android.content.res.ColorStateList.valueOf(colors.barFill)
                            progressBackgroundTintList =
                                android.content.res.ColorStateList.valueOf(colors.barTrack)
                        }
                    }
                },
            )
        }
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
        onApply = { _, _, _, _ -> },
    )
}

@Preview2
@Composable
private fun WidgetConfigScreenCustomPreview() = PreviewWrapper {
    WidgetConfigScreen(
        initialMode = WidgetTheme.MODE_CUSTOM,
        initialPresetName = WidgetTheme.BLUE.name,
        initialBgColor = WidgetTheme.BLUE.presetBg,
        initialAccentColor = WidgetTheme.BLUE.presetAccent,
        onClose = {},
        onApply = { _, _, _, _ -> },
    )
}
