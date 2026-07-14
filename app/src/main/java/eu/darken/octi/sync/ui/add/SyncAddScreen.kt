package eu.darken.octi.sync.ui.add

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import eu.darken.octi.sync.core.ConnectorOperation
import eu.darken.octi.sync.core.ConnectorPauseReason
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.ui.add.SyncAddVM.Companion.forMode

@Composable
fun SyncAddScreenHost(
    mode: Nav.Sync.AddPicker.Mode,
    vm: SyncAddVM = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        SyncAddScreen(
            state = it,
            mode = mode,
            onContributionClicked = { contribution -> vm.onContributionClicked(contribution, mode) },
            onNavigateUp = { vm.navUp() },
        )
    }
}

@Composable
fun SyncAddScreen(
    state: SyncAddVM.State,
    mode: Nav.Sync.AddPicker.Mode,
    onContributionClicked: (ConnectorUiContribution) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val items = remember(state.contributions, mode) { state.contributions.forMode(mode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when (mode) {
                                Nav.Sync.AddPicker.Mode.CREATE -> R.string.sync_setup_intent_new_title
                                Nav.Sync.AddPicker.Mode.LINK -> R.string.sync_setup_intent_link_title
                            }
                        )
                    )
                },
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
            item(key = "header") {
                Text(
                    text = stringResource(
                        when (mode) {
                            Nav.Sync.AddPicker.Mode.CREATE -> R.string.sync_add_pick_create_header
                            Nav.Sync.AddPicker.Mode.LINK -> R.string.sync_add_pick_link_header
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (items.isEmpty() && mode == Nav.Sync.AddPicker.Mode.LINK) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.sync_add_link_none_available),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
            } else {
                items(items, key = { it.type.name }) { contribution ->
                    SyncSetupRow(
                        icon = { modifier -> contribution.Icon(modifier = modifier) },
                        title = stringResource(contribution.labelRes),
                        description = stringResource(contribution.descriptionRes),
                        onClick = { onContributionClicked(contribution) },
                    )
                }
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

@Preview2
@Composable
private fun SyncAddScreenCreatePreview() = PreviewWrapper {
    SyncAddScreen(
        state = SyncAddVM.State(
            contributions = listOf(
                previewContribution(
                    type = ConnectorType.GDRIVE,
                    labelRes = R.string.sync_add_label,
                    descriptionRes = R.string.sync_add_help_desc,
                ),
                previewContribution(
                    type = ConnectorType.OCTISERVER,
                    labelRes = R.string.sync_add_label,
                    descriptionRes = R.string.sync_add_help_desc,
                ),
            ),
        ),
        mode = Nav.Sync.AddPicker.Mode.CREATE,
        onContributionClicked = {},
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun SyncAddScreenLinkPreview() = PreviewWrapper {
    SyncAddScreen(
        state = SyncAddVM.State(
            contributions = listOf(
                previewContribution(
                    type = ConnectorType.GDRIVE,
                    labelRes = R.string.sync_add_label,
                    descriptionRes = R.string.sync_add_help_desc,
                    joinDestination = Nav.Sync.AddGDrive(linking = true),
                ),
                previewContribution(
                    type = ConnectorType.OCTISERVER,
                    labelRes = R.string.sync_add_label,
                    descriptionRes = R.string.sync_add_help_desc,
                    joinDestination = Nav.Sync.OctiServerLinkClient,
                ),
            ),
        ),
        mode = Nav.Sync.AddPicker.Mode.LINK,
        onContributionClicked = {},
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun SyncAddScreenLinkEmptyPreview() = PreviewWrapper {
    SyncAddScreen(
        state = SyncAddVM.State(
            contributions = listOf(
                previewContribution(
                    type = ConnectorType.GDRIVE,
                    labelRes = R.string.sync_add_label,
                    descriptionRes = R.string.sync_add_help_desc,
                ),
            ),
        ),
        mode = Nav.Sync.AddPicker.Mode.LINK,
        onContributionClicked = {},
        onNavigateUp = {},
    )
}

private fun previewContribution(
    type: ConnectorType,
    labelRes: Int,
    descriptionRes: Int,
    joinDestination: NavigationDestination? = null,
): ConnectorUiContribution = object : ConnectorUiContribution {
    override val type = type
    override val displayOrder = 0
    override val labelRes = labelRes
    override val descriptionRes = descriptionRes
    @Composable override fun Icon(modifier: Modifier, tint: Color) {}
    override fun addAccountDestination(): NavigationDestination = Nav.Sync.AddPicker(Nav.Sync.AddPicker.Mode.CREATE)
    override fun joinDeviceDestination(): NavigationDestination? = joinDestination
    @Composable override fun listCardTitle(connector: SyncConnector): String = ""
    @Composable override fun listCardAccountValue(connector: SyncConnector): String = ""
    @Composable override fun ActionsSheet(
        connector: SyncConnector,
        state: SyncConnectorState,
        activeOperations: List<ConnectorOperation>,
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
