package eu.darken.octi.modules.files.core

import eu.darken.octi.sync.core.DeviceId

@JvmInline
value class FileKey(val raw: String) {
    companion object {
        fun of(deviceId: DeviceId, blobKey: String) = FileKey("${deviceId.id}:$blobKey")
    }
}
