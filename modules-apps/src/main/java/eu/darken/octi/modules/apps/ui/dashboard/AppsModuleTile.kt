package eu.darken.octi.modules.apps.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import java.time.Instant
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
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleMedium,
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

private val PREVIEW_PACKAGES = listOf(
    AppsInfo.Pkg(
        packageName = "com.whatsapp",
        label = "WhatsApp",
        versionCode = 231676,
        versionName = "2.24.8.78",
        installedAt = Instant.ofEpochMilli(1710800000000),
        installerPkg = "com.android.vending",
    ),
    AppsInfo.Pkg(
        packageName = "org.mozilla.firefox",
        label = "Firefox",
        versionCode = 2016042,
        versionName = "124.1.0",
        installedAt = Instant.ofEpochMilli(1710900000000),
        installerPkg = "org.fdroid.fdroid",
    ),
    AppsInfo.Pkg(
        packageName = "eu.darken.sdmse",
        label = "SD Maid 2/SE",
        versionCode = 40803,
        versionName = "4.8.3-rc0",
        installedAt = Instant.ofEpochMilli(1710950000000),
        installerPkg = "com.android.vending",
    ),
)

@Preview2
@Composable
private fun AppsModuleTileHalfPreview() = PreviewWrapper {
    AppsModuleTile(
        info = AppsInfo(installedPackages = PREVIEW_PACKAGES),
        onAppsClicked = {},
        onInstallClicked = {},
    )
}

@Preview2
@Composable
private fun AppsModuleTileWidePreview() = PreviewWrapper {
    AppsModuleTile(
        info = AppsInfo(installedPackages = PREVIEW_PACKAGES),
        isWide = true,
        onAppsClicked = {},
        onInstallClicked = {},
    )
}

@Preview2
@Composable
private fun AppsModuleTileEmptyPreview() = PreviewWrapper {
    AppsModuleTile(
        info = AppsInfo(installedPackages = emptyList()),
        onAppsClicked = {},
        onInstallClicked = {},
    )
}
