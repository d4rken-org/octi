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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.OctiMascot
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.upgrade.core.OurSku
import eu.darken.octi.common.R as CommonR

@Composable
fun UpgradeScreenHost(
    forced: Boolean,
    vm: UpgradeViewModel = hiltViewModel(),
) {
    vm.initialize(forced)

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(vm.billingEvents) {
        vm.billingEvents.collect { event ->
            if (activity == null) return@collect
            when (event) {
                UpgradeViewModel.BillingEvent.LaunchIap -> vm.launchBillingIap(activity)
                UpgradeViewModel.BillingEvent.LaunchSubscription -> vm.launchBillingSubscription(activity)
                UpgradeViewModel.BillingEvent.LaunchSubscriptionTrial -> vm.launchBillingSubscriptionTrial(activity)
            }
        }
    }

    var showRestoreFailedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                UpgradeEvents.RestoreFailed -> showRestoreFailedDialog = true
            }
        }
    }

    if (showRestoreFailedDialog) {
        RestoreFailedDialog(onDismiss = { showRestoreFailedDialog = false })
    }

    val state by vm.state.collectAsState(initial = null)
    UpgradeScreen(
        state = state,
        onNavigateUp = { vm.navUp() },
        onIap = { vm.onGoIap() },
        onSubscription = { vm.onGoSubscription() },
        onSubscriptionTrial = { vm.onGoSubscriptionTrial() },
        onRestore = { vm.restorePurchase() },
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

@Composable
fun UpgradeScreen(
    state: UpgradeViewModel.State?,
    onNavigateUp: () -> Unit,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRestore: () -> Unit,
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

            if (state?.wasPreviouslyPro == true) {
                Spacer(modifier = Modifier.height(16.dp))
                RestoreBanner(onRestore = onRestore)
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
                text = stringResource(R.string.upgrade_screen_how_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )

            if (state == null || state.pricingLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.pricing != null) {
                PricingContent(
                    pricing = state.pricing,
                    onIap = onIap,
                    onSubscription = onSubscription,
                    onSubscriptionTrial = onSubscriptionTrial,
                )
            } else {
                PricingUnavailable()
            }

            if (state != null) {
                Spacer(modifier = Modifier.height(8.dp))

                // Restore is available regardless of whether pricing could be loaded — a returning
                // buyer with a flaky Play connection is exactly who needs it.
                TextButton(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RestoreBanner(
    onRestore: () -> Unit,
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
                modifier = Modifier.fillMaxWidth(),
            ) {
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

        // Same fix action the GplayServiceUnavailableException error dialog offers.
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
private fun PricingContent(
    pricing: UpgradeViewModel.Pricing,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
) {
    val subOffer = pricing.sub?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
        OurSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offer)
    }
    val subOfferTrial = pricing.sub?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
        OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offer)
    }
    val iapOffer = pricing.iap?.details?.oneTimePurchaseOfferDetails
    val canSub = subOffer != null || subOfferTrial != null

    if (canSub) {
        Button(
            onClick = if (subOfferTrial != null) onSubscriptionTrial else onSubscription,
            enabled = !pricing.hasSub,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    if (subOfferTrial != null) R.string.upgrade_screen_subscription_trial_action
                    else R.string.upgrade_screen_subscription_action
                ),
            )
        }

        val subPrice = subOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        if (subPrice != null) {
            Text(
                text = stringResource(R.string.upgrade_screen_subscription_action_hint, subPrice),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onIap,
        enabled = iapOffer != null && !pricing.hasIap,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.upgrade_screen_iap_action))
    }

    if (iapOffer != null) {
        Text(
            text = stringResource(R.string.upgrade_screen_iap_action_hint, "${iapOffer.formattedPrice}"),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview2
@Composable
private fun UpgradeScreenLoadingPreview() = PreviewWrapper {
    UpgradeScreen(
        state = null,
        onNavigateUp = {},
        onIap = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onRestore = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenLoadedPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeViewModel.State(
            pricing = UpgradeViewModel.Pricing(
                iap = null,
                sub = null,
                hasIap = false,
                hasSub = false,
            ),
        ),
        onNavigateUp = {},
        onIap = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onRestore = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenReturningBuyerPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeViewModel.State(
            pricing = UpgradeViewModel.Pricing(
                iap = null,
                sub = null,
                hasIap = false,
                hasSub = false,
            ),
            wasPreviouslyPro = true,
        ),
        onNavigateUp = {},
        onIap = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onRestore = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenPricingUnavailablePreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeViewModel.State(
            pricing = null,
            pricingUnavailable = true,
            wasPreviouslyPro = true,
        ),
        onNavigateUp = {},
        onIap = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onRestore = {},
    )
}
