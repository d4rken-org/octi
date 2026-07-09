package eu.darken.octi.sync.ui.add.intent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.sync.ui.add.SyncSetupRow

@Composable
fun SyncSetupIntentScreenHost(vm: SyncSetupIntentVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    SyncSetupIntentScreen(
        onNew = { vm.goNew() },
        onLink = { vm.goLink() },
        onNavigateUp = { vm.navUp() },
    )
}

@Composable
fun SyncSetupIntentScreen(
    onNew: () -> Unit,
    onLink: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.sync_setup_intent_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SyncSetupRow(
                icon = { modifier ->
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = modifier)
                },
                title = stringResource(R.string.sync_setup_intent_new_title),
                description = stringResource(R.string.sync_setup_intent_new_desc),
                onClick = onNew,
            )

            SyncSetupRow(
                icon = { modifier ->
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = modifier)
                },
                title = stringResource(R.string.sync_setup_intent_link_title),
                description = stringResource(R.string.sync_setup_intent_link_desc),
                onClick = onLink,
            )
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            text = { Text(text = stringResource(R.string.sync_add_help_desc)) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(text = stringResource(CommonR.string.general_gotit_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHelpDialog = false
                    WebpageTool(context).open("https://github.com/d4rken-org/octi/wiki/Syncs")
                }) {
                    Text(text = stringResource(R.string.documentation_label))
                }
            },
        )
    }
}

@Preview2
@Composable
private fun SyncSetupIntentScreenPreview() = PreviewWrapper {
    SyncSetupIntentScreen(
        onNew = {},
        onLink = {},
        onNavigateUp = {},
    )
}
