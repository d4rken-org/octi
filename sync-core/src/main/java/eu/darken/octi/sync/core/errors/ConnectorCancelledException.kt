package eu.darken.octi.sync.core.errors

import eu.darken.octi.sync.core.ConnectorId

/**
 * Terminal error for an operation that was explicitly cancelled via `ConnectorProcessor.cancel`,
 * i.e. superseded by a newer request rather than failing on its own.
 *
 * Deliberately NOT a [kotlinx.coroutines.CancellationException] — same rationale as
 * [ConnectorStoppedException]: the operation's waiter must see an ordinary connector failure, not
 * its own coroutine being cancelled.
 */
class ConnectorCancelledException(
    val connectorId: ConnectorId,
) : IllegalStateException("Operation on ${connectorId.logLabel} was cancelled")
