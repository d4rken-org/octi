package eu.darken.octi.servers.gdrive.ui.add

import android.content.Intent

sealed class AddGDriveEvents {
    data class SignInStart(val intent: Intent) : AddGDriveEvents()
}
