package eu.darken.octi.common.upgrade.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import eu.darken.octi.R
import eu.darken.octi.common.compose.OctiMascot
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant

@Composable
fun UpgradeScreenHost(
    forced: Boolean = false,
    manage: Boolean = false,
    vm: UpgradeViewModel = hiltViewModel(),
) {
    vm.initialize(forced = forced, manage = manage)

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResumed() }

    val snackbarHostState = remember { SnackbarHostState() }
    val tooFastMsg = stringResource(R.string.upgrade_screen_sponsor_too_fast_msg)

    LaunchedEffect(Unit) {
        vm.snackbarEvents.collect { snackbarHostState.showSnackbar(tooFastMsg) }
    }

    val state by vm.state.collectAsState()
    UpgradeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onSponsor = { vm.goGithubSponsors() },
        onOpenSponsors = { vm.openSponsors() },
        onSeeUpgradeOptions = { vm.onSeeUpgradeOptions() },
    )
}

@Composable
fun UpgradeScreen(
    state: UpgradeViewModel.State?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onSponsor: () -> Unit,
    onOpenSponsors: () -> Unit,
    onSeeUpgradeOptions: () -> Unit,
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
            OctiMascot(modifier = Modifier.size(96.dp))
            Spacer(modifier = Modifier.height(8.dp))

            when {
                // DataStore hasn't answered yet — render nothing rather than flashing the sales
                // pitch (with its armable unlock heuristic) at an existing supporter.
                state == null -> Unit

                state.isPro -> SupporterStatus(
                    upgradedAt = state.upgradedAt,
                    onOpenSponsors = onOpenSponsors,
                )

                state.showFreeStatus -> FreeStatus(onSeeUpgradeOptions = onSeeUpgradeOptions)

                else -> SponsorPitch(onSponsor = onSponsor)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SupporterStatus(
    upgradedAt: Instant?,
    onOpenSponsors: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.upgrade_screen_supporter_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.upgrade_screen_supporter_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            upgradedAt?.let { instant ->
                Spacer(modifier = Modifier.height(4.dp))
                val formatted = remember(instant) {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withZone(ZoneId.systemDefault())
                        .format(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()))
                }
                Text(
                    text = stringResource(R.string.upgrade_screen_supporter_since, formatted),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(onClick = onOpenSponsors, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.upgrade_screen_open_sponsors_action))
    }
}

@Composable
private fun FreeStatus(onSeeUpgradeOptions: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.upgrade_screen_free_status_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.upgrade_screen_free_status_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onSeeUpgradeOptions, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.upgrade_screen_see_options_action))
            }
        }
    }
}

@Composable
private fun SponsorPitch(onSponsor: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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

    Button(onClick = onSponsor, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.upgrade_screen_sponsor_action))
    }
    Text(
        text = stringResource(R.string.upgrade_screen_sponsor_action_hint),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview2
@Composable
private fun UpgradeScreenSponsorPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeViewModel.State(isPro = false),
        onNavigateUp = {}, onSponsor = {}, onOpenSponsors = {}, onSeeUpgradeOptions = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenSupporterPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeViewModel.State(isPro = true, upgradedAt = Instant.fromEpochMilliseconds(1704067200000L)),
        onNavigateUp = {}, onSponsor = {}, onOpenSponsors = {}, onSeeUpgradeOptions = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenFreeStatusPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeViewModel.State(isPro = false, manageMode = true, viewingOffers = false),
        onNavigateUp = {}, onSponsor = {}, onOpenSponsors = {}, onSeeUpgradeOptions = {},
    )
}
