package eu.darken.octi.common.debug.recording.core

import java.io.File

data class LogSession(
    val sessionDir: File,
) {
    val coreLogFile: File get() = File(sessionDir, "core.log")
    val zipFile: File get() = File(sessionDir.parentFile, "${sessionDir.name}.zip")
    val name: String get() = sessionDir.name
    val hasZip: Boolean get() = zipFile.exists()
    val files: List<File> get() = sessionDir.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
}
