package eu.darken.octi.sync.ui.add.gdrive

import android.content.Intent

sealed class AddGDriveEvents {
    data class SignInStart(val intent: Intent) : AddGDriveEvents()
}
