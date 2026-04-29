package eu.darken.octi.sync.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.navigation.NavigationEventHandler
import androidx.compose.ui.graphics.Color
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorPauseReason
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState

@Composable
fun SyncAddScreenHost(vm: SyncAddVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        SyncAddScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
        )
    }
}

@Composable
fun SyncAddScreen(
    state: SyncAddVM.State,
    onNavigateUp: () -> Unit,
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.sync_add_label)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(state.items, key = { it.contribution.type.name }) { item ->
                SyncAddItemRow(item = item)
            }
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

@Composable
private fun SyncAddItemRow(item: SyncAddVM.SyncAddItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        item.contribution.Icon(modifier = Modifier.size(20.dp))

        Spacer(modifier = Modifier.width(4.dp))

        Column {
            Text(
                text = stringResource(item.contribution.labelRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(item.contribution.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview2
@Composable
private fun SyncAddScreenPreview() = PreviewWrapper {
    SyncAddScreen(
        state = SyncAddVM.State(
            items = listOf(
                SyncAddVM.SyncAddItem(
                    contribution = previewContribution(
                        type = ConnectorType.GDRIVE,
                        labelRes = R.string.sync_add_label,
                        descriptionRes = R.string.sync_add_help_desc,
                    ),
                    onClick = {},
                ),
                SyncAddVM.SyncAddItem(
                    contribution = previewContribution(
                        type = ConnectorType.OCTISERVER,
                        labelRes = R.string.sync_add_label,
                        descriptionRes = R.string.sync_add_help_desc,
                    ),
                    onClick = {},
                ),
            ),
        ),
        onNavigateUp = {},
    )
}

private fun previewContribution(
    type: ConnectorType,
    labelRes: Int,
    descriptionRes: Int,
): ConnectorUiContribution = object : ConnectorUiContribution {
    override val type = type
    override val displayOrder = 0
    override val labelRes = labelRes
    override val descriptionRes = descriptionRes
    @Composable override fun Icon(modifier: Modifier, tint: Color) {}
    override fun addAccountDestination(): NavigationDestination = Nav.Sync.Add
    @Composable override fun listCardTitle(connector: SyncConnector): String = ""
    @Composable override fun listCardAccountValue(connector: SyncConnector): String = ""
    @Composable override fun ActionsSheet(
        connector: SyncConnector,
        state: SyncConnectorState,
        isPaused: Boolean,
        pauseReason: ConnectorPauseReason?,
        isPro: Boolean,
        onDismiss: () -> Unit,
        onTogglePause: () -> Unit,
        onForceSync: () -> Unit,
        onViewDevices: () -> Unit,
        onLinkNewDevice: () -> Unit,
        onReset: () -> Unit,
        onDisconnect: () -> Unit,
    ) {}
}
