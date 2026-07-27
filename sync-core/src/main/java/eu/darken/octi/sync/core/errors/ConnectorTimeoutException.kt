package eu.darken.octi.sync.core.errors

import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.ConnectorId
import kotlin.time.Duration

/**
 * Terminal error for a command whose executor exceeded the processor's per-command bound.
 *
 * Deliberately NOT a [kotlinx.coroutines.CancellationException] — same rationale as
 * [ConnectorStoppedException]. The processor's own cancellation branch would otherwise record an
 * expired command as a plain cancellation, and callers that catch cancellation to honor structured
 * concurrency must treat this as an ordinary connector failure and carry on.
 */
class ConnectorTimeoutException(
    val connectorId: ConnectorId,
    val command: ConnectorCommand,
    val timeout: Duration,
) : IllegalStateException("Connector ${connectorId.logLabel} timed out after $timeout on $command")
