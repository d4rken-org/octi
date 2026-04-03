package eu.darken.octi.syncs.gdrive.ui.add

import android.app.PendingIntent

sealed class AddGDriveEvents {
    data class AuthConsent(val pendingIntent: PendingIntent) : AddGDriveEvents()
}
