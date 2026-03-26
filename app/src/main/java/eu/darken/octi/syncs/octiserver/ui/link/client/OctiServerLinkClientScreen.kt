package eu.darken.octi.syncs.octiserver.ui.link.client

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.syncs.octiserver.R as OctiServerR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.syncs.octiserver.ui.link.OctiServerLinkOption

@Composable
fun OctiServerLinkClientScreenHost(vm: OctiServerLinkClientVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            log(TAG) { "QRCode scanned: ${result.contents}" }
            vm.onCodeEntered(result.contents)
        } else {
            log(TAG) { "QRCode scan was cancelled." }
        }
    }

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        OctiServerLinkClientScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onLinkOptionSelected = { option -> vm.onLinkOptionSelected(option) },
            onCodeEntered = { code -> vm.onCodeEntered(code) },
            onStartCamera = {
                val options = ScanOptions().apply {
                    setOrientationLocked(false)
                }
                barcodeLauncher.launch(options)
            },
        )
    }
}

@Composable
fun OctiServerLinkClientScreen(
    state: OctiServerLinkClientVM.State,
    onNavigateUp: () -> Unit,
    onLinkOptionSelected: (OctiServerLinkOption) -> Unit,
    onCodeEntered: (String) -> Unit,
    onStartCamera: () -> Unit,
) {
    var linkCodeText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(OctiServerR.string.sync_octiserver_type_label))
                        Text(
                            text = stringResource(OctiServerR.string.sync_octiserver_link_device_action),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .selectableGroup(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.linkOption == OctiServerLinkOption.QRCODE,
                                    role = Role.RadioButton,
                                    onClick = { onLinkOptionSelected(OctiServerLinkOption.QRCODE) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.linkOption == OctiServerLinkOption.QRCODE,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(OctiServerR.string.sync_octiserver_link_client_option_qrcode),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.linkOption == OctiServerLinkOption.DIRECT,
                                    role = Role.RadioButton,
                                    onClick = { onLinkOptionSelected(OctiServerLinkOption.DIRECT) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.linkOption == OctiServerLinkOption.DIRECT,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(OctiServerR.string.sync_octiserver_link_client_option_direct),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                when (state.linkOption) {
                    OctiServerLinkOption.QRCODE -> {
                        Column(
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Button(
                                onClick = onStartCamera,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(OctiServerR.string.sync_octiserver_link_client_startcamera_action))
                            }
                        }
                    }

                    OctiServerLinkOption.DIRECT -> {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OutlinedTextField(
                                value = linkCodeText,
                                onValueChange = { linkCodeText = it },
                                label = { Text(text = stringResource(OctiServerR.string.sync_octiserver_link_code_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { onCodeEntered(linkCodeText) }),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { onCodeEntered(linkCodeText) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(OctiServerR.string.sync_octiserver_link_client_link_action))
                            }
                        }
                    }
                }
            }

            if (state.isBusy) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private val TAG = logTag("Sync", "OctiServer", "Link", "Client", "Screen")

@Preview2
@Composable
private fun OctiServerLinkClientScreenQRPreview() = PreviewWrapper {
    OctiServerLinkClientScreen(
        state = OctiServerLinkClientVM.State(linkOption = OctiServerLinkOption.QRCODE),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onCodeEntered = {},
        onStartCamera = {},
    )
}

@Preview2
@Composable
private fun OctiServerLinkClientScreenDirectPreview() = PreviewWrapper {
    OctiServerLinkClientScreen(
        state = OctiServerLinkClientVM.State(linkOption = OctiServerLinkOption.DIRECT),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onCodeEntered = {},
        onStartCamera = {},
    )
}

@Preview2
@Composable
private fun OctiServerLinkClientScreenBusyPreview() = PreviewWrapper {
    OctiServerLinkClientScreen(
        state = OctiServerLinkClientVM.State(isBusy = true),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onCodeEntered = {},
        onStartCamera = {},
    )
}
