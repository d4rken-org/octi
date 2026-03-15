package eu.darken.octi.modules.apps.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.installerIconRes

@Composable
fun AppsModuleItem(
    info: AppsInfo,
    onAppsClicked: () -> Unit,
    onInstallClicked: () -> Unit,
) {
    val last = info.installedPackages.maxByOrNull { it.installedAt }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAppsClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Apps,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(
                    AppsR.plurals.module_apps_x_installed,
                    info.installedPackages.size,
                    info.installedPackages.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (last != null) {
                Text(
                    text = stringResource(
                        AppsR.string.module_apps_last_installed_x,
                        "${last.label} (${last.versionName})",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (last != null) {
            IconButton(
                onClick = onInstallClicked,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(last.installerIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AppsModuleItemPreview() = PreviewWrapper {
    AppsModuleItem(
        info = AppsInfo(
            installedPackages = emptyList(),
        ),
        onAppsClicked = {},
        onInstallClicked = {},
    )
}
