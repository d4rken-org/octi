package eu.darken.octi.common.error

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.darken.octi.common.R

@Composable
fun ErrorEventHandler(source: ErrorEventSource2) {
    val errorEvents = source.errorEvents2
    var currentError by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(errorEvents) { errorEvents.collect { error -> currentError = error } }

    currentError?.let { error ->
        ComposeErrorDialog(
            throwable = error,
            onDismiss = { currentError = null },
        )
    }
}

@Composable
private fun ComposeErrorDialog(
    throwable: Throwable,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val localizedError = throwable.localized(context)
    val activity = context as? Activity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = localizedError.label.get(context)) },
        text = { Text(text = localizedError.description.get(context)) },
        confirmButton = {
            if (localizedError.fixAction != null && activity != null) {
                TextButton(onClick = {
                    localizedError.fixAction.invoke(activity)
                    onDismiss()
                }) {
                    Text(
                        text = localizedError.fixActionLabel?.get(context)
                            ?: stringResource(android.R.string.ok)
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        },
        dismissButton = when {
            localizedError.fixAction != null -> {
                {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.general_cancel_action))
                    }
                }
            }
            localizedError.infoAction != null && activity != null -> {
                {
                    TextButton(onClick = {
                        localizedError.infoAction.invoke(activity)
                        onDismiss()
                    }) {
                        Text(text = stringResource(R.string.general_show_details_action))
                    }
                }
            }
            else -> null
        },
    )
}
