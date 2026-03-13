package eu.darken.octi.modules.power.ui.widget

import androidx.annotation.Keep
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.modules.power.core.PowerRepo

@Keep
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BatteryWidgetEntryPoint {
    fun metaRepo(): MetaRepo
    fun powerRepo(): PowerRepo
}
