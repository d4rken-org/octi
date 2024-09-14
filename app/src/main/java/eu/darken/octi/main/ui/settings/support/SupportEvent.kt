package eu.darken.octi.main.ui.settings.support

sealed interface SupportEvent {
    data object DebugLogInfo : SupportEvent
}