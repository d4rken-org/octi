package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.caString
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.sync.core.encryption.EncryptionMode
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

    data class LegacyEncryptionAccount(
        override val connectorId: ConnectorId,
        override val deviceId: DeviceId,
    ) : OctiServerIssue {
        override val severity: IssueSeverity = IssueSeverity.WARNING
        override val label: CaString = caString { it.getString(R.string.sync_octiserver_issues_type_legacy_encryption) }
        override val description: CaString = caString { it.getString(R.string.sync_octiserver_legacy_encryption_account) }
    }

    sealed interface EncryptionCompatibilityIncompatible : OctiServerIssue {
        override val connectorId: ConnectorId
        override val deviceId: DeviceId

        /**
         * Android peer whose reported version is below the GCM-SIV-capable minimum, and
         * which hasn't published explicit capabilities yet. The remedy is to update the
         * Octi Android app on that device.
         */
        data class AndroidClientTooOld(
            override val connectorId: ConnectorId,
            override val deviceId: DeviceId,
            val minClientVersion: String,
        ) : EncryptionCompatibilityIncompatible {
            override val severity: IssueSeverity = IssueSeverity.ERROR
            override val label: CaString = caString {
                it.getString(R.string.sync_octiserver_issues_type_encryption_compatibility)
            }
            override val description: CaString = caString {
                it.getString(R.string.sync_octiserver_encryption_compatibility_incompatible, minClientVersion)
            }
        }

        /**
         * Any peer (Android or otherwise) that explicitly reports it doesn't support the
         * encryption mode this account uses. The remedy is for that device's maintainer
         * to add support — there's nothing the user can do on this device.
         */
        data class UnsupportedEncryptionMode(
            override val connectorId: ConnectorId,
            override val deviceId: DeviceId,
            val requiredMode: EncryptionMode,
        ) : EncryptionCompatibilityIncompatible {
            override val severity: IssueSeverity = IssueSeverity.ERROR
            override val label: CaString = caString {
                it.getString(R.string.sync_octiserver_issues_type_encryption_compatibility)
            }
            override val description: CaString = caString {
                it.getString(
                    R.string.sync_octiserver_encryption_compatibility_unsupported_mode,
                    requiredMode.typeString,
                )
            }
        }
    }
}
