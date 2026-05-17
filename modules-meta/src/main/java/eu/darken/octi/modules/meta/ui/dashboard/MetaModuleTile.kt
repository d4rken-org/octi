package eu.darken.octi.modules.meta.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.meta.R as MetaR
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.ui.formatUptime
import eu.darken.octi.modules.meta.ui.materialIcon
import eu.darken.octi.modules.meta.ui.osDisplayName
import eu.darken.octi.sync.core.DeviceId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Composable
fun MetaModuleTile(
    info: MetaInfo,
    now: Instant,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onDetailClicked: () -> Unit,
) {
    val osLabel = info.osDisplayName()
    val uptimeLabel = info.deviceBootedAt?.let {
        stringResource(MetaR.string.module_meta_uptime_label, formatUptime(now, it))
    }

    val tileColor = if (isWide) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val tileDescription = listOfNotNull(info.deviceName, osLabel).joinToString(" ")

    Surface(
        onClick = onDetailClicked,
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
                    imageVector = info.deviceType.materialIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = info.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            osLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            uptimeLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun previewInfo(
    deviceName: String = "Pixel 8 Pro",
    deviceType: MetaInfo.DeviceType = MetaInfo.DeviceType.PHONE,
    osType: String? = "android",
    osVersionName: String? = "15",
    androidVersionName: String? = "15",
    androidApiLevel: Int? = 35,
    bootedAgo: kotlin.time.Duration? = 2.days + 4.hours,
): MetaInfo = MetaInfo(
    deviceLabel = null,
    deviceId = DeviceId("preview"),
    octiVersionName = "1.0.0",
    octiGitSha = "abc1234",
    deviceManufacturer = "Google",
    deviceName = deviceName,
    deviceType = deviceType,
    deviceBootedAt = bootedAgo?.let { Clock.System.now() - it },
    androidVersionName = androidVersionName,
    androidApiLevel = androidApiLevel,
    androidSecurityPatch = "2025-04-05",
    osType = osType,
    osVersionName = osVersionName,
)

@Preview2
@Composable
private fun MetaModuleTileHalfPreview() = PreviewWrapper {
    MetaModuleTile(
        info = previewInfo(),
        now = Clock.System.now(),
        onDetailClicked = {},
    )
}

@Preview2
@Composable
private fun MetaModuleTileWidePreview() = PreviewWrapper {
    MetaModuleTile(
        info = previewInfo(),
        now = Clock.System.now(),
        isWide = true,
        onDetailClicked = {},
    )
}

@Preview2
@Composable
private fun MetaModuleTileTabletLongNamePreview() = PreviewWrapper {
    MetaModuleTile(
        info = previewInfo(
            deviceName = "Galaxy Tab S9 Ultra Plus 5G Wifi Edition",
            deviceType = MetaInfo.DeviceType.TABLET,
            osVersionName = "14",
            androidVersionName = "14",
            androidApiLevel = 34,
        ),
        now = Clock.System.now(),
        onDetailClicked = {},
    )
}

@Preview2
@Composable
private fun MetaModuleTileDesktopPreview() = PreviewWrapper {
    MetaModuleTile(
        info = previewInfo(
            deviceName = "MacBook Pro",
            deviceType = MetaInfo.DeviceType.DESKTOP,
            osType = "macos",
            osVersionName = "14.4",
            androidVersionName = null,
            androidApiLevel = null,
        ).copy(deviceManufacturer = "Apple"),
        now = Clock.System.now(),
        onDetailClicked = {},
    )
}

@Preview2
@Composable
private fun MetaModuleTileBrowserPreview() = PreviewWrapper {
    MetaModuleTile(
        info = previewInfo(
            deviceName = "Firefox 130",
            deviceType = MetaInfo.DeviceType.BROWSER,
            osType = "browser",
            osVersionName = "130",
            androidVersionName = null,
            androidApiLevel = null,
            bootedAgo = null,
        ).copy(deviceManufacturer = "Mozilla"),
        now = Clock.System.now(),
        onDetailClicked = {},
    )
}
