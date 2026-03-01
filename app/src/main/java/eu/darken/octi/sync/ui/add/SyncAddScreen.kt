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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.syncs.gdrive.R as GDriveR
import eu.darken.octi.syncs.kserver.R as KServerR
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler

@Composable
fun SyncAddScreenHost(vm: SyncAddVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        SyncAddScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
            items(state.items, key = { it.type.name }) { item ->
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
        val (iconRes, titleRes, descRes) = when (item.type) {
            SyncAddVM.SyncType.GDRIVE -> Triple(
                R.drawable.ic_baseline_gdrive_24,
                GDriveR.string.sync_gdrive_type_label,
                GDriveR.string.sync_gdrive_type_appdata_description,
            )

            SyncAddVM.SyncType.KSERVER -> Triple(
                R.drawable.ic_baseline_outdoor_grill_24,
                KServerR.string.sync_kserver_type_label,
                KServerR.string.sync_kserver_type_description,
            )
        }

        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(4.dp))

        Column {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(descRes),
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
                SyncAddVM.SyncAddItem(type = SyncAddVM.SyncType.GDRIVE, onClick = {}),
                SyncAddVM.SyncAddItem(type = SyncAddVM.SyncType.KSERVER, onClick = {}),
            ),
        ),
        onNavigateUp = {},
    )
}
