package eu.darken.octi.common.upgrade.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.compose.OctiMascot
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.R as CommonR

internal object UpgradeScreenTags {
    const val LOADING = "upgrade_loading"
    const val ACTIONS = "upgrade_actions"
    const val MASCOT_HAPPY = "upgrade_mascot_happy"
    const val MASCOT_GRUMPY = "upgrade_mascot_grumpy"
    const val HERO = "upgrade_hero"
    const val FOSS_SPONSOR = "upgrade_foss_sponsor"
    const val FOSS_STATUS_FREE = "upgrade_foss_status_free"
    const val FOSS_STATUS_UPGRADED = "upgrade_foss_status_upgraded"
    const val FOSS_SHOW_OPTIONS = "upgrade_foss_show_options"
    const val FOSS_DONATE = "upgrade_foss_donate"
    const val GPLAY_SUBSCRIPTION = "upgrade_gplay_subscription"
    const val GPLAY_SUBSCRIPTION_SPINNER = "upgrade_gplay_subscription_spinner"
    const val GPLAY_IAP = "upgrade_gplay_iap"
    const val GPLAY_IAP_SPINNER = "upgrade_gplay_iap_spinner"
    const val GPLAY_RESTORE = "upgrade_gplay_restore"
    const val GPLAY_RESTORE_BANNER = "upgrade_gplay_restore_banner"
    const val GPLAY_RESTORE_BANNER_ACTION = "upgrade_gplay_restore_banner_action"
    const val GPLAY_UNAVAILABLE = "upgrade_gplay_unavailable"
    const val GPLAY_RETRY = "upgrade_gplay_retry"
    const val GPLAY_OWNED_HERO = "upgrade_gplay_owned_hero"
    const val GPLAY_OWNED_IAP = "upgrade_gplay_owned_iap"
    const val GPLAY_OWNED_SUB = "upgrade_gplay_owned_sub"
    const val GPLAY_MANAGE_SUB = "upgrade_gplay_manage_sub"
    const val GPLAY_GRACE = "upgrade_gplay_grace"
    const val GPLAY_GRACE_SPINNER = "upgrade_gplay_grace_spinner"
    const val GPLAY_GRACE_RESTORE = "upgrade_gplay_grace_restore"
}

// "Octi" + the flavor postfix, colored while Pro is active. The postfix is its own translatable
// string (RTL/localized upgrade words reorder), so we never locate a substring inside a combined
// name.
@Composable
internal fun upgradeScreenTitle(upgraded: Boolean): AnnotatedString = buildAnnotatedString {
    append(stringResource(CommonR.string.app_name))
    append(" ")
    if (upgraded) pushStyle(SpanStyle(color = colorResource(R.color.colorUpgraded)))
    append(stringResource(R.string.app_name_upgrade_postfix))
    if (upgraded) pop()
}

// Marker char for brand-title splicing: formatted into the translated pattern via the normal
// Android format path (so %1$s vs %s, argument reordering, and %% all behave), then replaced
// with the styled brand. U+FFFC (object replacement) cannot occur in a real translation.
internal const val BRAND_TITLE_MARKER = "￼"

internal fun spliceBrandTitle(formatted: String, brand: AnnotatedString): AnnotatedString = buildAnnotatedString {
    var rest = formatted
    var found = false
    while (true) {
        val idx = rest.indexOf(BRAND_TITLE_MARKER)
        if (idx < 0) break
        found = true
        append(rest.substring(0, idx))
        append(brand)
        rest = rest.substring(idx + BRAND_TITLE_MARKER.length)
    }
    append(rest)
    if (!found) {
        // Defensive: a translation that lost its placeholder still shows the brand.
        append(" ")
        append(brand)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpgradeScreenScaffold(
    title: AnnotatedString,
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = stringResource(R.string.upgrade_screen_navigate_up),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(it) }
        },
        content = content,
    )
}

@Composable
internal fun UpgradeScreenContent(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

// Octi has a single mascot, so `happy` selects only the test tag (the visual is identical) — it
// keeps the canonical happy/grumpy contract that the screen tests assert against.
@Composable
internal fun UpgradeMascot(
    size: Dp,
    modifier: Modifier = Modifier,
    happy: Boolean = true,
) {
    OctiMascot(
        modifier = modifier
            .size(size)
            .testTag(if (happy) UpgradeScreenTags.MASCOT_HAPPY else UpgradeScreenTags.MASCOT_GRUMPY),
    )
}

@Composable
internal fun UpgradeHeader(
    mascotSize: Dp,
    modifier: Modifier = Modifier,
    happy: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
            shape = CircleShape,
        ) {
            UpgradeMascot(
                size = mascotSize,
                modifier = Modifier.padding(16.dp),
                happy = happy,
            )
        }
    }
}

private val HERO_GAP = 16.dp

// Below this much room for the copy the side-by-side split stops paying for itself: measured on a
// 320dp screen at 200% font, the row wrapped the preamble over 10 lines (breaking a word mid-way)
// and came out TALLER than stacking, which needs 6. Scaled by fontScale because the squeeze comes
// from text size as much as from screen width — at 200% font even a normal-width phone must stack.
private val HERO_MIN_TEXT_WIDTH = 150.dp

// The screen opener: mascot and preamble in one card instead of a floating icon stacked on a
// separate text box. Side-by-side keeps the mascot at eye level with the copy it introduces, and
// buys back the vertical space the standalone header used to spend above the fold — but only while
// the copy still has room to breathe, hence the stacked fallback.
@Composable
internal fun UpgradeHeroCard(
    text: String,
    modifier: Modifier = Modifier,
    mascotSize: Dp = 88.dp,
    happy: Boolean = true,
    colors: CardColors = CardDefaults.elevatedCardColors(),
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.HERO),
        colors = colors,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .padding(end = 8.dp),
        ) {
            val minTextWidth = HERO_MIN_TEXT_WIDTH * LocalDensity.current.fontScale
            if (maxWidth - mascotSize - HERO_GAP < minTextWidth) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UpgradeMascot(
                        size = mascotSize,
                        happy = happy,
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HERO_GAP),
                ) {
                    UpgradeMascot(
                        size = mascotSize,
                        happy = happy,
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// Preview copy matches the shipped preamble in length: the mascot/text split only reads correctly
// if the text wraps like it does in the app.
private const val PREVIEW_PREAMBLE =
    "Octi has no ads and doesn't sell user data. My work is financed by you ❤️"

// The screen pads its content column by 24dp horizontally, so the previews do too — the hero's
// branch threshold is measured against the width that actually remains for the card.
@Preview2
@Composable
private fun UpgradeHeroCardPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            UpgradeHeroCard(text = PREVIEW_PREAMBLE)
        }
    }
}

// Preview2 only varies light/dark, so it can never reach the stacked branch. These two pin the
// thresholds that flip it: a narrow screen, and a normal-width screen at 200% font.
@Preview(showBackground = true, name = "Compact width", widthDp = 280)
@Preview(showBackground = true, name = "Huge font", fontScale = 2f)
@Composable
private fun UpgradeHeroCardCompactPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            UpgradeHeroCard(text = PREVIEW_PREAMBLE)
        }
    }
}

// Both flavors tint the hero: FOSS on primaryContainer, GPLAY on secondaryContainer. Neither is
// the composable's default, so the default-colored preview above would not catch a contrast
// regression on the colors that actually ship.
@Preview2
@Composable
private fun UpgradeHeroCardTintedPreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpgradeHeroCard(
                text = PREVIEW_PREAMBLE,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            UpgradeHeroCard(
                text = PREVIEW_PREAMBLE,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
internal fun UpgradeSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    colors: CardColors? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColors = colors ?: CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpgradeSectionHeader(
                title = title,
                icon = icon,
                iconTint = iconTint,
                leading = leading,
            )
            content()
        }
    }
}

// The icon+title header every section card leads with — also usable standalone so headerless cards
// (like the offers action card) can join the same visual pattern.
@Composable
internal fun UpgradeSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) {
            leading()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.primary else iconTint,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun UpgradeSectionBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

// Renders a feature blurb: bullet lines (leading • or -, some translations use hyphens) become
// checkmark rows, everything else stays plain paragraph text.
@Composable
internal fun UpgradeFeatureList(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val bullet = line.startsWith("•") || line.startsWith("-")
                if (bullet) {
                    UpgradeFeatureRow(text = line.drop(1).trim())
                } else {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

@Composable
private fun UpgradeFeatureRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.TwoTone.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun UpgradeHintText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun UpgradeActionCard(
    modifier: Modifier = Modifier,
    colors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColors = colors ?: CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun UpgradeLoadingBlock(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
            .testTag(UpgradeScreenTags.LOADING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.upgrade_screen_progress_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun UpgradeInlineStateCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    UpgradeSectionCard(
        title = title,
        icon = icon,
        modifier = modifier.testTag(UpgradeScreenTags.GPLAY_UNAVAILABLE),
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        content()
    }
}
