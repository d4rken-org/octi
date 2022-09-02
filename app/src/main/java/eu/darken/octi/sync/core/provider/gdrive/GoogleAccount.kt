package eu.darken.octi.sync.core.provider.gdrive

import com.google.android.gms.auth.api.signin.GoogleSignInAccount

data class GoogleAccount(
    val signInAccount: GoogleSignInAccount,
) {

    init {
        if (signInAccount.id == null) throw IllegalArgumentException("Account ID is missing")
        if (signInAccount.email == null) throw IllegalArgumentException("Email is missing")
    }

    val id: Id
        get() = Id(signInAccount.id!!)

    val email: String
        get() = signInAccount.email!!

    override fun toString(): String = "GDriveAcc($email)"

    data class Id(val id: String)
}
