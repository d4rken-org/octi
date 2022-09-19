package eu.darken.octi.syncs.gdrive.core

import android.os.Parcelable
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.parcelize.Parcelize

@Parcelize
data class GoogleAccount(
    val signInAccount: GoogleSignInAccount,
) : Parcelable {

    init {
        if (signInAccount.id == null) throw IllegalArgumentException("Account ID is missing")
        if (signInAccount.email == null) throw IllegalArgumentException("Email is missing")
    }

    val id: Id
        get() = Id(signInAccount.id!!)

    val email: String
        get() = signInAccount.email!!

    val isAppDataScope: Boolean
        get() = signInAccount.grantedScopes.any { it.scopeUri.contains("drive.appdata") }

    override fun toString(): String = "GDriveAcc($email)"

    data class Id(val id: String)
}
