package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.caString
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.syncs.octiserver.R

sealed interface OctiServerIssue : ConnectorIssue {

    data class CurrentDeviceNotRegistered(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
    ) : OctiServerIssue {
        override val severity: IssueSeverity = IssueSeverity.ERROR
        override val label: CaString = caString { it.getString(R.string.sync_octiserver_issues_type_device_not_registered) }
        override val description: CaString = caString { it.getString(R.string.sync_octiserver_error_device_not_registered) }
    }

    data class BlobEncryptionUnsupported(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
    ) : OctiServerIssue {
        override val severity: IssueSeverity = IssueSeverity.WARNING
        override val label: CaString = caString { it.getString(R.string.sync_octiserver_issues_type_file_sharing_unavailable) }
        override val description: CaString = caString { it.getString(R.string.sync_octiserver_blob_encryption_unsupported) }
    }

    data class EncryptionCompatibilityIncompatible(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
        val minClientVersion: String,
    ) : OctiServerIssue {
        override val severity: IssueSeverity = IssueSeverity.ERROR
        override val label: CaString = caString {
            it.getString(R.string.sync_octiserver_issues_type_encryption_compatibility)
        }
        override val description: CaString = caString {
            it.getString(R.string.sync_octiserver_encryption_compatibility_incompatible, minClientVersion)
        }
    }
}
