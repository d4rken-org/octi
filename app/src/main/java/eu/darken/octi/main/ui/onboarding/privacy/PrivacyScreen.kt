package eu.darken.octi.main.ui.onboarding.privacy

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler

@Composable
fun PrivacyScreenHost(vm: PrivacyVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        PrivacyScreen(
            state = it,
            onPrivacyPolicy = { vm.openPrivacyPolicy() },
            onToggleUpdateCheck = { vm.toggleUpdateCheck() },
            onContinue = { vm.finishScreen() },
        )
    }
}

@Composable
fun PrivacyScreen(
    state: PrivacyVM.State,
    onPrivacyPolicy: () -> Unit,
    onToggleUpdateCheck: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(62.dp))

            Image(
                painter = painterResource(R.drawable.ic_splash_octi),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(CommonR.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )

            Text(
                text = stringResource(R.string.settings_privacy_policy_label),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.onboarding_privacy_body1),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))

            FilledTonalButton(onClick = onPrivacyPolicy) {
                Text(text = stringResource(R.string.settings_privacy_policy_label))
            }

            if (state.isUpdateCheckSupported) {
                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleUpdateCheck)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.SystemUpdateAlt,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.updatecheck_setting_enabled_label),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.updatecheck_setting_enabled_explanation),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = state.isUpdateCheckEnabled,
                        onCheckedChange = null,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp),
            ) {
                Text(text = stringResource(CommonR.string.general_continue))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview2
@Composable
private fun PrivacyScreenPreview() = PreviewWrapper {
    PrivacyScreen(
        state = PrivacyVM.State(
            isUpdateCheckSupported = true,
            isUpdateCheckEnabled = true,
        ),
        onPrivacyPolicy = {},
        onToggleUpdateCheck = {},
        onContinue = {},
    )
}
