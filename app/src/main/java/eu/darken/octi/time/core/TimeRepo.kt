package eu.darken.octi.time.core

import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.sync.core.SyncModuleId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeRepo @Inject constructor() {

    companion object {
        val MODULE_ID = SyncModuleId("${BuildConfigWrap.APPLICATION_ID}.module.core.time")
    }
}