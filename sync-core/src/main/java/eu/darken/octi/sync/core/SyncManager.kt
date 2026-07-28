package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.shareIn
import eu.darken.octi.sync.core.cache.SyncCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Singleton
class SyncManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val syncCache: SyncCache,
    private val connectorHubs: Set<@JvmSuppressWildcards ConnectorHub>,
    private val connectorSyncState: ConnectorSyncState,
) {

    /**
     * Guards the whole single-flight transition — {active run, its start mark, the pending request,
     * the generation token} move together or not at all. Held only for those decisions, never
     * across connector work.
     */
    private val stateLock = Mutex()
    private var activeRun: ActiveRun? = null
    private val pendingRequests = mutableListOf<RequestRecord>()
    private var generationCounter = 0L

    /**
     * Monotonic on purpose: a wall-clock jump must not make a run look stale (superseding healthy
     * work) or eternally fresh (never superseding wedged work). Overridable for tests only.
     */
    internal var timeSource: TimeSource = TimeSource.Monotonic

    private val modulePayloads = ConcurrentHashMap<ModuleId, SyncWrite.Device.Module>()

    private val _activeConnectorSyncs = MutableStateFlow(emptySet<ConnectorId>())

    /** Connectors with a sync submitted by this manager that has not reached a terminal state. */
    val activeConnectorSyncs: StateFlow<Set<ConnectorId>> = _activeConnectorSyncs.asStateFlow()

    sealed interface ConnectorSyncOutcome {
        data object Success : ConnectorSyncOutcome
        data class Failure(val error: Throwable) : ConnectorSyncOutcome
    }

    private val _lastConnectorSyncOutcomes = MutableStateFlow(emptyMap<ConnectorId, ConnectorSyncOutcome>())

    /** Result of the most recent sync this manager ran per connector. */
    val lastConnectorSyncOutcomes: StateFlow<Map<ConnectorId, ConnectorSyncOutcome>> =
        _lastConnectorSyncOutcomes.asStateFlow()

    /**
     * A single caller's request. Requests stay separate until a batch actually executes: merging
     * them up front would make one caller's scope indistinguishable from another's, and a caller
     * that gives up could then no longer withdraw exactly what it asked for.
     */
    private class RequestRecord(val options: SyncOptions) {
        val waiter = CompletableDeferred<Unit>()

        /**
         * The run that claimed this record into its batch, or null while it is still pending.
         * Guarded by [stateLock].
         *
         * Ownership has to be read off the record rather than inferred from [activeRun]: a run that
         * was superseded but whose cancellation has not landed yet still holds its batch, while
         * [activeRun] is already its replacement.
         */
        var ownerRun: ActiveRun? = null
    }

    private class ActiveRun(val generation: Long) {
        lateinit var job: Job

        /** Reset per iteration, so a run that keeps doing real work never ages into staleness. */
        var startedAt: TimeMark? = null

        /**
         * The requests this iteration is working for. Non-null while a batch is executing — folded
         * back into pending if the run is cancelled, and emptied when its last owner walks away.
         */
        var currentBatch: MutableList<RequestRecord>? = null

        /** What to cancel when this run is superseded: the actual connector operations. */
        val inFlightOps = ConcurrentHashMap<ConnectorId, Pair<SyncConnector, OperationId>>()
    }

    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val pendingSyncTrigger: Flow<Unit> = syncRequests.debounce(2.seconds)

    private val disabledConnectors = MutableStateFlow(emptySet<SyncConnector>())

    init {
        scope.launch(dispatcherProvider.Default) {
            syncSettings.migrateLegacyPauseStates()
        }
    }

    private val hubs: Flow<Collection<ConnectorHub>> = flow {
        emit(connectorHubs)
        awaitCancellation()
    }
        .setupCommonEventHandlers(TAG) { "syncHubs" }
        .shareLatest(scope + dispatcherProvider.Default)

    /**
     * All connectors, including paused ones. Only consumers that need to surface paused
     * entries (e.g. the sync list screen, by-id lookups for disconnect) should use this.
     */
    val allConnectors: Flow<List<SyncConnector>> = combine(
        hubs.flatMapLatest { hs -> combine(hs.map { it.connectors }) { it.toList().flatten() } },
        disabledConnectors
    ) { connectors, disabledConnectors ->
        connectors.filter { !disabledConnectors.contains(it) }
    }
        .setupCommonEventHandlers(TAG) { "allConnectors" }
        .shareLatest(scope + dispatcherProvider.Default)

    /**
     * Active (non-paused) connectors. This is the default view — new code should use this
     * unless it explicitly needs paused entries too.
     */
    val connectors: Flow<List<SyncConnector>> = allConnectors
        .combine(syncSettings.pausedConnectorIds) { conns, paused ->
            conns.filter { !paused.contains(it.identifier) }
        }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(scope + dispatcherProvider.Default)

    /** States from all connectors, including paused. */
    val allStates: Flow<Collection<SyncConnectorState>> = allConnectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) flowOf(emptyList())
            else combine(hs.map { it.state }) { it.toList() }
        }
        .setupCommonEventHandlers(TAG, logValues = false) { "allStates" }
        .shareLatest(scope + dispatcherProvider.Default)

    /** States from active (non-paused) connectors. Default view. */
    val states: Flow<Collection<SyncConnectorState>> = connectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) flowOf(emptyList())
            else combine(hs.map { it.state }) { it.toList() }
        }
        .setupCommonEventHandlers(TAG, logValues = false) { "states" }
        .shareLatest(scope + dispatcherProvider.Default)

    /**
     * Identifiers of active connectors that currently have at least one queued or processing
     * command — derived from each connector's operations flow. Consumers that previously read
     * `SyncConnectorState.isBusy` should use this instead.
     */
    val busyConnectorIds: Flow<Set<ConnectorId>> = connectors
        .flatMapLatest { cons ->
            if (cons.isEmpty()) flowOf(emptySet())
            else combine(
                cons.map { c -> c.isBusy.map { busy -> c.identifier to busy } }
            ) { pairs ->
                pairs.filter { it.second }.map { it.first }.toSet()
            }
        }
        .setupCommonEventHandlers(TAG, logValues = false) { "busyConnectorIds" }
        .shareLatest(scope + dispatcherProvider.Default)

    val syncEvents: Flow<SyncEvent> = connectors
        .flatMapLatest { cons ->
            if (cons.isEmpty()) emptyFlow()
            else cons.map { it.syncEvents }.merge()
        }
        .setupCommonEventHandlers(TAG) { "syncEvents" }
        .shareIn(scope + dispatcherProvider.Default, SharingStarted.WhileSubscribed(), replay = 0)

    val data: Flow<Collection<SyncRead.Device>> = connectors
        .flatMapLatest { connectorList ->
            if (connectorList.isEmpty()) {
                flowOf(emptyList())
            } else {
                val connectorDataFlows: List<Flow<Pair<ConnectorId, SyncRead?>>> = connectorList.map { con ->
                    con.data.map { syncRead -> con.identifier to syncRead }
                }
                combine(connectorDataFlows) { it.toSet() }
            }
        }
        .map { pairs ->
            pairs.mapNotNull { (id, read) ->
                if (read != null) syncCache.save(id, read)
                read ?: syncCache.load(id)
            }
        }
        .map { it.latestData() }
        .setupCommonEventHandlers(TAG) { "syncData" }
        .shareLatest(scope + dispatcherProvider.Default)

    /**
     * Runs [options] against every active connector, and does not return until that work is done.
     *
     * Single-flight with accumulation: a request arriving while a run is in progress is merged into
     * the run's next iteration instead of being dropped, so no caller's scope is silently absorbed
     * by whichever request happened to hold the lock. If the current run has been working on the
     * same iteration for longer than [SYNC_STALE_AFTER] it is superseded — its connector
     * operations are cancelled and a fresh run takes over carrying every outstanding request.
     *
     * Cancelling the caller withdraws that caller's request. If it was the last one the current
     * iteration was working for, the iteration itself is cancelled — connector operations included.
     * As long as another caller still wants it, the work continues untouched.
     */
    suspend fun sync(options: SyncOptions = SyncOptions()) {
        log(TAG) { "sync(${options.logLabel})" }
        val record = RequestRecord(options)
        stateLock.withLock {
            pendingRequests += record
            val current = activeRun
            val age = current?.startedAt?.elapsedNow()
            when {
                current == null -> startRunLocked()
                age != null && age >= SYNC_STALE_AFTER -> {
                    log(TAG, WARN) { "sync(): run ${current.generation} stuck for $age, superseding" }
                    supersedeLocked(current)
                    startRunLocked()
                }
                else -> log(TAG) { "sync(): folded into run ${current.generation}" }
            }
        }
        try {
            record.waiter.await()
        } catch (e: CancellationException) {
            // Our caller is gone: the worker was stopped, a watchdog timed out, a UI scope died.
            // The run lives on @AppScope, so without this it would keep doing network work for a
            // result nobody reads. NonCancellable because we are already being cancelled.
            withContext(NonCancellable) { abandon(record) }
            throw e
        }
    }

    /**
     * Withdraws [record] on behalf of a cancelled caller, and cancels the run if that leaves the
     * executing iteration with no owner at all.
     */
    private suspend fun abandon(record: RequestRecord) = stateLock.withLock {
        pendingRequests.remove(record)
        // The owner comes from the record, not from activeRun: a superseded run whose cancellation
        // is still in flight keeps its batch, and activeRun already points at the replacement. Going
        // through activeRun would silently fail to find the record and leave it to be folded back in.
        val run = record.ownerRun ?: return@withLock
        val batch = run.currentBatch ?: return@withLock
        if (!batch.remove(record)) return@withLock
        record.ownerRun = null
        if (batch.isNotEmpty()) {
            // Another caller still wants this work. Options already merged into the executing
            // command cannot be subtracted anyway — leave it running.
            log(TAG) { "abandon(): run ${run.generation} still has ${batch.size} owner(s)" }
            return@withLock
        }
        log(TAG, WARN) { "abandon(): last caller of run ${run.generation} left, cancelling its work" }
        // Detach the batch BEFORE cancelling: the run's finally folds a still-attached batch back
        // into pending, which would restart the very work we are abandoning. Applies whether or not
        // this run is still activeRun — a superseded run folds its leftovers back just the same.
        run.currentBatch = null
        supersedeLocked(run)
    }

    /** Callers must hold [stateLock]. */
    private fun startRunLocked() {
        val run = ActiveRun(++generationCounter)
        // LAZY so the run object is fully wired (and published as `activeRun`) before its body can
        // observe it — the body's first act is to take this very lock.
        val job = scope.launch(start = CoroutineStart.LAZY) { runLoop(run) }
        run.job = job
        activeRun = run
        log(TAG) { "startRun(${run.generation})" }
        job.start()
    }

    /**
     * Callers must hold [stateLock]. Cancels the connector operations first: cancelling only our
     * own waiter would leave the command running inside the connector's actor, and the replacement
     * would simply queue behind it.
     *
     * [run] need not be [activeRun]: a run that was already superseded can still be holding a batch
     * and in-flight operations, and both have to go when its last owner walks away. Only this run's
     * own operation ids are cancelled, so a replacement run's work is never touched.
     */
    private fun supersedeLocked(run: ActiveRun) {
        run.inFlightOps.forEach { (connectorId, entry) ->
            val (connector, opId) = entry
            val cancelled = connector.cancel(opId)
            log(TAG, WARN) { "supersede(${run.generation}): cancel ${connectorId.logLabel} -> $cancelled" }
        }
        run.job.cancel(CancellationException("Sync run ${run.generation} superseded"))
    }

    private suspend fun runLoop(run: ActiveRun) {
        try {
            while (true) {
                // Merging happens here and nowhere earlier: one option set per executing batch,
                // computed under the same lock hold that claims the records.
                val options = stateLock.withLock {
                    if (pendingRequests.isEmpty()) return@withLock null
                    val next = pendingRequests.toMutableList()
                    pendingRequests.clear()
                    // Stamp ownership while claiming, so a caller that gives up later can find the
                    // batch holding its record without having to guess which run that is.
                    next.forEach { it.ownerRun = run }
                    run.startedAt = timeSource.markNow()
                    run.currentBatch = next
                    next.map { it.options }.reduce { acc, o -> acc.merge(o) }
                } ?: break

                try {
                    executeBatch(run, options)
                    // Detach and release atomically: a caller that abandoned while this batch ran
                    // is no longer in the list, so it is not "completed" behind its own back.
                    detachOwners(run).forEach { it.waiter.complete(Unit) }
                } catch (e: CancellationException) {
                    // Leave currentBatch set — the finally folds it back into pending so a
                    // superseded batch's requests and waiters are carried by the next run.
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "runLoop(${run.generation}) failed: ${e.asLog()}" }
                    detachOwners(run).forEach { it.waiter.completeExceptionally(e) }
                }
            }
        } finally {
            // The whole exit decision happens under one lock hold, so there is no window between
            // "nothing pending" and "no longer the active run" for a request to fall into.
            withContext(NonCancellable) {
                stateLock.withLock {
                    // Compare before clearing: a superseded run must not clear its replacement.
                    if (activeRun?.generation == run.generation) activeRun = null
                    run.currentBatch?.let { leftover ->
                        run.currentBatch = null
                        log(TAG, WARN) { "runLoop(${run.generation}): folding cancelled batch back in" }
                        // Back to unowned: whichever run claims them next stamps itself, and until
                        // then an abandoning caller must find them in pendingRequests, not here.
                        leftover.forEach { it.ownerRun = null }
                        pendingRequests.addAll(0, leftover)
                    }
                    if (pendingRequests.isNotEmpty() && activeRun == null) startRunLocked()
                }
            }
        }
    }

    private suspend fun detachOwners(run: ActiveRun): List<RequestRecord> = stateLock.withLock {
        val owners = run.currentBatch.orEmpty().toList()
        owners.forEach { it.ownerRun = null }
        run.currentBatch = null
        owners
    }

    private suspend fun executeBatch(run: ActiveRun, options: SyncOptions) {
        // Bounded: an unresolved connector list must not wedge the run (and with it every waiter).
        val targets = withTimeoutOrNull(CONNECTOR_RESOLVE_TIMEOUT) { connectors.first() }
        if (targets == null) {
            log(TAG, WARN) { "executeBatch(): connectors unresolved after $CONNECTOR_RESOLVE_TIMEOUT" }
            return
        }
        // Structured children of the run: cancelling the run actually stops them, which detached
        // scope.launch calls would not.
        supervisorScope {
            targets.map { connector ->
                launch {
                    try {
                        sync(connector.identifier, options, run)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "sync(): ${connector.identifier} failed: ${e.asLog()}" }
                    }
                }
            }.joinAll()
        }
    }

    suspend fun sync(connectorId: ConnectorId, options: SyncOptions = SyncOptions()) =
        sync(connectorId, options, run = null)

    private suspend fun sync(connectorId: ConnectorId, options: SyncOptions, run: ActiveRun?) {
        log(TAG) { "sync(${connectorId.logLabel}, ${options.logLabel})" }
        // Fast-path defense: avoid submitting work we already know the processor will reject.
        // The processor also enforces the pause guard authoritatively.
        if (syncSettings.isPaused(connectorId)) {
            log(TAG, INFO) { "sync(${connectorId.logLabel}): connector is paused, skipping" }
            return
        }
        val connector = allConnectors.first().singleOrNull { it.identifier == connectorId }
        if (connector == null) {
            log(TAG, WARN) { "sync(): Connector $connectorId not found, skipping" }
            return
        }

        val changedModules = if (options.writeData) {
            modulePayloads.values.mapNotNull { module ->
                val currentHash = module.payload.sha256().hex()
                val record = connectorSyncState.getRecord(connectorId, module.moduleId)
                if (record == null || record.hash != currentHash || record.age >= MODULE_REWRITE_HORIZON) {
                    SyncOptions.ModuleWrite(module = module, expectedHash = currentHash)
                } else {
                    null
                }
            }
        } else {
            emptyList()
        }

        val effectiveOptions = if (changedModules.isNotEmpty()) {
            log(TAG) { "sync(): Passing ${changedModules.size} changed modules to $connectorId" }
            options.copy(writePayload = changedModules)
        } else {
            options
        }

        // Connector sets hashes inline for each successfully-written module — no post-call update here.
        // Submit and await separately (rather than execute()) so a supersede has the operation id to
        // cancel; cancelling only the waiter would leave the command running. Exclusive because we
        // may cancel this id: a coalesced one is shared, and cancelling it would fail unrelated
        // callers — including a targeted foreground sync whose filters would be dropped with it.
        val opId = connector.submitExclusive(ConnectorCommand.Sync(effectiveOptions))
        run?.inFlightOps?.put(connectorId, connector to opId)
        _activeConnectorSyncs.update { it + connectorId }
        try {
            when (val terminal = connector.await(opId)) {
                is ConnectorOperation.Succeeded -> {
                    _lastConnectorSyncOutcomes.update { it + (connectorId to ConnectorSyncOutcome.Success) }
                }
                is ConnectorOperation.Failed -> {
                    _lastConnectorSyncOutcomes.update {
                        it + (connectorId to ConnectorSyncOutcome.Failure(terminal.error))
                    }
                    throw terminal.error
                }
            }
        } finally {
            run?.inFlightOps?.remove(connectorId)
            _activeConnectorSyncs.update { it - connectorId }
        }
    }

    fun updatePayload(payload: SyncWrite.Device.Module) {
        log(TAG) { "updatePayload(moduleId=${payload.moduleId})" }
        modulePayloads[payload.moduleId] = payload
    }

    fun requestSync() {
        syncRequests.tryEmit(Unit)
    }

    suspend fun resetData(identifier: ConnectorId) = withContext(NonCancellable) {
        log(TAG) { "resetData(identifier=$identifier)" }
        if (syncSettings.isPaused(identifier)) {
            log(TAG, INFO) { "resetData($identifier): connector is paused, skipping" }
            return@withContext
        }
        getConnectorById<SyncConnector>(identifier).first().execute(ConnectorCommand.Reset)
        syncCache.removeDeviceMetadata(identifier)
        log(TAG) { "resetData(identifier=$identifier) done" }
    }

    suspend fun disconnect(identifier: ConnectorId) = withContext(NonCancellable) {
        log(TAG) { "disconnect(identifier=$identifier)" }

        connectorSyncState.clearConnector(identifier)

        val connector = getConnectorById<SyncConnector>(identifier).first()

        disabledConnectors.value += connector

        if (syncSettings.isPaused(identifier)) {
            log(TAG) { "disconnect(...) was paused, clearing it" }
            syncSettings.resumeConnector(identifier)
        }

        try {
            hubs.first().filter { it.owns(identifier) }.forEach {
                it.remove(identifier)
            }
            syncCache.removeDeviceMetadata(identifier)
        } catch (e: Exception) {
            log(TAG, ERROR) { "disconnect(...) failed: ${e.asLog()}" }
            throw e
        } finally {
            disabledConnectors.value -= connector
        }

        log(TAG) { "disconnect(connector=$connector) done" }
    }

    suspend fun togglePause(identifier: ConnectorId, paused: Boolean? = null) {
        log(TAG, INFO) { "togglePause($identifier, enabled=$paused)" }
        val wasPaused = syncSettings.isPaused(identifier)
        val pause = paused ?: !wasPaused
        if (wasPaused == pause) {
            log(TAG) { "togglePause($identifier): no-op, already in target state" }
            return
        }
        val connector = allConnectors.first().singleOrNull { it.identifier == identifier } ?: run {
            log(TAG, WARN) { "togglePause($identifier): connector not found" }
            return
        }
        // Route through the queue so the setting write is serialized with the connector's other
        // work. After a successful Resume, the processor enqueues a Sync on its own queue — no
        // fire-and-forget launch needed here.
        connector.execute(if (pause) ConnectorCommand.Pause() else ConnectorCommand.Resume)
    }

    companion object {
        private val TAG = logTag("Sync", "Manager")

        /**
         * How long one run iteration may work before a newly arriving request takes over. Above the
         * connector processor's per-command bound on purpose: in practice a wedged command fails
         * itself first, and this is the backstop for anything the processor cannot bound.
         */
        internal val SYNC_STALE_AFTER = 5.minutes
        private val CONNECTOR_RESOLVE_TIMEOUT = 10.seconds

        /**
         * How long an unchanged module payload may go unwritten before it is sent again anyway.
         * Static-payload modules (meta: labels, versions, bootedAt) never change their hash between
         * reboots, so pure hash dedup writes them exactly once per process lifetime and every peer's
         * view of "last written" ages into process uptime. Rewriting at roughly background-sync
         * cadence restores the pre-dedup hourly freshness without giving up per-minute dedup.
         */
        internal val MODULE_REWRITE_HORIZON = 1.hours
    }
}
