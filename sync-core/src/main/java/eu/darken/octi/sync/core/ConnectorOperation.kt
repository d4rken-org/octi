package eu.darken.octi.sync.core

import java.util.UUID
import kotlin.time.Instant

@JvmInline
value class OperationId(val value: String) {
    companion object {
        fun create(): OperationId = OperationId(UUID.randomUUID().toString())
    }
}

sealed interface ConnectorOperation {

    val id: OperationId
    val command: ConnectorCommand
    val submittedAt: Instant

    sealed interface Terminal : ConnectorOperation {
        val startedAt: Instant
        val finishedAt: Instant
    }

    data class Queued(
        override val id: OperationId,
        override val command: ConnectorCommand,
        override val submittedAt: Instant,
    ) : ConnectorOperation

    data class Processing(
        override val id: OperationId,
        override val command: ConnectorCommand,
        override val submittedAt: Instant,
        val startedAt: Instant,
    ) : ConnectorOperation

    data class Succeeded(
        override val id: OperationId,
        override val command: ConnectorCommand,
        override val submittedAt: Instant,
        override val startedAt: Instant,
        override val finishedAt: Instant,
    ) : Terminal

    data class Failed(
        override val id: OperationId,
        override val command: ConnectorCommand,
        override val submittedAt: Instant,
        override val startedAt: Instant,
        override val finishedAt: Instant,
        val error: Throwable,
    ) : Terminal
}
