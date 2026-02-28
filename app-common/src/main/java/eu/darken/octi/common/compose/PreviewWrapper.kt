package eu.darken.octi.common.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import eu.darken.octi.common.theming.OctiTheme

@Composable
fun PreviewWrapper(content: @Composable () -> Unit) {
    OctiTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
