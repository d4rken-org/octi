package eu.darken.octi.common.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper

@Composable
fun WidgetConfigScreen(
    initialMode: String?,
    initialPresetName: String?,
    initialBgColor: Int?,
    initialAccentColor: Int?,
    availableDevices: List<WidgetConfigDevice>? = null,
    initialSelectedDeviceIds: Set<String> = emptySet(),
    onClose: () -> Unit,
    onApply: (
        isMaterialYou: Boolean,
        presetName: String?,
        bgColor: Int?,
        accentColor: Int?,
        selectedDeviceIds: Set<String>,
    ) -> Unit,
    previewContent: @Composable (WidgetTheme.Colors?) -> Unit,
) {
    val initialTheme = WidgetTheme.fromName(initialPresetName)
    val isInitiallyCustom = initialMode == WidgetInstanceConfig.MODE_CUSTOM && initialTheme == null
    val isInitiallyPreset = initialMode == WidgetInstanceConfig.MODE_CUSTOM && initialTheme != null

    var selectedPreset by rememberSaveable { mutableStateOf(initialTheme ?: WidgetTheme.MATERIAL_YOU) }
    var isCustomMode by rememberSaveable { mutableStateOf(isInitiallyCustom) }
    var bgColor by rememberSaveable { mutableStateOf(initialBgColor) }
    var accentColor by rememberSaveable { mutableStateOf(initialAccentColor) }
    var bgHex by rememberSaveable { mutableStateOf(initialBgColor?.toHexColorString().orEmpty()) }
    var accentHex by rememberSaveable { mutableStateOf(initialAccentColor?.toHexColorString().orEmpty()) }
    var selectedDeviceIds by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(initialSelectedDeviceIds) }

    fun setBgColor(color: Int?) {
        bgColor = color
        bgHex = color?.toHexColorString().orEmpty()
    }

    fun setAccentColor(color: Int?) {
        accentColor = color
        accentHex = color?.toHexColorString().orEmpty()
    }

    fun applyPreset(theme: WidgetTheme) {
        isCustomMode = false
        selectedPreset = theme
        if (theme != WidgetTheme.MATERIAL_YOU) {
            setBgColor(theme.presetBg)
            setAccentColor(theme.presetAccent)
        }
    }

    LaunchedEffect(Unit) {
        if (isInitiallyPreset && bgColor == null) {
            setBgColor(initialTheme.presetBg)
            setAccentColor(initialTheme.presetAccent)
        }
    }

    val isMaterialYou = !isCustomMode && selectedPreset == WidgetTheme.MATERIAL_YOU
    val canApply = isMaterialYou || (bgColor != null && accentColor != null)

    val currentColors = when {
        isMaterialYou -> null
        bgColor != null && accentColor != null -> WidgetTheme.deriveColors(bgColor!!, accentColor!!)
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
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
                            selectedDeviceIds,
                        )
                    },
                    enabled = canApply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.widget_config_apply_action))
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
            Text(
                text = stringResource(R.string.widget_config_preview_label),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            previewContent(currentColors)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.widget_config_presets_label),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))

            WidgetThemePresetSelector(
                selectedPreset = selectedPreset,
                isCustomMode = isCustomMode,
                onPresetSelected = { theme -> applyPreset(theme) },
                onCustomSelected = {
                    isCustomMode = true
                    selectedPreset = WidgetTheme.MATERIAL_YOU
                },
            )

            if (isCustomMode) {
                Spacer(modifier = Modifier.height(16.dp))
                WidgetColorPickerCard(
                    title = stringResource(R.string.widget_config_background_label),
                    selectedColor = bgColor,
                    hexValue = bgHex,
                    onColorSelected = { color -> setBgColor(color) },
                    onHexValueChange = { hex ->
                        bgHex = hex
                        WidgetTheme.parseHexColor(hex)?.let { bgColor = it }
                    },
                    onClear = { setBgColor(null) },
                )

                Spacer(modifier = Modifier.height(16.dp))
                WidgetColorPickerCard(
                    title = stringResource(R.string.widget_config_accent_label),
                    selectedColor = accentColor,
                    hexValue = accentHex,
                    onColorSelected = { color -> setAccentColor(color) },
                    onHexValueChange = { hex ->
                        accentHex = hex
                        WidgetTheme.parseHexColor(hex)?.let { accentColor = it }
                    },
                    onClear = { setAccentColor(null) },
                )
            }

            if (availableDevices != null) {
                Spacer(modifier = Modifier.height(16.dp))
                WidgetDeviceFilterCard(
                    devices = availableDevices,
                    selectedDeviceIds = selectedDeviceIds,
                    onDeviceToggle = { id, checked ->
                        selectedDeviceIds = if (checked) selectedDeviceIds + id else selectedDeviceIds - id
                    },
                )
            }
        }
    }
}

@Composable
private fun WidgetThemePresetSelector(
    selectedPreset: WidgetTheme,
    isCustomMode: Boolean,
    onPresetSelected: (WidgetTheme) -> Unit,
    onCustomSelected: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WidgetTheme.entries.forEach { theme ->
            FilterChip(
                selected = !isCustomMode && selectedPreset == theme,
                onClick = { onPresetSelected(theme) },
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
                } else {
                    null
                },
            )
        }

        FilterChip(
            selected = isCustomMode,
            onClick = onCustomSelected,
            label = { Text(stringResource(R.string.widget_config_custom_label)) },
        )
    }
}

@Composable
private fun WidgetConfigPreviewContent(colors: WidgetTheme.Colors?) {
    val previewColors = colors ?: WidgetTheme.deriveColors(
        bg = WidgetTheme.CLASSIC_GREEN.presetBg!!,
        accent = WidgetTheme.CLASSIC_GREEN.presetAccent!!,
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(previewColors.containerBg)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pixel 9 Pro",
                style = MaterialTheme.typography.titleMedium,
                color = Color(previewColors.onContainer),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(previewColors.tileBg)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(previewColors.accentBg)),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "68% battery",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(previewColors.onContainer),
            )
        }
    }
}

private fun Int.toHexColorString(): String = String.format("%06X", this and 0xFFFFFF)

@Preview2
@Composable
private fun WidgetConfigScreenPreview() = PreviewWrapper {
    WidgetConfigScreen(
        initialMode = WidgetInstanceConfig.MODE_CUSTOM,
        initialPresetName = null,
        initialBgColor = WidgetTheme.BLUE.presetBg,
        initialAccentColor = WidgetTheme.BLUE.presetAccent,
        availableDevices = widgetConfigPreviewDevices(),
        initialSelectedDeviceIds = setOf("pixel-9-pro"),
        onClose = {},
        onApply = { _, _, _, _, _ -> },
        previewContent = { colors -> WidgetConfigPreviewContent(colors) },
    )
}
