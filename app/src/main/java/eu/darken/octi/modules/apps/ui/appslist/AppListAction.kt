package eu.darken.octi.modules.apps.ui.appslist

import android.content.Intent

sealed interface AppListAction {
    data class OpenAppOrStore(
        val intent: Intent,
        val fallback: Intent,
    ) : AppListAction
}