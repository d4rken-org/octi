package eu.darken.octi.common.error

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag

@Composable
fun ErrorEventHandler(source: ErrorEventSource) {
    val errorEvents = source.errorEvents
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

    // Keyed on the throwable, not the LocalizedError: the latter is rebuilt (with fresh action
    // lambdas, so never equal) on every recomposition, which would wipe the message immediately.
    var actionError by remember(throwable) { mutableStateOf<CaString?>(null) }

    // errorMessage is per-dispatch, NOT read from localizedError: this function serves both the fix
    // and the info button, and fixActionErrorMessage describes only the fix action's failure. Each
    // call site passes its own copy (or none), so no button can ever surface another one's message.
    fun dispatchAndDismiss(
        action: (Activity) -> Unit,
        errorMessage: CaString? = null,
    ) {
        // Error actions are arbitrary third-party code (intent launches, deep links): a throw here
        // would crash the UI thread from inside a click handler, and skipping onDismiss() would
        // leave the dialog latched on the current error with no way out.
        try {
            activity?.let { action(it) }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Error action failed: ${e.asLog()}" }
            // A dispatch that ships its own failure copy keeps the dialog open and shows it inline
            // (no length cap, unlike a Toast). Never latched: the dismiss button stays available.
            errorMessage?.let {
                actionError = it
                return
            }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = localizedError.label.get(context)) },
        text = {
            Column {
                Text(text = localizedError.description.get(context))
                actionError?.let {
                    Text(
                        text = it.get(context),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (localizedError.fixAction != null && activity != null) {
                TextButton(
                    onClick = {
                        dispatchAndDismiss(
                            action = localizedError.fixAction,
                            errorMessage = localizedError.fixActionErrorMessage,
                        )
                    },
                ) {
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
                        Text(text = stringResource(R.string.general_dismiss_action))
                    }
                }
            }
            localizedError.infoAction != null && activity != null -> {
                {
                    // No errorMessage: the info action has no failure copy of its own, and it must
                    // never borrow the fix action's.
                    TextButton(onClick = { dispatchAndDismiss(action = localizedError.infoAction) }) {
                        Text(text = stringResource(R.string.general_show_details_action))
                    }
                }
            }
            else -> null
        },
    )
}

private val TAG = logTag("Error", "EventHandler")
