package eu.darken.octi.syncs.gdrive.ui.add

import android.content.Intent

sealed class AddGDriveEvents {
    data class SignInStart(val intent: Intent) : AddGDriveEvents()
}
