package eu.darken.octi.syncs.gdrive.ui.add

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.syncs.gdrive.R as GDriveR

@Composable
fun AddGDriveScreenHost(vm: AddGDriveVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        vm.onAuthResult(result)
    }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is AddGDriveEvents.AuthConsent -> {
                    val request = IntentSenderRequest.Builder(event.pendingIntent).build()
                    authLauncher.launch(request)
                }
            }
        }
    }

    AddGDriveScreen(
        onNavigateUp = { vm.navUp() },
        onSignIn = { vm.startSignIn() },
    )
}

@Composable
fun AddGDriveScreen(
    onNavigateUp: () -> Unit,
    onSignIn: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(GDriveR.string.sync_gdrive_type_label))
                        Text(
                            text = stringResource(GDriveR.string.sync_gdrive_add_label),
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
            Text(
                text = stringResource(GDriveR.string.sync_gdrive_add_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onSignIn) {
                Text(text = stringResource(GDriveR.string.sync_gdrive_add_label))
            }
        }
    }
}

@Preview2
@Composable
private fun AddGDriveScreenPreview() = PreviewWrapper {
    AddGDriveScreen(
        onNavigateUp = {},
        onSignIn = {},
    )
}
