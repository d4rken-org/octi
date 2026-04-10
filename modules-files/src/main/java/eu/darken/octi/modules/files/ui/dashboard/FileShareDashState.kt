package eu.darken.octi.modules.files.ui.dashboard

import eu.darken.octi.modules.files.core.FileShareInfo

data class FileShareDashState(
    val info: FileShareInfo,
    val isOurDevice: Boolean,
)
