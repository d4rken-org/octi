package eu.darken.octi.modules.clipboard.ui.widget

import androidx.annotation.Keep
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.widget.WidgetSettings
import eu.darken.octi.modules.clipboard.ClipboardHandler
import eu.darken.octi.modules.clipboard.ClipboardRepo
import eu.darken.octi.modules.meta.core.MetaRepo

@Keep
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClipboardWidgetEntryPoint {
    fun metaRepo(): MetaRepo
    fun clipboardRepo(): ClipboardRepo
    fun clipboardHandler(): ClipboardHandler
    fun upgradeRepo(): UpgradeRepo
    fun widgetSettings(): WidgetSettings
}
