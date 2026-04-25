package eu.darken.octi.modules.connectivity.ui.widget

import androidx.annotation.Keep
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.widget.WidgetSettings
import eu.darken.octi.modules.connectivity.core.ConnectivityRepo
import eu.darken.octi.modules.meta.core.MetaRepo

@Keep
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkWidgetEntryPoint {
    fun metaRepo(): MetaRepo
    fun connectivityRepo(): ConnectivityRepo
    fun upgradeRepo(): UpgradeRepo
    fun widgetSettings(): WidgetSettings
}
