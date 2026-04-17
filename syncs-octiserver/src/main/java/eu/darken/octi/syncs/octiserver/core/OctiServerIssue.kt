package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.caString
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.syncs.octiserver.R

sealed interface OctiServerIssue : ConnectorIssue {

    data class EncryptionIncompatible(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
        val deviceLabel: String?,
    ) : OctiServerIssue {
        override val severity: IssueSeverity = IssueSeverity.ERROR
        override val label: CaString = caString { it.getString(R.string.sync_octiserver_issues_type_encryption_error) }
        override val description: CaString = caString { it.getString(R.string.sync_octiserver_device_encryption_incompatible) }
    }

    data class BlobEncryptionUnsupported(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
    ) : OctiServerIssue {
        override val severity: IssueSeverity = IssueSeverity.WARNING
        override val label: CaString = caString { it.getString(R.string.sync_octiserver_issues_type_encryption_error) }
        override val description: CaString = caString { it.getString(R.string.sync_octiserver_blob_encryption_unsupported) }
    }
}
