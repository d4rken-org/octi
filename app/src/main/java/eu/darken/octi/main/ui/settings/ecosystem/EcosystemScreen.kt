package eu.darken.octi.main.ui.settings.ecosystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.DesktopWindows
import androidx.compose.material.icons.twotone.Dns
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsBaseItem

@Composable
fun EcosystemScreenHost(vm: EcosystemVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    EcosystemScreen(
        onNavigateUp = { vm.navUp() },
        onOpenUrl = { url -> vm.openUrl(url) },
    )
}

@Composable
fun EcosystemScreen(
    onNavigateUp: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_ecosystem_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Text(
                    text = stringResource(R.string.settings_ecosystem_header_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_ecosystem_web_label),
                    subtitle = stringResource(R.string.settings_ecosystem_web_desc),
                    icon = Icons.TwoTone.Language,
                    onClick = { onOpenUrl("https://github.com/d4rken-org/octi-web") },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_ecosystem_desktop_label),
                    subtitle = stringResource(R.string.settings_ecosystem_desktop_desc),
                    icon = Icons.TwoTone.DesktopWindows,
                    onClick = { onOpenUrl("https://github.com/d4rken-org/octi-desktop") },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_ecosystem_server_label),
                    subtitle = stringResource(R.string.settings_ecosystem_server_desc),
                    icon = Icons.TwoTone.Dns,
                    onClick = { onOpenUrl("https://github.com/d4rken-org/octi-server") },
                )
            }
        }
    }
}

@Preview2
@Composable
private fun EcosystemScreenPreview() = PreviewWrapper {
    EcosystemScreen(onNavigateUp = {}, onOpenUrl = {})
}
