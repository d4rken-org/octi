package eu.darken.octi.syrvs.gdrive.core

import com.google.android.gms.auth.api.signin.GoogleSignInClient

data class GoogleClient(
    val account: GoogleAccount,
    val client: GoogleSignInClient
) {
    val id: GoogleAccount.Id
        get() = account.id

    fun signOut() {
        client.revokeAccess()
        client.signOut()
    }
}