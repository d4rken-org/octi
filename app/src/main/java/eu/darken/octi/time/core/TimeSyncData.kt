package eu.darken.octi.time.core

import eu.darken.octi.sync.core.ModuleId
import eu.darken.octi.sync.core.Sync
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.time.OffsetDateTime

data class TimeSyncData(
    val deviceTime: OffsetDateTime
) {

    fun toSyncWrite(): Sync.Module {
        return object : Sync.Module {
            override val moduleId: ModuleId = TimeSync.MODULE_ID
            override val payload: ByteString = deviceTime.toString().toByteArray().toByteString()
            override fun toString(): String = this@TimeSyncData.toString()
        }
    }

    companion object {
        fun from(syncRead: Sync.Read.Module): TimeSyncData {
            if (syncRead.moduleId != TimeSync.MODULE_ID) {
                throw IllegalArgumentException("Wrong moduleId: ${syncRead.moduleId}\n$syncRead")
            }
            return TimeSyncData(
                deviceTime = OffsetDateTime.parse(syncRead.payload.utf8())
            )
        }
    }

}