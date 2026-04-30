package eu.darken.octi.syncs.octiserver.ui.add

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.syncs.octiserver.R as OctiServerR
import eu.darken.octi.syncs.octiserver.core.OctiServer

@Composable
fun AddOctiServerScreenHost(vm: AddOctiServerVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        AddOctiServerScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onSelectType = { type -> vm.selectType(type) },
            onCreateAccount = { customServer -> vm.createAccount(customServer) },
            onLinkAccount = { vm.linkAccount() },
        )
    }
}

@Composable
fun AddOctiServerScreen(
    state: AddOctiServerVM.State,
    onNavigateUp: () -> Unit,
    onSelectType: (OctiServer.Official?) -> Unit,
    onCreateAccount: (String?) -> Unit,
    onLinkAccount: () -> Unit,
) {
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var customServerText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(OctiServerR.string.sync_octiserver_type_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(OctiServerR.string.sync_octiserver_add_select_server_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )

            Column(modifier = Modifier.selectableGroup()) {
                ServerRadioOption(
                    text = "${OctiServer.Official.PROD.address.domain} (Production)",
                    selected = state.serverType == OctiServer.Official.PROD,
                    enabled = !state.isBusy,
                    onClick = { onSelectType(OctiServer.Official.PROD) },
                )

                if (BuildConfigWrap.DEBUG) {
                    ServerRadioOption(
                        text = "${OctiServer.Official.LOCAL.address.domain} (local)",
                        selected = state.serverType == OctiServer.Official.LOCAL,
                        enabled = !state.isBusy,
                        onClick = { onSelectType(OctiServer.Official.LOCAL) },
                    )
                }

                ServerRadioOption(
                    text = stringResource(OctiServerR.string.sync_octiserver_add_custom_server_label),
                    selected = state.serverType == null,
                    enabled = !state.isBusy,
                    onClick = { onSelectType(null) },
                )
            }

            if (state.serverType == null) {
                OutlinedTextField(
                    value = customServerText,
                    onValueChange = { customServerText = it },
                    label = { Text("protocol://server:port") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showCreateDialog = true },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            ) {
                Text(text = stringResource(OctiServerR.string.sync_octiserver_add_create_account_action))
            }

            Text(
                text = stringResource(OctiServerR.string.sync_octiserver_add_create_action_hint),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = onLinkAccount,
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            ) {
                Text(text = stringResource(OctiServerR.string.sync_octiserver_add_link_existing_action))
            }

            Text(
                text = stringResource(OctiServerR.string.sync_octiserver_add_link_action_hint),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showCreateDialog) {
        CreateAccountDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                showCreateDialog = false
                onCreateAccount(if (state.serverType == null) customServerText else null)
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(text = stringResource(OctiServerR.string.sync_octiserver_about_title)) },
            text = { Text(text = stringResource(OctiServerR.string.sync_octiserver_about_desc)) },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(text = stringResource(CommonR.string.general_gotit_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAboutDialog = false
                    WebpageTool(context).open("https://github.com/d4rken-org/octi-server")
                }) {
                    Text(text = stringResource(OctiServerR.string.sync_octiserver_about_source_action))
                }
            },
        )
    }
}

@Composable
private fun CreateAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(OctiServerR.string.sync_octiserver_add_create_account_title)) },
        text = { Text(text = stringResource(OctiServerR.string.sync_octiserver_add_create_account_desc)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(OctiServerR.string.sync_octiserver_add_create_account_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}

@Composable
private fun ServerRadioOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Preview2
@Composable
private fun AddOctiServerScreenPreview() = PreviewWrapper {
    AddOctiServerScreen(
        state = AddOctiServerVM.State(serverType = OctiServer.Official.PROD),
        onNavigateUp = {},
        onSelectType = {},
        onCreateAccount = {},
        onLinkAccount = {},
    )
}

@Preview2
@Composable
private fun AddOctiServerScreenCustomPreview() = PreviewWrapper {
    AddOctiServerScreen(
        state = AddOctiServerVM.State(serverType = null),
        onNavigateUp = {},
        onSelectType = {},
        onCreateAccount = {},
        onLinkAccount = {},
    )
}

@Preview2
@Composable
private fun AddOctiServerScreenBusyPreview() = PreviewWrapper {
    AddOctiServerScreen(
        state = AddOctiServerVM.State(serverType = OctiServer.Official.PROD, isBusy = true),
        onNavigateUp = {},
        onSelectType = {},
        onCreateAccount = {},
        onLinkAccount = {},
    )
}
