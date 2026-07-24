package eu.darken.octi.sync.core.errors

import eu.darken.octi.sync.core.ConnectorId

/**
 * Terminal error for operations that were still pending when a connector's processor shut down (its
 * scope was cancelled). Deliberately NOT a [kotlinx.coroutines.CancellationException]: a shutdown must
 * fail waiters, but callers that catch cancellation to honor structured concurrency (e.g. the
 * per-connector loop in `ForegroundSyncControl`) must treat this as an ordinary connector failure and
 * carry on, not as their own coroutine being cancelled.
 */
class ConnectorStoppedException(
    val connectorId: ConnectorId,
) : IllegalStateException("Connector ${connectorId.logLabel} stopped")
