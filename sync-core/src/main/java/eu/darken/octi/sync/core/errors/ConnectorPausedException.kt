package eu.darken.octi.sync.core.errors

import eu.darken.octi.common.ca.caString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import eu.darken.octi.sync.R
import eu.darken.octi.sync.core.ConnectorId

class ConnectorPausedException(
    val connectorId: ConnectorId,
) : IllegalStateException("Connector ${connectorId.logLabel} is paused"), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = caString { getString(R.string.sync_connector_paused_error_label) },
        description = caString { getString(R.string.sync_connector_paused_error_desc) },
    )
}
