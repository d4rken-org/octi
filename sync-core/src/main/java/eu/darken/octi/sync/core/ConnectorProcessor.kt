package eu.darken.octi.sync.core

import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.errors.ConnectorPausedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

/**
 * Actor-like command processor for a [SyncConnector].
 *
 * Composition, not inheritance: each connector owns a [ConnectorProcessor] as a field and passes
 * its command-dispatch logic as [executor]. The connector delegates the [SyncConnector] operations
 * surface to this processor.
 *
 * Submit commands with [submit] (non-suspending); the processor drains them serially via
 * [executor]. Every command's lifecycle is published via [operations] (for UI display, bounded by
 * [displayRetention]) and [completions] (every terminal state emitted exactly once). Per-op
 * [CompletableDeferred]s back [await] and are independent of retention, so waiters can never be
 * starved by trimming.
 *
 * Hubs own the processor lifetime: construct the connector, then call [start] with a scope tied
 * to the connector's lifetime. Cancelling that scope fails any still-pending ops so waiters don't
 * hang.
 */
class ConnectorProcessor(
    private val connectorId: ConnectorId,
    private val syncSettings: SyncSettings,
    private val displayRetention: Int = 20,
    private val executor: suspend (ConnectorCommand) -> Unit,
) {

    private data class Pending(
        val queued: ConnectorOperation.Queued,
        val result: CompletableDeferred<ConnectorOperation.Terminal>,
    )

    private val inbox = Channel<Pending>(capacity = Channel.UNLIMITED)
    private val pending = ConcurrentHashMap<OperationId, Pending>()

    private val _operations = MutableStateFlow<List<ConnectorOperation>>(emptyList())
    val operations: StateFlow<List<ConnectorOperation>> = _operations.asStateFlow()

    private val _completions = MutableSharedFlow<ConnectorOperation.Terminal>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val completions: SharedFlow<ConnectorOperation.Terminal> = _completions.asSharedFlow()

    fun start(scope: CoroutineScope): Job = scope.launch {
        log(TAG, VERBOSE) { "processor($connectorId): started" }
        try {
            for (entry in inbox) processOne(entry)
        } finally {
            log(TAG, VERBOSE) { "processor($connectorId): stopping, failing ${pending.size} pending" }
            val now = Clock.System.now()
            pending.values.forEach { entry ->
                if (!entry.result.isCompleted) {
                    val failed = ConnectorOperation.Failed(
                        id = entry.queued.id,
                        command = entry.queued.command,
                        submittedAt = entry.queued.submittedAt,
                        startedAt = now,
                        finishedAt = now,
                        error = CancellationException("Connector shutting down"),
                    )
                    entry.result.complete(failed)
                }
            }
            pending.clear()
        }
    }

    fun submit(command: ConnectorCommand): OperationId {
        val id = OperationId.create()
        val queued = ConnectorOperation.Queued(id, command, Clock.System.now())
        val deferred = CompletableDeferred<ConnectorOperation.Terminal>()
        val entry = Pending(queued, deferred)
        pending[id] = entry
        _operations.update { (it + queued).trim() }
        val sendResult = inbox.trySend(entry)
        if (sendResult.isFailure) {
            val now = Clock.System.now()
            val failed = ConnectorOperation.Failed(
                id = id,
                command = command,
                submittedAt = queued.submittedAt,
                startedAt = now,
                finishedAt = now,
                error = IllegalStateException("Connector inbox closed"),
            )
            pending.remove(id)?.result?.complete(failed)
            _operations.update { ops -> ops.map { if (it.id == id) failed else it }.trim() }
            _completions.tryEmit(failed)
        }
        return id
    }

    suspend fun await(id: OperationId): ConnectorOperation.Terminal {
        val entry = pending[id]
        if (entry != null) return entry.result.await()
        _operations.value.firstOrNull { it.id == id }?.let {
            if (it is ConnectorOperation.Terminal) return it
        }
        error("No operation with id=$id known to processor($connectorId)")
    }

    fun dismiss(id: OperationId) {
        _operations.update { ops -> ops.filterNot { it is ConnectorOperation.Terminal && it.id == id } }
    }

    private suspend fun processOne(entry: Pending) {
        val startedAt = Clock.System.now()
        val processing = ConnectorOperation.Processing(
            id = entry.queued.id,
            command = entry.queued.command,
            submittedAt = entry.queued.submittedAt,
            startedAt = startedAt,
        )
        _operations.update { ops -> ops.map { if (it.id == entry.queued.id) processing else it } }

        val terminal: ConnectorOperation.Terminal = try {
            guardPauseIfNeeded(entry.queued.command)
            executor(entry.queued.command)
            ConnectorOperation.Succeeded(
                id = entry.queued.id,
                command = entry.queued.command,
                submittedAt = entry.queued.submittedAt,
                startedAt = startedAt,
                finishedAt = Clock.System.now(),
            )
        } catch (e: CancellationException) {
            val failed = ConnectorOperation.Failed(
                id = entry.queued.id,
                command = entry.queued.command,
                submittedAt = entry.queued.submittedAt,
                startedAt = startedAt,
                finishedAt = Clock.System.now(),
                error = e,
            )
            publishTerminal(entry, failed)
            // If the processor scope itself is cancelling, rethrow so the loop exits; otherwise
            // keep looping so a per-command timeout/cancel doesn't kill the actor.
            currentCoroutineContext().ensureActive()
            return
        } catch (e: Throwable) {
            log(TAG, ERROR) { "processor($connectorId) ${entry.queued.command}: ${e.asLog()}" }
            ConnectorOperation.Failed(
                id = entry.queued.id,
                command = entry.queued.command,
                submittedAt = entry.queued.submittedAt,
                startedAt = startedAt,
                finishedAt = Clock.System.now(),
                error = e,
            )
        }

        publishTerminal(entry, terminal)

        // After a successful Resume, enqueue a Sync on our own queue. Submitting here (inside
        // the processing of Resume) guarantees Sync lands in the inbox before any subsequent
        // user Pause, so the post-resume sync is deterministic.
        if (terminal is ConnectorOperation.Succeeded && entry.queued.command == ConnectorCommand.Resume) {
            submit(ConnectorCommand.Sync())
        }
    }

    private fun publishTerminal(entry: Pending, terminal: ConnectorOperation.Terminal) {
        _operations.update { ops -> ops.map { if (it.id == entry.queued.id) terminal else it }.trim() }
        pending.remove(entry.queued.id)
        entry.result.complete(terminal)
        _completions.tryEmit(terminal)
    }

    private suspend fun guardPauseIfNeeded(command: ConnectorCommand) {
        if (command == ConnectorCommand.Pause || command == ConnectorCommand.Resume) return
        if (syncSettings.pausedConnectors.value().contains(connectorId)) {
            log(TAG, WARN) { "guard($connectorId): paused, rejecting $command" }
            throw ConnectorPausedException(connectorId)
        }
    }

    private fun List<ConnectorOperation>.trim(): List<ConnectorOperation> {
        val terminals = this.filterIsInstance<ConnectorOperation.Terminal>()
        if (terminals.size <= displayRetention) return this
        val kept = terminals.sortedBy { it.finishedAt }.takeLast(displayRetention).toSet()
        return this.filter { it !is ConnectorOperation.Terminal || it in kept }
    }

    companion object {
        private val TAG = logTag("Sync", "Connector", "Processor")
    }
}
