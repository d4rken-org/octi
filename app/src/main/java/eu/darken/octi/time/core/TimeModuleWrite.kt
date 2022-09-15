package eu.darken.octi.time.core

import eu.darken.octi.sync.core.Sync
import eu.darken.octi.sync.core.SyncModuleId
import okio.ByteString

data class TimeModuleWrite(
    override val payload: ByteString
) : Sync.Write.Module {
    override val moduleId: SyncModuleId
        get() = TimeRepo.MODULE_ID
}