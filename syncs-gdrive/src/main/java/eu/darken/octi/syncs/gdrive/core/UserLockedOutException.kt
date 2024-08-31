package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import eu.darken.octi.syncs.gdrive.R

class UserLockedOutException(
    cause: Exception,
) : IllegalStateException(cause), HasLocalizedError {
    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.gdrive_account_lockout_error_label.toCaString(),
        description = R.string.gdrive_account_lockout_error_description.toCaString()
    )
}