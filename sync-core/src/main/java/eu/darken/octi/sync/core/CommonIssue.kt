package eu.darken.octi.sync.core

import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.caString
import eu.darken.octi.sync.R
import kotlin.time.Instant

sealed interface CommonIssue : ConnectorIssue {

    data class StaleDevice(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
        val lastSeen: Instant,
    ) : CommonIssue {
        override val severity: IssueSeverity = IssueSeverity.WARNING
        override val label: CaString = caString { it.getString(R.string.sync_issues_type_stale_device) }
        override val description: CaString = caString {
            val period = StalenessUtil.formatStalePeriod(it, lastSeen)
            it.getString(R.string.sync_device_stale_warning_text, period)
        }
    }

    data class OutdatedVersion(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
        val version: String,
    ) : CommonIssue {
        override val severity: IssueSeverity = IssueSeverity.WARNING
        override val label: CaString = caString { it.getString(R.string.sync_issues_type_outdated_version) }
        override val description: CaString = caString { it.getString(R.string.sync_device_outdated_version_warning, version) }
    }

    data class ClockSkew(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
    ) : CommonIssue {
        override val severity: IssueSeverity = IssueSeverity.WARNING
        override val label: CaString = caString { it.getString(R.string.sync_issues_type_clock_skew) }
        override val description: CaString = caString { it.getString(R.string.sync_device_clock_skew_warning_text) }
    }

    data class ServerStorageLow(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
    ) : CommonIssue {
        override val severity: IssueSeverity = IssueSeverity.ERROR
        override val label: CaString = caString { it.getString(R.string.sync_issues_type_server_storage_low) }
        override val description: CaString = caString { it.getString(R.string.sync_server_storage_low_description) }
    }

    data class AccountQuotaFull(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
    ) : CommonIssue {
        override val severity: IssueSeverity = IssueSeverity.WARNING
        override val label: CaString = caString { it.getString(R.string.sync_issues_type_account_quota_full) }
        override val description: CaString = caString { it.getString(R.string.sync_account_quota_full_description) }
    }
}
