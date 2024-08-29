package eu.darken.octi.syncs.gdrive.core

import android.content.Context
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import eu.darken.octi.syncs.gdrive.R

class ScopeNotGrantedException(
    val scope: String,
) : IllegalStateException(), HasLocalizedError {
    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.gdrive_account_scope_error_label),
        description = context.getString(R.string.gdrive_account_scope_error_description)
    )
}