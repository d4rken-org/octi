package eu.darken.octi.syncs.octiserver.ui.link.host

import android.app.Activity
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.syncs.octiserver.ui.link.OctiServerLinkOption
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.syncs.octiserver.R as OctiServerR

@Composable
fun OctiServerLinkHostScreenHost(
    connectorId: String,
    vm: OctiServerLinkHostVM = hiltViewModel(),
) {
    vm.initialize(connectorId)

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        vm.onScreenVisible()
        vm.deviceLinkedEvents.collect {
            Toast.makeText(
                context,
                OctiServerR.string.sync_octiserver_link_host_device_linked_message,
                Toast.LENGTH_LONG
            ).show()
            vm.navUp()
        }
    }

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        OctiServerLinkHostScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onLinkOptionSelected = { option -> vm.onLinkOptionSelected(option) },
            onShareLinkCode = { activity?.let { act -> vm.shareLinkCode(act) } },
        )
    }
}

@Composable
fun OctiServerLinkHostScreen(
    state: OctiServerLinkHostVM.State,
    onNavigateUp: () -> Unit,
    onLinkOptionSelected: (OctiServerLinkOption) -> Unit,
    onShareLinkCode: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(OctiServerR.string.sync_octiserver_type_label))
                        Text(
                            text = stringResource(OctiServerR.string.sync_octiserver_link_device_action),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        // BoxWithConstraints sits OUTSIDE any scroll on purpose: it is the only place that sees the
        // real viewport height. Inside a verticalScroll the height constraint is Infinity, which is
        // what let the QR grow to a full-screen-width square and run off the bottom of the display.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Captured here: inside the Row/Column below these are no longer reachable as implicit
            // receivers, and they are the only reading of the true viewport in the whole screen.
            val viewportHeight = maxHeight
            val isWide = maxWidth > viewportHeight

            if (isWide) {
                // Landscape and TV: stacking the QR under the option card leaves it far less height
                // than the display has width. Side by side, the QR pane carries no other content,
                // so it gets the full viewport height and needs no allowance for chrome.
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        // Only the card scrolls, so a large font scale or a long translation cannot
                        // clip the options out of reach while the indicator stays pinned below.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            LinkOptions(
                                selected = state.linkOption,
                                onLinkOptionSelected = onLinkOptionSelected,
                            )
                        }
                        WaitingIndicator(modifier = Modifier.padding(bottom = 16.dp))
                    }
                    LinkContent(
                        state = state,
                        onShareLinkCode = onShareLinkCode,
                        // No cap needed and no scroll around it: this pane is bounded, so QrCode
                        // measures the real height itself. A scroll here would hand it Infinity
                        // again and let its own padding push it back off the bottom.
                        contentScrolls = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                    )
                }
            } else {
                // Stacked: the whole column scrolls, so the QR cannot be sized by weight. Capping it
                // against the viewport is what keeps it on screen; the cap is generous enough that
                // width is the binding constraint on a normal phone, and the scroll absorbs the rest
                // when a large font scale grows the card beyond the space left over.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    LinkOptions(
                        selected = state.linkOption,
                        onLinkOptionSelected = onLinkOptionSelected,
                    )
                    LinkContent(
                        state = state,
                        onShareLinkCode = onShareLinkCode,
                        qrMaxSide = viewportHeight * STACKED_QR_VIEWPORT_FRACTION,
                        // The Column above already scrolls; a second one here would nest.
                        contentScrolls = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                    WaitingIndicator(modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun LinkOptions(
    selected: OctiServerLinkOption,
    onLinkOptionSelected: (OctiServerLinkOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .selectableGroup(),
        ) {
            LinkOptionRow(
                option = OctiServerLinkOption.QRCODE,
                selected = selected,
                labelRes = OctiServerR.string.sync_octiserver_link_host_option_qrcode,
                onLinkOptionSelected = onLinkOptionSelected,
            )
            LinkOptionRow(
                option = OctiServerLinkOption.DIRECT,
                selected = selected,
                labelRes = OctiServerR.string.sync_octiserver_link_host_option_direct,
                onLinkOptionSelected = onLinkOptionSelected,
            )
        }
    }
}

@Composable
private fun LinkOptionRow(
    option: OctiServerLinkOption,
    selected: OctiServerLinkOption,
    @StringRes labelRes: Int,
    onLinkOptionSelected: (OctiServerLinkOption) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected == option,
                role = Role.RadioButton,
                onClick = { onLinkOptionSelected(option) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == option, onClick = null)
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LinkContent(
    state: OctiServerLinkHostVM.State,
    onShareLinkCode: () -> Unit,
    contentScrolls: Boolean,
    modifier: Modifier = Modifier,
    qrMaxSide: Dp = Dp.Infinity,
) = when (state.linkOption) {
    OctiServerLinkOption.QRCODE -> QrCode(
        encodedLinkCode = state.encodedLinkCode,
        maxSide = qrMaxSide,
        modifier = modifier,
    )

    OctiServerLinkOption.DIRECT -> TextCode(
        encodedLinkCode = state.encodedLinkCode,
        onShareLinkCode = onShareLinkCode,
        // Only the text code can outgrow its pane, and only where nothing else scrolls already.
        modifier = if (contentScrolls) modifier.verticalScroll(rememberScrollState()) else modifier,
    )
}

@Composable
private fun QrCode(
    encodedLinkCode: String?,
    maxSide: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // The largest square that fits, so the QR can never exceed the space it was given. maxHeight
        // is unbounded when an ancestor scrolls, which is why the caller also supplies [maxSide].
        val side = min(min(maxWidth, maxHeight), maxSide)
        val sidePx = with(LocalDensity.current) { side.roundToPx() }

        val qrBitmap = remember(encodedLinkCode, sidePx) {
            if (encodedLinkCode == null || sidePx <= 0) {
                null
            } else {
                try {
                    // Encoded at the size it is actually drawn at. A fixed 512px bitmap stretched
                    // across a 1080p TV blurs the module edges, and a blurry QR photographed off a
                    // TV panel is markedly harder for a phone camera to decode.
                    BarcodeEncoder().encodeBitmap(encodedLinkCode, BarcodeFormat.QR_CODE, sidePx, sidePx)
                } catch (_: Exception) {
                    null
                }
            }
        }

        qrBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                // Nearest-neighbour: bilinear filtering softens module edges and costs scan range.
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(side),
            )
        }
    }
}

@Composable
private fun TextCode(
    encodedLinkCode: String?,
    onShareLinkCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No scroll of its own: each layout branch owns exactly one scroll container, otherwise the
    // stacked branch would nest two vertical scrolls and measure this one against Infinity.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(OctiServerR.string.sync_octiserver_link_code_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp),
        )

        Text(
            text = encodedLinkCode ?: "",
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onShareLinkCode,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(CommonR.string.general_share_action))
        }
    }
}

@Composable
private fun WaitingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(OctiServerR.string.sync_octiserver_link_host_waiting_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview2
@Composable
private fun OctiServerLinkHostScreenQRPreview() = PreviewWrapper {
    OctiServerLinkHostScreen(
        state = OctiServerLinkHostVM.State(
            linkOption = OctiServerLinkOption.QRCODE,
            encodedLinkCode = PREVIEW_CODE,
        ),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onShareLinkCode = {},
    )
}

@Preview2
@Composable
private fun OctiServerLinkHostScreenDirectPreview() = PreviewWrapper {
    OctiServerLinkHostScreen(
        state = OctiServerLinkHostVM.State(
            linkOption = OctiServerLinkOption.DIRECT,
            encodedLinkCode = PREVIEW_CODE,
        ),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onShareLinkCode = {},
    )
}

/**
 * How much of the viewport the QR may claim in the stacked layout. The option card and the waiting
 * row take the rest, and because that column scrolls their height cannot be measured up front. On a
 * normal phone the screen width binds first and this cap never applies; it exists so an unusually
 * short viewport degrades into scrolling rather than an off-screen QR.
 */
private const val STACKED_QR_VIEWPORT_FRACTION = 0.55f

/** Same length as a real link code, so previews show the layout at the size it actually renders. */
private val PREVIEW_CODE = "H4sIAAAAAAAAA" + "Wm9jdGlwcmV2aWV3".repeat(30) + "AAA=="
