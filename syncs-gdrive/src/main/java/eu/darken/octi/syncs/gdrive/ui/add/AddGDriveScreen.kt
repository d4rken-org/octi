package eu.darken.octi.syncs.gdrive.ui.add

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.syncs.gdrive.R as GDriveR

@Composable
fun AddGDriveScreenHost(
    linking: Boolean,
    vm: AddGDriveVM = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentLinking by rememberUpdatedState(linking)
    var pendingAuthEmail by remember { mutableStateOf<String?>(null) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        vm.onAuthResult(result, expectedEmail = pendingAuthEmail)
        pendingAuthEmail = null
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            ?: return@rememberLauncherForActivityResult
        vm.onAccountPicked(email)
    }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is AddGDriveEvents.ShowAccountPicker -> {
                    @Suppress("DEPRECATION")
                    val intent = AccountManager.newChooseAccountIntent(
                        null, null, arrayOf("com.google"), false, null, null, null, null,
                    )
                    accountPickerLauncher.launch(intent)
                }

                is AddGDriveEvents.AuthConsent -> {
                    pendingAuthEmail = event.email
                    val request = IntentSenderRequest.Builder(event.pendingIntent).build()
                    authLauncher.launch(request)
                }

                is AddGDriveEvents.AccountAlreadyConnected -> {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            if (currentLinking) {
                                GDriveR.string.sync_gdrive_link_already_connected
                            } else {
                                GDriveR.string.sync_gdrive_error_account_already_connected
                            }
                        )
                    )
                }
            }
        }
    }

    AddGDriveScreen(
        linking = linking,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onSignIn = { vm.startSignIn() },
    )
}

@Composable
fun AddGDriveScreen(
    linking: Boolean,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onNavigateUp: () -> Unit,
    onSignIn: () -> Unit,
) {
    val actionLabelRes = if (linking) GDriveR.string.sync_gdrive_link_label else GDriveR.string.sync_gdrive_add_label
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(GDriveR.string.sync_gdrive_type_label))
                        Text(
                            text = stringResource(actionLabelRes),
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (linking) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(GDriveR.string.sync_gdrive_link_same_account_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = stringResource(GDriveR.string.sync_gdrive_add_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(GDriveR.string.sync_gdrive_add_same_account_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onSignIn) {
                Text(text = stringResource(actionLabelRes))
            }
        }
    }
}

@Preview2
@Composable
private fun AddGDriveScreenPreview() = PreviewWrapper {
    AddGDriveScreen(
        linking = false,
        onNavigateUp = {},
        onSignIn = {},
    )
}

@Preview2
@Composable
private fun AddGDriveScreenLinkingPreview() = PreviewWrapper {
    AddGDriveScreen(
        linking = true,
        onNavigateUp = {},
        onSignIn = {},
    )
}
