package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

interface SyncConnector {

    enum class EventMode { NONE, LIVE, POLLING }

    val identifier: ConnectorId
    val accountLabel: String
    val capabilities: ConnectorCapabilities

    val state: Flow<SyncConnectorState>
    val data: Flow<SyncRead?>

    /** UI-facing lifecycle list. Bounded by retention — never use for completion/error handling. */
    val operations: StateFlow<List<ConnectorOperation>>

    /** Every terminal state emitted exactly once, independent of [operations] retention. */
    val completions: SharedFlow<ConnectorOperation.Terminal>

    val syncEvents: Flow<SyncEvent> get() = emptyFlow()
    val syncEventMode: StateFlow<EventMode> get() = MutableStateFlow(EventMode.NONE)

    /** Non-suspending. Enqueues [command]; observe [operations] / [completions] for lifecycle. */
    fun submit(command: ConnectorCommand): OperationId

    /** Suspends until the op with [id] reaches terminal state. */
    suspend fun await(id: OperationId): ConnectorOperation.Terminal

    /** Remove a terminal entry from [operations]. No effect on pending ops. */
    fun dismiss(id: OperationId)
}

val SyncConnector.isBusy: Flow<Boolean>
    get() = operations.map { ops ->
        ops.any { it is ConnectorOperation.Queued || it is ConnectorOperation.Processing }
    }

suspend fun SyncConnector.submitAndAwait(command: ConnectorCommand): ConnectorOperation.Terminal =
    await(submit(command))

/** Submit + await + throw on failure. Replaces direct suspend calls. */
suspend fun SyncConnector.execute(command: ConnectorCommand) {
    when (val terminal = submitAndAwait(command)) {
        is ConnectorOperation.Failed -> throw terminal.error
        is ConnectorOperation.Succeeded -> Unit
    }
}
