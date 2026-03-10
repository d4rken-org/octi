package eu.darken.octi.common.debug.recording.core

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.compression.Zipper
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugLogZipper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val zipper = Zipper()

    fun zipAndGetUri(session: LogSession): Uri {
        log(TAG) { "zipAndGetUri(${session.name})" }
        zipper.zipDirectory(session.sessionDir, session.zipFile)
        return getUriForZip(session)
    }

    fun getUriForZip(session: LogSession): Uri {
        log(TAG) { "getUriForZip(${session.name})" }
        return FileProvider.getUriForFile(
            context,
            "${BuildConfigWrap.APPLICATION_ID}.provider",
            session.zipFile,
        )
    }

    companion object {
        private val TAG = logTag("Debug", "Log", "Zipper")
    }
}
