package eu.darken.octi.common.debug.recording.core

sealed interface DebugSession {
    val session: LogSession
    val size: Long
    val lastModified: Long

    data class Recording(
        override val session: LogSession,
        override val size: Long,
        override val lastModified: Long,
        val startedAt: Long,
    ) : DebugSession

    data class Compressing(
        override val session: LogSession,
        override val size: Long,
        override val lastModified: Long,
    ) : DebugSession

    data class Ready(
        override val session: LogSession,
        override val size: Long,
        override val lastModified: Long,
    ) : DebugSession

    data class Failed(
        override val session: LogSession,
        override val size: Long,
        override val lastModified: Long,
    ) : DebugSession
}
