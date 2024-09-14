package eu.darken.octi.main.ui.dashboard

import android.content.Intent
import eu.darken.octi.common.permissions.Permission

sealed interface DashboardEvent {

    data class ShowPermissionPopup(
        val permission: Permission,
        val onDismiss: (Permission) -> Unit,
        val onGrant: (Permission) -> Unit,
    ) : DashboardEvent

    data class ShowPermissionDismissHint(val permission: Permission) : DashboardEvent
    data class RequestPermissionEvent(val permission: Permission) : DashboardEvent

    data class OpenAppOrStore(
        val intent: Intent,
        val fallback: Intent,
    ) : DashboardEvent

}