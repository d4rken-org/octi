package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import eu.darken.octi.syncs.gdrive.R

class ScopeNotGrantedException(
    val scope: String,
) : IllegalStateException(), HasLocalizedError {
    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.gdrive_account_scope_error_label.toCaString(),
        description = R.string.gdrive_account_scope_error_description.toCaString()
    )
}