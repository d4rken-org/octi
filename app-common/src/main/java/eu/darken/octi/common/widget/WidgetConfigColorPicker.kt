package eu.darken.octi.common.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper

@Composable
fun WidgetColorPickerCard(
    title: String,
    selectedColor: Int?,
    hexValue: String,
    onColorSelected: (Int) -> Unit,
    onHexValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            WidgetColorSwatchGrid(
                selectedColor = selectedColor,
                onColorSelected = onColorSelected,
            )
            Spacer(modifier = Modifier.height(12.dp))
            WidgetHexColorInput(
                value = hexValue,
                onValueChange = onHexValueChange,
                onClear = onClear,
            )
        }
    }
}

@Composable
private fun WidgetColorSwatchGrid(
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = 6,
    ) {
        WidgetTheme.SWATCH_COLORS.forEach { color ->
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
private fun WidgetHexColorInput(
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
        label = { Text(stringResource(R.string.widget_config_hex_hint)) },
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

@Preview2
@Composable
private fun WidgetColorPickerCardPreview() = PreviewWrapper {
    WidgetColorPickerCard(
        title = stringResource(R.string.widget_config_background_label),
        selectedColor = WidgetTheme.BLUE.presetBg,
        hexValue = "1565C0",
        onColorSelected = {},
        onHexValueChange = {},
        onClear = {},
        modifier = Modifier.padding(16.dp),
    )
}

@Preview2
@Composable
private fun WidgetColorPickerCardEmptyPreview() = PreviewWrapper {
    WidgetColorPickerCard(
        title = stringResource(R.string.widget_config_accent_label),
        selectedColor = null,
        hexValue = "",
        onColorSelected = {},
        onHexValueChange = {},
        onClear = {},
        modifier = Modifier.padding(16.dp),
    )
}
