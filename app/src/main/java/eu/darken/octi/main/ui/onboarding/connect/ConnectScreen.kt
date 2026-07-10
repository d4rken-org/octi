package eu.darken.octi.main.ui.onboarding.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.OctiMascot
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.main.ui.onboarding.StepIndicator

@Composable
fun ConnectScreenHost(vm: ConnectVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    ConnectScreen(
        onSetupSync = { vm.finishWithSyncSetup() },
        onMaybeLater = { vm.finishToDashboard() },
    )
}

@Composable
fun ConnectScreen(
    onSetupSync: () -> Unit,
    onMaybeLater: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Scrollable content fills available space
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Column(
                    modifier = Modifier.widthIn(max = 600.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OctiMascot(modifier = Modifier.size(96.dp))

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(CommonR.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                    )

                    Text(
                        text = stringResource(R.string.onboarding_connect_title),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = stringResource(R.string.onboarding_connect_body1),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.onboarding_connect_body2),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Fixed bottom section
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onSetupSync,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.onboarding_connect_setup_action))
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onMaybeLater,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(CommonR.string.general_maybe_later_action))
                }

                Spacer(modifier = Modifier.height(24.dp))

                StepIndicator(totalSteps = 3, currentStep = 3)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Preview2
@Composable
private fun ConnectScreenPreview() = PreviewWrapper {
    ConnectScreen(
        onSetupSync = {},
        onMaybeLater = {},
    )
}
