package eu.darken.octi.syncs.gdrive.core

import android.content.Context
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import eu.darken.octi.syncs.gdrive.R

class UserLockedOutException(
    cause: Exception,
) : IllegalStateException(cause), HasLocalizedError {
    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.gdrive_account_lockout_error_label),
        description = context.getString(R.string.gdrive_account_lockout_error_description)
    )
}