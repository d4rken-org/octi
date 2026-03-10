package eu.darken.octi.common.upgrade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler

@Composable
fun UpgradeScreenHost(
    forced: Boolean,
    vm: UpgradeViewModel = hiltViewModel(),
) {
    vm.initialize(forced)

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResumed() }

    val snackbarHostState = remember { SnackbarHostState() }
    val tooFastMsg = stringResource(R.string.upgrade_screen_sponsor_too_fast_msg)

    LaunchedEffect(Unit) {
        vm.snackbarEvents.collect { snackbarHostState.showSnackbar(tooFastMsg) }
    }

    UpgradeScreen(
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onSponsor = { vm.goGithubSponsors() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onSponsor: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.upgrade_screen_title),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Image(
                painter = painterResource(R.drawable.ic_splash_octi),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.upgrade_screen_preamble),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.upgrade_screen_how_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.upgrade_screen_how_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.upgrade_screen_why_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.upgrade_screen_benefits_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSponsor,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.upgrade_screen_sponsor_action))
            }

            Text(
                text = stringResource(R.string.upgrade_screen_sponsor_action_hint),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview2
@Composable
private fun UpgradeScreenPreview() = PreviewWrapper {
    UpgradeScreen(
        onNavigateUp = {},
        onSponsor = {},
    )
}
