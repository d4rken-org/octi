package eu.darken.octi.modules.meta.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Computer
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.Public
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.clampToNow
import eu.darken.octi.common.compose.BottomSheetHeader
import eu.darken.octi.common.compose.CopyableDetailRow
import eu.darken.octi.common.compose.DetailRow
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.meta.R as MetaR
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.ui.labelRes
import eu.darken.octi.modules.meta.ui.osDisplayName
import eu.darken.octi.sync.core.DeviceId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
fun MetaDetailSheet(
    info: MetaInfo,
    deviceLabel: String,
    deviceIcon: ImageVector,
    lastUpdated: Instant?,
    onDismiss: () -> Unit,
    showMessage: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MetaDetailContent(
            info = info,
            deviceLabel = deviceLabel,
            deviceIcon = deviceIcon,
            lastUpdated = lastUpdated,
            showMessage = showMessage,
        )
    }
}

@Composable
private fun MetaDetailContent(
    info: MetaInfo,
    deviceLabel: String,
    deviceIcon: ImageVector,
    lastUpdated: Instant?,
    showMessage: (String) -> Unit = {},
) {
    val deviceTypeLabel = stringResource(info.deviceType.labelRes())
    val osLabel = info.osDisplayName()

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        BottomSheetHeader(
            icon = Icons.TwoTone.Info,
            title = stringResource(MetaR.string.module_meta_label),
            deviceLabel = deviceLabel,
            deviceIcon = deviceIcon,
        )

        DetailRow(
            label = stringResource(MetaR.string.module_meta_detail_manufacturer_label),
            value = info.deviceManufacturer,
        )
        DetailRow(
            label = stringResource(MetaR.string.module_meta_detail_model_label),
            value = info.deviceName,
        )
        DetailRow(
            label = stringResource(MetaR.string.module_meta_detail_device_type_label),
            value = deviceTypeLabel,
        )
        osLabel?.let { label ->
            DetailRow(
                label = stringResource(MetaR.string.module_meta_detail_os_label),
                value = label,
            )
        }
        info.androidVersionName?.let { version ->
            DetailRow(
                label = stringResource(MetaR.string.module_meta_detail_android_version_label),
                value = version,
            )
        }
        info.androidApiLevel?.let { api ->
            DetailRow(
                label = stringResource(MetaR.string.module_meta_detail_api_level_label),
                value = api.toString(),
            )
        }
        info.androidSecurityPatch?.let { patch ->
            DetailRow(
                label = stringResource(MetaR.string.module_meta_detail_security_patch_label),
                value = patch,
            )
        }
        info.deviceBootedAt?.let { booted ->
            DetailRow(
                label = stringResource(MetaR.string.module_meta_detail_booted_label),
                value = DateUtils
                    .getRelativeTimeSpanString(booted.clampToNow().toEpochMilliseconds())
                    .toString(),
            )
        }
        DetailRow(
            label = stringResource(MetaR.string.module_meta_detail_octi_version_label),
            value = info.octiVersionName,
        )
        val gitSha = info.octiGitSha.takeIf { it.isNotBlank() }
        CopyableDetailRow(
            label = stringResource(MetaR.string.module_meta_detail_git_sha_label),
            value = gitSha ?: "—",
            copyable = gitSha != null,
            showMessage = showMessage,
        )
        CopyableDetailRow(
            label = stringResource(MetaR.string.module_meta_detail_device_id_label),
            value = info.deviceId.id,
            copyable = true,
            showMessage = showMessage,
        )
        lastUpdated?.let { ts ->
            DetailRow(
                label = stringResource(MetaR.string.module_meta_detail_last_updated_label),
                value = DateUtils.getRelativeTimeSpanString(ts.toEpochMilliseconds()).toString(),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun previewInfo(
    deviceLabel: String? = "Living Room Pixel",
    androidSecurityPatch: String? = "2025-04-05",
    octiGitSha: String = "abc1234def5",
    bootedAgo: kotlin.time.Duration? = 2.days + 4.hours,
    deviceType: MetaInfo.DeviceType = MetaInfo.DeviceType.PHONE,
    deviceManufacturer: String = "Google",
    deviceName: String = "Pixel 8 Pro",
    androidVersionName: String? = "15",
    androidApiLevel: Int? = 35,
    osType: String? = "android",
    osVersionName: String? = "15",
): MetaInfo = MetaInfo(
    deviceLabel = deviceLabel,
    deviceId = DeviceId("preview-device-id-1234"),
    octiVersionName = "1.0.0-rc1",
    octiGitSha = octiGitSha,
    deviceManufacturer = deviceManufacturer,
    deviceName = deviceName,
    deviceType = deviceType,
    deviceBootedAt = bootedAgo?.let { Clock.System.now() - it },
    androidVersionName = androidVersionName,
    androidApiLevel = androidApiLevel,
    androidSecurityPatch = androidSecurityPatch,
    osType = osType,
    osVersionName = osVersionName,
)

@Preview2
@Composable
private fun MetaDetailContentFullPreview() = PreviewWrapper {
    MetaDetailContent(
        info = previewInfo(),
        deviceLabel = "Living Room Pixel",
        deviceIcon = Icons.TwoTone.PhoneAndroid,
        lastUpdated = Clock.System.now() - 5.minutes,
    )
}

@Preview2
@Composable
private fun MetaDetailContentMinimalPreview() = PreviewWrapper {
    MetaDetailContent(
        info = previewInfo(
            deviceLabel = null,
            androidSecurityPatch = null,
            octiGitSha = "",
        ),
        deviceLabel = "Pixel 8 Pro",
        deviceIcon = Icons.TwoTone.PhoneAndroid,
        lastUpdated = null,
    )
}

@Preview2
@Composable
private fun MetaDetailContentDesktopPreview() = PreviewWrapper {
    MetaDetailContent(
        info = previewInfo(
            deviceLabel = "Work Mac",
            deviceType = MetaInfo.DeviceType.DESKTOP,
            deviceManufacturer = "Apple",
            deviceName = "MacBook Pro",
            androidVersionName = null,
            androidApiLevel = null,
            androidSecurityPatch = null,
            osType = "macos",
            osVersionName = "14.4",
        ),
        deviceLabel = "Work Mac",
        deviceIcon = Icons.TwoTone.Computer,
        lastUpdated = Clock.System.now() - 2.minutes,
    )
}

@Preview2
@Composable
private fun MetaDetailContentBrowserPreview() = PreviewWrapper {
    MetaDetailContent(
        info = previewInfo(
            deviceLabel = "Browser Tab",
            deviceType = MetaInfo.DeviceType.BROWSER,
            deviceManufacturer = "Mozilla",
            deviceName = "Firefox 130",
            androidVersionName = null,
            androidApiLevel = null,
            androidSecurityPatch = null,
            osType = "browser",
            osVersionName = "130",
            bootedAgo = null,
        ),
        deviceLabel = "Browser Tab",
        deviceIcon = Icons.TwoTone.Public,
        lastUpdated = Clock.System.now() - 1.minutes,
    )
}
