package eu.darken.octi.main.ui.settings.support

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.debug.recording.core.DebugSession
import eu.darken.octi.common.debug.recording.core.LogSession
import eu.darken.octi.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSessionsSheet(
    sessions: List<DebugSession>,
    onDismiss: () -> Unit,
    onDeleteSession: (LogSession) -> Unit,
    onOpenSession: (LogSession) -> Unit,
    onStopRecording: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.support_debuglog_sessions_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDeleteAll) {
                    Text(text = stringResource(CommonR.string.general_delete_action))
                }
            }

            LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
                items(
                    items = sessions,
                    key = { it.session.sessionDir.absolutePath },
                ) { debugSession ->
                    DebugSessionRow(
                        debugSession = debugSession,
                        onDelete = { onDeleteSession(debugSession.session) },
                        onOpen = { onOpenSession(debugSession.session) },
                        onStop = onStopRecording,
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugSessionRow(
    debugSession: DebugSession,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val isClickable = debugSession is DebugSession.Ready

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading icon
        when (debugSession) {
            is DebugSession.Recording -> Icon(
                imageVector = Icons.TwoTone.Stop,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )

            is DebugSession.Compressing -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )

            is DebugSession.Ready -> Icon(
                imageVector = Icons.TwoTone.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            is DebugSession.Failed -> Icon(
                imageVector = Icons.TwoTone.Error,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (debugSession) {
                    is DebugSession.Recording -> stringResource(R.string.support_debuglog_session_recording_label)
                    is DebugSession.Compressing -> stringResource(R.string.support_debuglog_session_compressing_label)
                    is DebugSession.Ready -> DateUtils.getRelativeTimeSpanString(
                        debugSession.lastModified,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                    is DebugSession.Failed -> stringResource(R.string.support_debuglog_session_failed_label)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = Formatter.formatShortFileSize(context, debugSession.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Trailing action
        when (debugSession) {
            is DebugSession.Recording -> IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.TwoTone.Stop,
                    contentDescription = stringResource(R.string.debug_debuglog_stop_action),
                )
            }

            is DebugSession.Compressing -> { /* no trailing action */ }

            is DebugSession.Ready -> IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.TwoTone.Delete,
                    contentDescription = stringResource(CommonR.string.general_delete_action),
                )
            }

            is DebugSession.Failed -> IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.TwoTone.Delete,
                    contentDescription = stringResource(CommonR.string.general_delete_action),
                )
            }
        }
    }
}
