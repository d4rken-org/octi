package eu.darken.octi.sync.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOptions @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val deviceIdFile = File(context.filesDir, "identifier_device")
    val deviceId by lazy {
        DeviceId(deviceIdFile.getOrCreateUUID().toString())
    }

    private fun File.getOrCreateUUID(): UUID {
        val existing = takeIf { exists() }?.let { readText() }?.let { UUID.fromString(it) }

        return existing ?: UUID.randomUUID().also {
            log(TAG) { "New ID created: $it" }
            writeText(it.toString())
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Options")
    }
}
