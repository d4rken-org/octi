package eu.darken.octi.syncs.kserver.ui.link.client

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import eu.darken.octi.syncs.kserver.R as KServerR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.syncs.kserver.ui.link.KServerLinkOption

@Composable
fun KServerLinkClientScreenHost(vm: KServerLinkClientVM = hiltViewModel()) {
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
        KServerLinkClientScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KServerLinkClientScreen(
    state: KServerLinkClientVM.State,
    onNavigateUp: () -> Unit,
    onLinkOptionSelected: (KServerLinkOption) -> Unit,
    onCodeEntered: (String) -> Unit,
    onStartCamera: () -> Unit,
) {
    var linkCodeText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(KServerR.string.sync_kserver_type_label))
                        Text(
                            text = stringResource(KServerR.string.sync_kserver_link_device_action),
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
                                    selected = state.linkOption == KServerLinkOption.QRCODE,
                                    role = Role.RadioButton,
                                    onClick = { onLinkOptionSelected(KServerLinkOption.QRCODE) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.linkOption == KServerLinkOption.QRCODE,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(KServerR.string.sync_kserver_link_client_option_qrcode),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.linkOption == KServerLinkOption.DIRECT,
                                    role = Role.RadioButton,
                                    onClick = { onLinkOptionSelected(KServerLinkOption.DIRECT) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.linkOption == KServerLinkOption.DIRECT,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(KServerR.string.sync_kserver_link_client_option_direct),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                when (state.linkOption) {
                    KServerLinkOption.QRCODE -> {
                        Column(
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Button(
                                onClick = onStartCamera,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(KServerR.string.sync_kserver_link_client_startcamera_action))
                            }
                        }
                    }

                    KServerLinkOption.DIRECT -> {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OutlinedTextField(
                                value = linkCodeText,
                                onValueChange = { linkCodeText = it },
                                label = { Text(text = stringResource(KServerR.string.sync_kserver_link_code_label)) },
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
                                Text(text = stringResource(KServerR.string.sync_kserver_link_client_link_action))
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

private val TAG = logTag("Sync", "KServer", "Link", "Client", "Screen")

@Preview2
@Composable
private fun KServerLinkClientScreenQRPreview() = PreviewWrapper {
    KServerLinkClientScreen(
        state = KServerLinkClientVM.State(linkOption = KServerLinkOption.QRCODE),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onCodeEntered = {},
        onStartCamera = {},
    )
}

@Preview2
@Composable
private fun KServerLinkClientScreenDirectPreview() = PreviewWrapper {
    KServerLinkClientScreen(
        state = KServerLinkClientVM.State(linkOption = KServerLinkOption.DIRECT),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onCodeEntered = {},
        onStartCamera = {},
    )
}

@Preview2
@Composable
private fun KServerLinkClientScreenBusyPreview() = PreviewWrapper {
    KServerLinkClientScreen(
        state = KServerLinkClientVM.State(isBusy = true),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onCodeEntered = {},
        onStartCamera = {},
    )
}
