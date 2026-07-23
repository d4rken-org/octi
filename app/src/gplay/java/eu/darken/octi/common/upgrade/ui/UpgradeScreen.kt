package eu.darken.octi.common.upgrade.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.darken.octi.R
import eu.darken.octi.common.compose.OctiMascot
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.R as CommonR

@Composable
fun UpgradeScreenHost(
    forced: Boolean = false,
    manage: Boolean = false,
    vm: UpgradeViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    // Bind the route BEFORE anything else can race the auto-close collector.
    LaunchedEffect(forced, manage) { vm.initialize(forced = forced, manage = manage) }

    val context = LocalContext.current
    val activity = context as? Activity

    var showStillRenewingDialog by remember { mutableStateOf(false) }
    var showCheckFailedDialog by remember { mutableStateOf(false) }
    var showRestoreFailedDialog by remember { mutableStateOf(false) }

    val restoreSuccessMessage = stringResource(R.string.upgrade_screen_restore_success_message)

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                UpgradeEvents.RestoreFailed -> showRestoreFailedDialog = true
                UpgradeEvents.RestoreSucceeded ->
                    Toast.makeText(context, restoreSuccessMessage, Toast.LENGTH_LONG).show()

                UpgradeEvents.SubscriptionStillRenewing -> showStillRenewingDialog = true
                UpgradeEvents.SubscriptionCheckFailed -> showCheckFailedDialog = true
            }
        }
    }

    // Returning from Play's subscription-management page must refresh the renewal state promptly.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state by vm.state.collectAsState()
    UpgradeScreen(
        state = state,
        onNavigateUp = { vm.navUp() },
        onIap = { activity?.let { vm.onGoIap(it) } },
        onSubscription = { activity?.let { vm.onGoSubscription(it) } },
        onSubscriptionTrial = { activity?.let { vm.onGoSubscriptionTrial(it) } },
        onRestore = { vm.restorePurchase() },
        onManageSubscription = { vm.onManageSubscription() },
        onSeeUpgradeOptions = { vm.onSeeUpgradeOptions() },
        onContactSupport = { vm.onContactSupport() },
    )

    if (showStillRenewingDialog) {
        StillRenewingDialog(
            onManage = {
                showStillRenewingDialog = false
                vm.onManageSubscription()
            },
            onDismiss = { showStillRenewingDialog = false },
        )
    }
    if (showCheckFailedDialog) {
        CheckFailedDialog(onDismiss = { showCheckFailedDialog = false })
    }
    if (showRestoreFailedDialog) {
        RestoreFailedDialog(onDismiss = { showRestoreFailedDialog = false })
    }
}

@Composable
fun UpgradeScreen(
    state: UpgradeUiState?,
    onNavigateUp: () -> Unit,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
    onSeeUpgradeOptions: () -> Unit,
    onContactSupport: () -> Unit,
) {
    Scaffold(
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            OctiMascot(modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                null, is UpgradeUiState.Loading -> LoadingContent()
                is UpgradeUiState.Loaded -> when {
                    state.ownership.ownsAnything -> OwnedContent(
                        state = state,
                        onIap = onIap,
                        onManageSubscription = onManageSubscription,
                    )

                    state.grace != null -> GraceContent(
                        grace = state.grace,
                        restoreInProgress = state.restoreInProgress,
                        onRestore = onRestore,
                        onContactSupport = onContactSupport,
                    )

                    state.showFreeStatus -> FreeStatusContent(onSeeUpgradeOptions = onSeeUpgradeOptions)

                    else -> OffersContent(
                        state = state,
                        onIap = onIap,
                        onSubscription = onSubscription,
                        onSubscriptionTrial = onSubscriptionTrial,
                        onRestore = onRestore,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: @Composable (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            content?.let {
                Spacer(modifier = Modifier.height(12.dp))
                it()
            }
        }
    }
}

@Composable
private fun OwnedContent(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    val subscription = state.ownership.subscription

    StatusCard(
        title = stringResource(R.string.upgrade_screen_owned_title),
        body = stringResource(R.string.upgrade_screen_owned_body),
    )

    if (state.ownership.hasIap) {
        Spacer(modifier = Modifier.height(16.dp))
        StatusCard(
            title = stringResource(R.string.upgrade_screen_owned_iap_title),
            body = stringResource(R.string.upgrade_screen_owned_iap_body),
        )
    }

    if (subscription != null) {
        Spacer(modifier = Modifier.height(16.dp))
        StatusCard(
            title = stringResource(R.string.upgrade_screen_owned_sub_title),
            body = stringResource(
                if (subscription.isAutoRenewing) R.string.upgrade_screen_owned_sub_renewing_body
                else R.string.upgrade_screen_owned_sub_not_renewing_body
            ),
        ) {
            OutlinedButton(onClick = onManageSubscription, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.upgrade_screen_manage_subscription_action))
            }
        }
    }

    // Own both: warn so the user can cancel the (redundant) subscription in Play.
    if (state.ownership.hasIap && subscription != null) {
        Spacer(modifier = Modifier.height(16.dp))
        StatusCard(
            title = stringResource(R.string.upgrade_screen_owned_both_title),
            body = stringResource(R.string.upgrade_screen_owned_both_warning),
            container = MaterialTheme.colorScheme.errorContainer,
        ) {
            OutlinedButton(onClick = onManageSubscription, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.upgrade_screen_manage_subscription_action))
            }
        }
    }

    // Switch offer: a subscriber without the lifetime purchase can move over. Locked while the
    // subscription still auto-renews (cancel in Play first); the purchase gate re-verifies anyway.
    if (subscription != null && !state.ownership.hasIap) {
        Spacer(modifier = Modifier.height(16.dp))
        SwitchCard(
            renewing = subscription.isAutoRenewing,
            iapPrice = state.iapPrice,
            iapEnabled = state.iapEnabled,
            onIap = onIap,
            onManageSubscription = onManageSubscription,
        )
    }
}

@Composable
private fun SwitchCard(
    renewing: Boolean,
    iapPrice: String?,
    iapEnabled: Boolean,
    onIap: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.upgrade_screen_switch_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.upgrade_screen_switch_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (renewing) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.upgrade_screen_switch_locked_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onManageSubscription, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.upgrade_screen_manage_subscription_action))
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onIap,
                    enabled = iapEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (iapPrice != null) {
                            stringResource(R.string.upgrade_screen_switch_action, iapPrice)
                        } else {
                            stringResource(R.string.upgrade_screen_iap_action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GraceContent(
    grace: GraceHint,
    restoreInProgress: Boolean,
    onRestore: () -> Unit,
    onContactSupport: () -> Unit,
) {
    StatusCard(
        title = stringResource(R.string.upgrade_screen_grace_title),
        body = stringResource(
            if (grace.showDiagnostics) R.string.upgrade_screen_grace_diagnostics_body
            else R.string.upgrade_screen_grace_body
        ),
    ) {
        if (grace.showDiagnostics) {
            Button(
                onClick = onRestore,
                enabled = !restoreInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (restoreInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onContactSupport, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.upgrade_screen_contact_support_action))
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun FreeStatusContent(onSeeUpgradeOptions: () -> Unit) {
    StatusCard(
        title = stringResource(R.string.upgrade_screen_free_status_title),
        body = stringResource(R.string.upgrade_screen_free_status_body),
    ) {
        Button(onClick = onSeeUpgradeOptions, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.upgrade_screen_see_options_action))
        }
    }
}

@Composable
private fun OffersContent(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRestore: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Text(
            text = stringResource(R.string.upgrade_screen_preamble),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (state.showRestoreBanner) {
        Spacer(modifier = Modifier.height(16.dp))
        RestoreBanner(onRestore = onRestore, restoreInProgress = state.restoreInProgress)
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.upgrade_screen_benefits_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.upgrade_screen_benefits_body),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.upgrade_screen_how_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.upgrade_screen_how_body2),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.subscriptionAction == SubscriptionAction.TRIAL) {
        Text(
            text = stringResource(R.string.upgrade_screen_how_trial_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (!state.subAvailable && !state.iapAvailable) {
        PricingUnavailable()
    } else {
        PricingContent(
            state = state,
            onIap = onIap,
            onSubscription = onSubscription,
            onSubscriptionTrial = onSubscriptionTrial,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Restore is available regardless of whether pricing could be loaded — a returning buyer with a
    // flaky Play connection is exactly who needs it.
    TextButton(
        onClick = onRestore,
        enabled = !state.restoreInProgress,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action))
    }
}

@Composable
private fun PricingContent(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
) {
    if (state.subAvailable) {
        Button(
            onClick = if (state.subscriptionAction == SubscriptionAction.TRIAL) onSubscriptionTrial else onSubscription,
            enabled = state.subscriptionEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    if (state.subscriptionAction == SubscriptionAction.TRIAL) R.string.upgrade_screen_subscription_trial_action
                    else R.string.upgrade_screen_subscription_action
                ),
            )
        }
        state.subscriptionPrice?.let { price ->
            Text(
                text = stringResource(R.string.upgrade_screen_subscription_action_hint, price),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onIap,
        enabled = state.iapEnabled && state.iapAvailable,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.upgrade_screen_iap_action))
    }
    state.iapPrice?.let { price ->
        Text(
            text = stringResource(R.string.upgrade_screen_iap_action_hint, price),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestoreBanner(
    onRestore: () -> Unit,
    restoreInProgress: Boolean = false,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.upgrade_screen_restore_banner_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.upgrade_screen_restore_banner_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRestore,
                enabled = !restoreInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (restoreInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action))
            }
        }
    }
}

@Composable
private fun PricingUnavailable() {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.upgrades_gplay_unavailable_error_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.upgrades_gplay_unavailable_error_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                try {
                    val intent = Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", "com.android.vending", null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "Google Play is not installed", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Google Play")
        }
    }
}

@Composable
private fun StillRenewingDialog(onManage: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.upgrade_screen_sub_still_renewing_title)) },
        text = { Text(text = stringResource(R.string.upgrade_screen_sub_still_renewing_message)) },
        confirmButton = {
            TextButton(onClick = onManage) {
                Text(text = stringResource(R.string.upgrade_screen_manage_subscription_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
        },
    )
}

@Composable
private fun CheckFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.upgrade_screen_sub_check_failed_title)) },
        text = { Text(text = stringResource(R.string.upgrade_screen_sub_check_failed_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
        },
    )
}

@Composable
private fun RestoreFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = listOf(
                    stringResource(R.string.upgrade_screen_restore_purchase_message),
                    stringResource(R.string.upgrade_screen_restore_troubleshooting_msg),
                    stringResource(R.string.upgrade_screen_restore_sync_patience_hint),
                    stringResource(R.string.upgrade_screen_restore_multiaccount_hint),
                ).joinToString("\n\n"),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
        },
    )
}

@Preview2
@Composable
private fun UpgradeScreenLoadingPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeUiState.Loading,
        onNavigateUp = {}, onIap = {}, onSubscription = {}, onSubscriptionTrial = {},
        onRestore = {}, onManageSubscription = {}, onSeeUpgradeOptions = {}, onContactSupport = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenOffersPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeUiState.Loaded(
            subscriptionAction = SubscriptionAction.STANDARD,
            subscriptionEnabled = true,
            subscriptionPrice = "€1.99",
            iapEnabled = true,
            iapPrice = "€9.99",
        ),
        onNavigateUp = {}, onIap = {}, onSubscription = {}, onSubscriptionTrial = {},
        onRestore = {}, onManageSubscription = {}, onSeeUpgradeOptions = {}, onContactSupport = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenOwnedSubRenewingPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeUiState.Loaded(
            subscriptionAction = SubscriptionAction.UNAVAILABLE,
            subscriptionEnabled = false,
            subscriptionPrice = null,
            iapEnabled = false,
            iapPrice = "€9.99",
            ownership = Ownership(hasIap = false, subscription = SubscriptionOwnership(isAutoRenewing = true)),
        ),
        onNavigateUp = {}, onIap = {}, onSubscription = {}, onSubscriptionTrial = {},
        onRestore = {}, onManageSubscription = {}, onSeeUpgradeOptions = {}, onContactSupport = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenSwitchAvailablePreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeUiState.Loaded(
            subscriptionAction = SubscriptionAction.UNAVAILABLE,
            subscriptionEnabled = false,
            subscriptionPrice = null,
            iapEnabled = true,
            iapPrice = "€9.99",
            ownership = Ownership(hasIap = false, subscription = SubscriptionOwnership(isAutoRenewing = false)),
        ),
        onNavigateUp = {}, onIap = {}, onSubscription = {}, onSubscriptionTrial = {},
        onRestore = {}, onManageSubscription = {}, onSeeUpgradeOptions = {}, onContactSupport = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenGraceDiagnosticsPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeUiState.Loaded(
            subscriptionAction = SubscriptionAction.UNAVAILABLE,
            subscriptionEnabled = false,
            subscriptionPrice = null,
            iapEnabled = false,
            iapPrice = null,
            grace = GraceHint(showDiagnostics = true),
        ),
        onNavigateUp = {}, onIap = {}, onSubscription = {}, onSubscriptionTrial = {},
        onRestore = {}, onManageSubscription = {}, onSeeUpgradeOptions = {}, onContactSupport = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenFreeStatusPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeUiState.Loaded(
            subscriptionAction = SubscriptionAction.STANDARD,
            subscriptionEnabled = true,
            subscriptionPrice = "€1.99",
            iapEnabled = true,
            iapPrice = "€9.99",
            manageMode = true,
            viewingOffers = false,
        ),
        onNavigateUp = {}, onIap = {}, onSubscription = {}, onSubscriptionTrial = {},
        onRestore = {}, onManageSubscription = {}, onSeeUpgradeOptions = {}, onContactSupport = {},
    )
}
