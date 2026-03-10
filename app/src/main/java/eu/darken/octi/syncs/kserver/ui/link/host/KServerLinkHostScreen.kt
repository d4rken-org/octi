package eu.darken.octi.syncs.kserver.ui.link.host

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.syncs.kserver.R as KServerR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.syncs.kserver.ui.link.KServerLinkOption

@Composable
fun KServerLinkHostScreenHost(
    connectorId: String,
    vm: KServerLinkHostVM = hiltViewModel(),
) {
    vm.initialize(connectorId)

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(vm.deviceLinkedEvents) {
        vm.deviceLinkedEvents.collect {
            Toast.makeText(
                context,
                KServerR.string.sync_kserver_link_host_device_linked_message,
                Toast.LENGTH_LONG
            ).show()
            vm.navUp()
        }
    }

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        KServerLinkHostScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onLinkOptionSelected = { option -> vm.onLinkOptionSelected(option) },
            onShareLinkCode = { activity?.let { act -> vm.shareLinkCode(act) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KServerLinkHostScreen(
    state: KServerLinkHostVM.State,
    onNavigateUp: () -> Unit,
    onLinkOptionSelected: (KServerLinkOption) -> Unit,
    onShareLinkCode: () -> Unit,
) {
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
                            text = stringResource(KServerR.string.sync_kserver_link_host_option_qrcode),
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
                            text = stringResource(KServerR.string.sync_kserver_link_host_option_direct),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            when (state.linkOption) {
                KServerLinkOption.QRCODE -> {
                    val qrBitmap = remember(state.encodedLinkCode) {
                        state.encodedLinkCode?.let { code ->
                            try {
                                BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, 512, 512)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(8.dp),
                        )
                    }
                }

                KServerLinkOption.DIRECT -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = stringResource(KServerR.string.sync_kserver_link_code_label),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = state.encodedLinkCode ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onShareLinkCode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(CommonR.string.general_share_action))
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun KServerLinkHostScreenQRPreview() = PreviewWrapper {
    KServerLinkHostScreen(
        state = KServerLinkHostVM.State(
            linkOption = KServerLinkOption.QRCODE,
            encodedLinkCode = "octi://link/abc123def456",
        ),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onShareLinkCode = {},
    )
}

@Preview2
@Composable
private fun KServerLinkHostScreenDirectPreview() = PreviewWrapper {
    KServerLinkHostScreen(
        state = KServerLinkHostVM.State(
            linkOption = KServerLinkOption.DIRECT,
            encodedLinkCode = "octi://link/abc123def456",
        ),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onShareLinkCode = {},
    )
}
