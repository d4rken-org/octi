package eu.darken.octi.common.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R
import eu.darken.octi.common.preferences.EnumPreference

@Composable
fun <T> SortModeDialog(
    title: String,
    currentMode: T,
    modes: List<T>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    reversed: Boolean = false,
    onReverseChange: ((Boolean) -> Unit)? = null,
) where T : Enum<T>, T : EnumPreference<T> {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                modes.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onSelect(mode) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = mode.label.get(context))
                    }
                }

                if (onReverseChange != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .clickable { onReverseChange(!reversed) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.general_sort_reverse_label),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = reversed,
                            onCheckedChange = onReverseChange,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
