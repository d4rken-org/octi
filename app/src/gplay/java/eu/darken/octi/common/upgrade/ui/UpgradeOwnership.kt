package eu.darken.octi.common.upgrade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Autorenew
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR

// Ownership presentation for users who already own a Pro entitlement. Subscribers without the
// one-time purchase see the switch offer — LOCKED while the subscription still renews, so buying it
// can't stack with an upcoming renewal, and (Octi-specific) held until billing has settled.
@Composable
internal fun UpgradeOwnershipContent(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestore: () -> Unit,
) {
    val ownership = state.ownership
    val subscription = ownership.subscription

    UpgradeOwnedHero(ownership = ownership)

    if (ownership.hasIap) {
        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_owned_iap_title),
            icon = Icons.TwoTone.Verified,
        ) {
            UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_owned_iap_body))
        }
    }

    if (subscription != null) {
        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_owned_sub_title),
            icon = Icons.TwoTone.Autorenew,
        ) {
            UpgradeSectionBody(
                text = stringResource(
                    if (subscription.isAutoRenewing) R.string.upgrade_screen_owned_sub_renewing_body
                    else R.string.upgrade_screen_owned_sub_not_renewing_body
                ),
            )
            // Own both + still renewing: nudge to cancel the redundant subscription in Play.
            if (subscription.isAutoRenewing && ownership.hasIap) {
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_both_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = onManageSubscription, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.upgrade_screen_manage_subscription_action))
            }
        }
    }

    if (subscription != null && !ownership.hasIap) {
        // The switch path as a visible artifact, not just prose: while the subscription still renews
        // the offer is shown LOCKED with the unlock condition. Kept gated on `settled` too — an
        // unsettled fresh install must not let a renewing subscriber start the one-time purchase.
        val switchUnlocked = !subscription.isAutoRenewing
        UpgradeActionCard {
            UpgradeOfferRow(
                title = stringResource(R.string.upgrade_screen_iap_offer_title),
                price = state.iapPrice,
                hint = stringResource(
                    if (switchUnlocked) R.string.upgrade_screen_switch_body
                    else R.string.upgrade_screen_switch_locked_note
                ),
            ) {
                Button(
                    onClick = onIap,
                    // Not gated on iapEnabled (prices may have failed while the purchase would still
                    // work — the billing flow re-queries on launch), but still gated on `settled`.
                    enabled = switchUnlocked && state.settled && !state.anyOperationInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.verificationInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.upgrade_screen_iap_action))
                }
            }
        }
    }

    // Framed as a status re-check; support is offered by the failed-restore dialog only.
    UpgradeRestoreSection(
        title = stringResource(R.string.upgrade_screen_restore_status_title),
        body = stringResource(R.string.upgrade_screen_restore_status_body),
        onRestore = onRestore,
        restoreInProgress = state.restoreInProgress,
        busy = state.anyOperationInProgress,
    )
}

// The "you have it" moment: mascot and congrats in one hero card at the top of the status screen,
// with the variant (subscription vs one-time) spelled out.
@Composable
private fun UpgradeOwnedHero(
    ownership: Ownership,
    modifier: Modifier = Modifier,
) {
    val proName = "${stringResource(CommonR.string.app_name)} ${stringResource(R.string.app_name_upgrade_postfix)}"
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpgradeMascot(size = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    // The permanent purchase is the meaningful one when both are owned.
                    text = stringResource(
                        if (ownership.hasIap) R.string.upgrade_screen_owned_hero_iap_body
                        else R.string.upgrade_screen_owned_hero_sub_body,
                        proName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// Shown on the acquisition view while Pro is active purely via the local grace window. Calm
// reassurance, not a warning: the user has lost nothing (yet). Stage 1 confirms Pro is intact;
// stage 2 (after the episode aged past the threshold) explains and offers restore. Support is NOT
// offered here — escalation lives in the failed-restore dialog after an empty restore.
@Composable
internal fun UpgradeGraceCard(
    showDiagnostics: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    restoreInProgress: Boolean = false,
    busy: Boolean = false,
) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_screen_grace_title),
        icon = Icons.TwoTone.Verified,
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        // While the episode is young the title is "Confirming…", so the header shows motion. Once
        // diagnostics appear the copy asks the user to act — a spinner would say "still working,
        // wait" and undercut the restore button, so the static icon returns.
        leading = if (showDiagnostics) null else {
            {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            }
        },
    ) {
        Text(
            text = stringResource(
                if (showDiagnostics) R.string.upgrade_screen_grace_diagnostics_body
                else R.string.upgrade_screen_grace_body
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (showDiagnostics) {
            Button(
                onClick = onRestore,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (restoreInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.upgrade_screen_restore_purchase_action))
            }
        }
    }
}
