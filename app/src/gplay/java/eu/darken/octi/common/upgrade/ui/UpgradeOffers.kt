package eu.darken.octi.common.upgrade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR

// The acquisition offers box: header, offer rows, "or" divider, parity footnote. A product whose
// offer failed to load shows disabled with a "Refresh offers" action so the user isn't stuck.
@Composable
internal fun LoadedOffers(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UpgradeSectionHeader(
            title = stringResource(R.string.upgrade_screen_offers_title),
            icon = Icons.TwoTone.Stars,
        )

        Spacer(modifier = Modifier.height(8.dp))

        UpgradeOfferRow(
            title = stringResource(R.string.upgrade_screen_subscription_offer_title),
            price = state.subscriptionPrice,
            // Only promise the trial when Play actually returned the trial offer.
            hint = stringResource(
                if (state.subscriptionAction == SubscriptionAction.TRIAL) {
                    R.string.upgrade_screen_subscription_offer_body
                } else {
                    R.string.upgrade_screen_subscription_offer_body_no_trial
                }
            ),
        ) {
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
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.upgrade_screen_offers_or),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))

        UpgradeOfferRow(
            title = stringResource(R.string.upgrade_screen_iap_offer_title),
            price = state.iapPrice,
            hint = stringResource(R.string.upgrade_screen_iap_offer_body),
        ) {
            OutlinedButton(
                onClick = onIap,
                enabled = state.iapEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.verificationInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.upgrade_screen_iap_action))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        UpgradeHintText(text = stringResource(R.string.upgrade_screen_offers_body))

        // One product's offer didn't load: keep the other actionable, but give a way to re-fetch the
        // missing one without leaving the screen (retry re-runs both queries).
        if (!state.subAvailable || !state.iapAvailable) {
            TextButton(
                onClick = onRetry,
                enabled = !state.anyOperationInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(CommonR.string.general_refresh_action))
            }
        }
    }
}

// Title and price share one line ("·"-joined in code: direction-neutral punctuation, not
// translatable copy), terms follow as body text, then the action — the terms must not repeat the
// button label.
@Composable
internal fun UpgradeOfferRow(
    title: String,
    price: String?,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = listOfNotNull(title, price).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        hint?.let { UpgradeSectionBody(text = it) }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
