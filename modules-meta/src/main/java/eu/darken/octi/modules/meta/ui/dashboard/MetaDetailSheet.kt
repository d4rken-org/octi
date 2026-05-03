package eu.darken.octi.modules.meta.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.clampToNow
import eu.darken.octi.common.compose.CopyableDetailRow
import eu.darken.octi.common.compose.DetailRow
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.meta.R as MetaR
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
fun MetaDetailSheet(
    info: MetaInfo,
    lastUpdated: Instant?,
    onDismiss: () -> Unit,
    showMessage: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MetaDetailContent(info = info, lastUpdated = lastUpdated, showMessage = showMessage)
    }
}

@Composable
private fun MetaDetailContent(
    info: MetaInfo,
    lastUpdated: Instant?,
    showMessage: (String) -> Unit = {},
) {
    val deviceTypeLabel = when (info.deviceType) {
        MetaInfo.DeviceType.PHONE -> stringResource(MetaR.string.module_meta_detail_device_type_phone)
        MetaInfo.DeviceType.TABLET -> stringResource(MetaR.string.module_meta_detail_device_type_tablet)
        MetaInfo.DeviceType.UNKNOWN -> stringResource(MetaR.string.module_meta_detail_device_type_unknown)
    }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(MetaR.string.module_meta_label),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        info.deviceLabel?.let { label ->
            DetailRow(
                label = stringResource(MetaR.string.module_meta_detail_label_label),
                value = label,
            )
        }
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
        DetailRow(
            label = stringResource(MetaR.string.module_meta_detail_android_version_label),
            value = info.androidVersionName,
        )
        DetailRow(
            label = stringResource(MetaR.string.module_meta_detail_api_level_label),
            value = info.androidApiLevel.toString(),
        )
        info.androidSecurityPatch?.let { patch ->
            DetailRow(
                label = stringResource(MetaR.string.module_meta_detail_security_patch_label),
                value = patch,
            )
        }
        DetailRow(
            label = stringResource(MetaR.string.module_meta_detail_booted_label),
            value = DateUtils
                .getRelativeTimeSpanString(info.deviceBootedAt.clampToNow().toEpochMilliseconds())
                .toString(),
        )
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
    bootedAgo: kotlin.time.Duration = 2.days + 4.hours,
): MetaInfo = MetaInfo(
    deviceLabel = deviceLabel,
    deviceId = DeviceId("preview-device-id-1234"),
    octiVersionName = "1.0.0-rc1",
    octiGitSha = octiGitSha,
    deviceManufacturer = "Google",
    deviceName = "Pixel 8 Pro",
    deviceType = MetaInfo.DeviceType.PHONE,
    deviceBootedAt = Clock.System.now() - bootedAgo,
    androidVersionName = "15",
    androidApiLevel = 35,
    androidSecurityPatch = androidSecurityPatch,
)

@Preview2
@Composable
private fun MetaDetailContentFullPreview() = PreviewWrapper {
    MetaDetailContent(
        info = previewInfo(),
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
        lastUpdated = null,
    )
}
