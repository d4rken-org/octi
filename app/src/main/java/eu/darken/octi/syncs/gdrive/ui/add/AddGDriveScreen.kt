package eu.darken.octi.syncs.gdrive.ui.add

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.syncs.gdrive.R as GDriveR

@Composable
fun AddGDriveScreenHost(vm: AddGDriveVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vm.onGoogleSignIn(result)
    }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is AddGDriveEvents.SignInStart -> {
                    googleSignInLauncher.launch(event.intent)
                }

                is AddGDriveEvents.NoGoogleAccount -> {
                    Toast.makeText(
                        context,
                        GDriveR.string.sync_gdrive_error_no_account_on_device,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    AddGDriveScreen(
        onNavigateUp = { vm.navUp() },
        onSignIn = { vm.startSignIn() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
