package eu.darken.octi.modules.apps.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.installerIconRes

@Composable
fun AppsModuleTile(
    info: AppsInfo,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onAppsClicked: () -> Unit,
    onInstallClicked: () -> Unit,
) {
    val count = info.installedPackages.size
    val last = info.installedPackages.maxByOrNull { it.installedAt }
    val installedLabel = pluralStringResource(AppsR.plurals.module_apps_x_installed, count, count)

    val tileColor = if (isWide) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val tileDescription = installedLabel

    Surface(
        onClick = onAppsClicked,
        modifier = modifier.semantics { contentDescription = tileDescription },
        shape = RoundedCornerShape(12.dp),
        color = tileColor,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (last != null) {
                    IconButton(
                        onClick = onInstallClicked,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            painter = painterResource(last.installerIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(AppsR.string.module_apps_tile_apps_installed_label),
                style = MaterialTheme.typography.bodySmall,
            )
            if (last != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        AppsR.string.module_apps_last_installed_x,
                        last.label ?: last.packageName,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AppsModuleTilePreview() = PreviewWrapper {
    AppsModuleTile(
        info = AppsInfo(installedPackages = emptyList()),
        onAppsClicked = {},
        onInstallClicked = {},
    )
}
