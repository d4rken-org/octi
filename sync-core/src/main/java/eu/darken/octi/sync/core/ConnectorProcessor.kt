package eu.darken.octi.sync.core

import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.errors.ConnectorPausedException
import eu.darken.octi.sync.core.errors.ConnectorStoppedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
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

    // Serializes the submit/shutdown ownership decision: a submit either registers in [pending] before
    // shutdown claims it, or is rejected once [stopped] is set — never both, and never onto a closed
    // inbox with no consumer.
    private val lifecycleLock = Any()
    private var stopped = false

    private val _operations = MutableStateFlow<List<ConnectorOperation>>(emptyList())
    val operations: StateFlow<List<ConnectorOperation>> = _operations.asStateFlow()

    private val _completions = MutableSharedFlow<ConnectorOperation.Terminal>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val completions: SharedFlow<ConnectorOperation.Terminal> = _completions.asSharedFlow()

    // ATOMIC start so the body — and therefore the shutdown `finally` — always runs, even if `scope`
    // is cancelled before the coroutine is first dispatched. With the default start, that pre-dispatch
    // cancellation would skip the body entirely, leaving `stopped` false, the inbox open, and any op
    // that raced in stuck forever (its await() would never resolve). The "delicate" property (the body
    // is guaranteed to begin regardless of cancellation) is exactly what we rely on to run cleanup.
    @OptIn(DelicateCoroutinesApi::class)
    fun start(scope: CoroutineScope): Job = scope.launch(start = CoroutineStart.ATOMIC) {
        log(TAG, VERBOSE) { "processor($connectorId): started" }
        try {
            for (entry in inbox) processOne(entry)
        } finally {
            // Under the lock: mark stopped and close the inbox so any racing submit() is rejected up
            // front (submit registers everything under this same lock, so an op either became fully
            // visible before this snapshot or is rejected after — never a half-registered op), then
            // snapshot the still-pending ops. We deliberately do NOT clear `pending`: leaving the
            // (about-to-be-completed) entries in place lets a late await(id) resolve from its deferred
            // regardless of `operations` retention trimming. The map dies with the processor anyway.
            val snapshot = synchronized(lifecycleLock) {
                stopped = true
                inbox.close()
                pending.values.toList()
            }
            log(TAG, VERBOSE) { "processor($connectorId): stopping, failing ${snapshot.size} pending" }
            val now = Clock.System.now()
            val failures = snapshot.mapNotNull { entry ->
                if (entry.result.isCompleted) return@mapNotNull null
                val failed = ConnectorOperation.Failed(
                    id = entry.queued.id,
                    command = entry.queued.command,
                    submittedAt = entry.queued.submittedAt,
                    startedAt = now,
                    finishedAt = now,
                    error = ConnectorStoppedException(connectorId),
                )
                entry.result.complete(failed)
                failed
            }
            // Move the UI operations to terminal and emit their completions too — otherwise a shutdown
            // leaves Queued/Processing entries in `operations`, keeping `isBusy` true (and gated actions
            // disabled) until the connector is fully reconstructed. `operations` is the authoritative
            // isBusy source (lossless StateFlow); the completions emit is best-effort observational.
            if (failures.isNotEmpty()) {
                val failedById = failures.associateBy { it.id }
                _operations.update { ops -> ops.map { failedById[it.id] ?: it }.trim() }
                failures.forEach { _completions.tryEmit(it) }
            }
        }
    }

    fun submit(command: ConnectorCommand): OperationId {
        val id = OperationId.create()
        val queued = ConnectorOperation.Queued(id, command, Clock.System.now())
        val deferred = CompletableDeferred<ConnectorOperation.Terminal>()
        val entry = Pending(queued, deferred)

        // Register the pending entry, publish the Queued op, and enqueue — all under the lock. The
        // shutdown path runs under the same lock, so it sees an all-or-nothing submit: either the op is
        // fully visible before shutdown snapshots (and gets failed there), or `stopped` is already set
        // and we reject here. Because the inbox is only closed under this lock once `stopped` is true, a
        // trySend while `stopped` is false is guaranteed to land in the UNLIMITED buffer — there is no
        // "enqueued onto a closed inbox" case to recover from.
        val accepted = synchronized(lifecycleLock) {
            if (stopped) {
                false
            } else {
                pending[id] = entry
                _operations.update { (it + queued).trim() }
                check(inbox.trySend(entry).isSuccess) { "inbox closed while not stopped" }
                true
            }
        }
        if (!accepted) {
            // Processor already stopped — fail fast so the caller's await() never hangs. Keep the entry
            // in `pending` (completed) so a late await(id) still resolves from its deferred instead of
            // racing retention trimming of `_operations`. The processor is dead; this map dies with it.
            pending[id] = entry
            failEntry(entry, ConnectorStoppedException(connectorId))
        }
        return id
    }

    // Completes an unaccepted [entry] as Failed and publishes it. Only reachable from the submit-after-
    // shutdown reject path, so the op was never listed in [_operations] — append it.
    private fun failEntry(entry: Pending, error: Throwable) {
        val now = Clock.System.now()
        val failed = ConnectorOperation.Failed(
            id = entry.queued.id,
            command = entry.queued.command,
            submittedAt = entry.queued.submittedAt,
            startedAt = now,
            finishedAt = now,
            error = error,
        )
        entry.result.complete(failed)
        _operations.update { (it + failed).trim() }
        _completions.tryEmit(failed)
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

        // After a successful Resume, enqueue a Sync on our own queue BEFORE publishing Resume's terminal.
        // Completing the Resume deferred can wake a waiter that immediately submits (e.g. Pause) on
        // another thread; enqueuing the Sync first guarantees it lands in the inbox ahead of that
        // command, so the post-resume sync is ordered deterministically (Resume → Sync → …).
        if (terminal is ConnectorOperation.Succeeded && entry.queued.command == ConnectorCommand.Resume) {
            submit(ConnectorCommand.Sync())
        }

        publishTerminal(entry, terminal)
    }

    private fun publishTerminal(entry: Pending, terminal: ConnectorOperation.Terminal) {
        _operations.update { ops -> ops.map { if (it.id == entry.queued.id) terminal else it }.trim() }
        pending.remove(entry.queued.id)
        entry.result.complete(terminal)
        _completions.tryEmit(terminal)
    }

    private suspend fun guardPauseIfNeeded(command: ConnectorCommand) {
        if (command is ConnectorCommand.Pause || command == ConnectorCommand.Resume) return
        if (syncSettings.isPaused(connectorId)) {
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
