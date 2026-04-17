package eu.darken.octi.syncs.gdrive.ui.add

import android.app.PendingIntent

sealed class AddGDriveEvents {
    data object ShowAccountPicker : AddGDriveEvents()
    data class AuthConsent(val pendingIntent: PendingIntent, val email: String) : AddGDriveEvents()
    data class AccountAlreadyConnected(val email: String) : AddGDriveEvents()
}
