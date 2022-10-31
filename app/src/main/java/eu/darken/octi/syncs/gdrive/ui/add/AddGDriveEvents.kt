package eu.darken.octi.syncs.gdrive.ui.add

import android.content.Intent
import com.google.android.gms.common.api.ApiException

sealed class AddGDriveEvents {
    data class SignInStart(val intent: Intent) : AddGDriveEvents()
    data class NoGoogleAccount(val error: ApiException) : AddGDriveEvents()
}
