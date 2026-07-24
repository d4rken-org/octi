package eu.darken.octi.common.upgrade.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.octi.R
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

    // rememberSaveable so a config change (rotation) can't drop an in-flight dialog after it consumed
    // its one-shot event.
    var showStillRenewingDialog by rememberSaveable { mutableStateOf(false) }
    var showCheckFailedDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreFailedDialog by rememberSaveable { mutableStateOf(false) }

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

    val state by vm.state.collectAsStateWithLifecycle()
    UpgradeScreen(
        state = state,
        onNavigateUp = { vm.navUp() },
        onIap = { activity?.let { vm.onGoIap(it) } },
        onSubscription = { activity?.let { vm.onGoSubscription(it) } },
        onSubscriptionTrial = { activity?.let { vm.onGoSubscriptionTrial(it) } },
        onRestore = { vm.restorePurchase() },
        onManageSubscription = { vm.onManageSubscription() },
        onSeeUpgradeOptions = { vm.onSeeUpgradeOptions() },
        onRetry = { vm.retrySkuQuery() },
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
        RestoreFailedDialog(
            onContactSupport = {
                showRestoreFailedDialog = false
                vm.onContactSupport()
            },
            onDismiss = { showRestoreFailedDialog = false },
        )
    }
}

@Composable
internal fun UpgradeScreen(
    state: UpgradeUiState?,
    onNavigateUp: () -> Unit,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
    onSeeUpgradeOptions: () -> Unit,
    onRetry: () -> Unit,
) {
    val loaded = state as? UpgradeUiState.Loaded
    val owned = loaded?.takeIf { it.ownership.ownsAnything }

    UpgradeScreenScaffold(
        // Grace users are still Pro: they get the "Octi Pro" status title too — "Get Octi Pro" would
        // contradict the rest of the app, which behaves upgraded.
        title = if (owned != null || loaded?.grace != null) {
            upgradeScreenTitle(upgraded = true)
        } else {
            AnnotatedString(stringResource(R.string.upgrade_screen_title))
        },
        onNavigateUp = onNavigateUp,
    ) { innerPadding ->
        UpgradeScreenContent(
            paddingValues = innerPadding,
            contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 32.dp),
        ) {
            // Owners get the mascot inside the congrats hero card instead of the standalone header.
            if (owned == null) UpgradeHeader(mascotSize = 88.dp)

            when {
                owned != null -> UpgradeOwnershipContent(
                    state = owned,
                    onIap = onIap,
                    onManageSubscription = onManageSubscription,
                    onRestore = onRestore,
                )

                loaded != null && loaded.showFreeStatus -> FreeStatusContent(onSeeUpgradeOptions = onSeeUpgradeOptions)

                loaded != null -> UpgradeAcquisitionContent(
                    state = loaded,
                    onIap = onIap,
                    onSubscription = onSubscription,
                    onSubscriptionTrial = onSubscriptionTrial,
                    onRestore = onRestore,
                    onRetry = onRetry,
                )

                else -> UpgradeActionCard { UpgradeLoadingBlock() }
            }
        }
    }
}

@Composable
private fun UpgradeAcquisitionContent(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
) {
    val grace = state.grace
    val inGrace = grace != null

    if (grace != null) {
        UpgradeGraceCard(
            showDiagnostics = grace.showDiagnostics,
            onRestore = onRestore,
            restoreInProgress = state.restoreInProgress,
            busy = state.anyOperationInProgress,
        )
    }

    // Grace users never see the sales pitch (they are Pro; sales copy next to a "still active" card
    // reads as a contradiction). The OFFERS follow the episode age: a young episode (likely a
    // self-healing blip) shows calm status only, an aged one adds restore AND the offers so an
    // expired subscriber can switch without waiting out the full grace window.
    if (!inGrace) {
        UpgradePreambleCard(
            text = stringResource(R.string.upgrade_screen_preamble),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )

        if (state.showRestoreBanner) {
            // The targeted returning-buyer nudge: emphasized, and the ONLY restore affordance on the
            // screen — a second one below would make the screen feel uncertain about its own advice.
            UpgradeRestoreSection(
                title = stringResource(R.string.upgrade_screen_restore_banner_title),
                body = stringResource(R.string.upgrade_screen_restore_banner_body),
                onRestore = onRestore,
                restoreInProgress = state.restoreInProgress,
                busy = state.anyOperationInProgress,
                emphasized = true,
            )
        }

        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_benefits_title),
            icon = Icons.TwoTone.AutoAwesome,
        ) {
            UpgradeFeatureList(text = stringResource(R.string.upgrade_screen_benefits_body))
        }
    }

    if (grace == null || grace.showDiagnostics) {
        UpgradeOffersBox(
            state = state,
            onIap = onIap,
            onSubscription = onSubscription,
            onSubscriptionTrial = onSubscriptionTrial,
            onRetry = onRetry,
        )
    }

    // Restore is account reconciliation, not an offer — its own described section, after the offers.
    // Only for plain acquisition: returning buyers get the emphasized section up top, grace users'
    // restore is owned by the grace card's two-stage disclosure.
    if (!inGrace && !state.showRestoreBanner) {
        UpgradeRestoreSection(
            title = stringResource(R.string.upgrade_screen_restore_banner_title),
            body = stringResource(R.string.upgrade_screen_restore_body),
            onRestore = onRestore,
            restoreInProgress = state.restoreInProgress,
            busy = state.anyOperationInProgress,
        )
    }
}

// Each offer phase brings its OWN container: the Unavailable state is a full card itself (wrapping
// it in the action card would nest a card in a card). Animated by phase KIND only, so per-tap
// field changes (restore/verification progress) don't cross-fade the card.
@Composable
private fun UpgradeOffersBox(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedContent(
        targetState = state.offers,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        label = "upgrade-offers",
    ) { offers ->
        when (offers) {
            OfferState.Loading -> UpgradeActionCard { UpgradeLoadingBlock() }
            is OfferState.Unavailable -> UpgradeInlineStateCard(
                title = stringResource(R.string.upgrades_gplay_unavailable_error_title),
                body = stringResource(R.string.upgrades_gplay_unavailable_error_description),
                icon = Icons.TwoTone.WarningAmber,
            ) {
                // Play can be slow rather than broken (cold store, first sign-in): let the user
                // re-run the offer queries instead of leaving a dead screen.
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(CommonR.string.general_refresh_action))
                }
            }

            OfferState.Ready -> UpgradeActionCard {
                LoadedOffers(
                    state = state,
                    onIap = onIap,
                    onSubscription = onSubscription,
                    onSubscriptionTrial = onSubscriptionTrial,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun FreeStatusContent(onSeeUpgradeOptions: () -> Unit) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_screen_free_status_title),
        icon = Icons.TwoTone.AutoAwesome,
    ) {
        UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_free_status_body))
        Button(onClick = onSeeUpgradeOptions, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.upgrade_screen_see_options_action))
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

// Leads with the just-happened live Play check (hedged: RestoreFailed also fires on timeout, so the
// copy never claims a successful check). This dialog is the ONLY contact-support surface —
// escalation comes after an empty restore, never before.
@Composable
private fun RestoreFailedDialog(onContactSupport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = listOf(
                    stringResource(R.string.upgrade_screen_restore_checked_message),
                    stringResource(R.string.upgrade_screen_restore_multiaccount_hint),
                    stringResource(R.string.upgrade_screen_restore_sync_patience_hint),
                    stringResource(R.string.upgrade_screen_restore_contact_hint),
                ).joinToString("\n\n"),
            )
        },
        confirmButton = {
            TextButton(onClick = onContactSupport) {
                Text(text = stringResource(R.string.upgrade_screen_contact_support_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
        },
    )
}

private fun previewLoaded(
    subscriptionAction: SubscriptionAction = SubscriptionAction.STANDARD,
    subscriptionPrice: String? = "€1.99",
    iapPrice: String? = "€9.99",
    offers: OfferState = OfferState.Ready,
    ownership: Ownership = Ownership(),
    grace: GraceHint? = null,
    showRestoreBanner: Boolean = false,
    manageMode: Boolean = false,
    viewingOffers: Boolean = false,
) = UpgradeUiState.Loaded(
    subscriptionAction = subscriptionAction,
    subscriptionEnabled = subscriptionAction != SubscriptionAction.UNAVAILABLE && ownership.subscription == null,
    subscriptionPrice = subscriptionPrice,
    iapEnabled = !ownership.hasIap && iapPrice != null,
    iapPrice = iapPrice,
    offers = offers,
    ownership = ownership,
    grace = grace,
    showRestoreBanner = showRestoreBanner,
    manageMode = manageMode,
    viewingOffers = viewingOffers,
)

@Composable
private fun PreviewUpgradeScreen(state: UpgradeUiState?) {
    UpgradeScreen(
        state = state,
        onNavigateUp = {}, onIap = {}, onSubscription = {}, onSubscriptionTrial = {},
        onRestore = {}, onManageSubscription = {}, onSeeUpgradeOptions = {}, onRetry = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenLoadingPreview() = PreviewWrapper { PreviewUpgradeScreen(UpgradeUiState.Loading) }

@Preview2
@Composable
private fun UpgradeScreenOffersPreview() = PreviewWrapper {
    PreviewUpgradeScreen(previewLoaded(subscriptionAction = SubscriptionAction.TRIAL))
}

@Preview2
@Composable
private fun UpgradeScreenUnavailablePreview() = PreviewWrapper {
    PreviewUpgradeScreen(
        previewLoaded(
            subscriptionAction = SubscriptionAction.UNAVAILABLE,
            subscriptionPrice = null,
            iapPrice = null,
            offers = OfferState.Unavailable(RuntimeException("Play unavailable")),
        ),
    )
}

@Preview2
@Composable
private fun UpgradeScreenOwnedSubRenewingPreview() = PreviewWrapper {
    PreviewUpgradeScreen(
        previewLoaded(
            subscriptionAction = SubscriptionAction.UNAVAILABLE,
            subscriptionPrice = null,
            ownership = Ownership(hasIap = false, subscription = SubscriptionOwnership(isAutoRenewing = true)),
        ),
    )
}

@Preview2
@Composable
private fun UpgradeScreenSwitchAvailablePreview() = PreviewWrapper {
    PreviewUpgradeScreen(
        previewLoaded(
            subscriptionAction = SubscriptionAction.UNAVAILABLE,
            subscriptionPrice = null,
            ownership = Ownership(hasIap = false, subscription = SubscriptionOwnership(isAutoRenewing = false)),
        ),
    )
}

@Preview2
@Composable
private fun UpgradeScreenGraceDiagnosticsPreview() = PreviewWrapper {
    PreviewUpgradeScreen(
        previewLoaded(
            subscriptionAction = SubscriptionAction.UNAVAILABLE,
            subscriptionPrice = null,
            iapPrice = null,
            grace = GraceHint(showDiagnostics = true),
        ),
    )
}

@Preview2
@Composable
private fun UpgradeScreenFreeStatusPreview() = PreviewWrapper {
    PreviewUpgradeScreen(previewLoaded(manageMode = true, viewingOffers = false))
}
