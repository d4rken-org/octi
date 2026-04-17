package eu.darken.octi.syncs.gdrive.ui.add

import android.accounts.Account
import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.syncs.gdrive.core.GoogleAccountRepo
import javax.inject.Inject

@HiltViewModel
class AddGDriveVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val accRepo: GoogleAccountRepo,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    val events = SingleEventFlow<AddGDriveEvents>()

    fun startSignIn() {
        log(TAG) { "startSignIn()" }
        events.tryEmit(AddGDriveEvents.ShowAccountPicker)
    }

    fun onAccountPicked(email: String) = launch {
        log(TAG) { "onAccountPicked(email=$email)" }

        if (accRepo.isAccountConnected(email)) {
            log(TAG) { "onAccountPicked(): Account already connected" }
            events.tryEmit(AddGDriveEvents.AccountAlreadyConnected(email))
            return@launch
        }

        when (val action = accRepo.startNewAuth(Account(email, "com.google"))) {
            is GoogleAccountRepo.AuthAction.AlreadyAuthorized -> {
                val added = accRepo.add(action.account)
                if (added) {
                    popTo(Nav.Sync.List)
                } else {
                    events.tryEmit(AddGDriveEvents.AccountAlreadyConnected(email))
                }
            }

            is GoogleAccountRepo.AuthAction.NeedsConsent -> {
                events.tryEmit(AddGDriveEvents.AuthConsent(action.pendingIntent, email))
            }
        }
    }

    fun onAuthResult(result: ActivityResult, expectedEmail: String?) = launch {
        log(TAG) { "onAuthResult(resultCode=${result.resultCode})" }
        if (result.resultCode != Activity.RESULT_OK) {
            log(TAG) { "onAuthResult(): User cancelled" }
            return@launch
        }
        val data = result.data
        if (data == null) {
            log(TAG) { "onAuthResult(): No data in result" }
            return@launch
        }
        val account = accRepo.handleAuthResult(data, expectedEmail = expectedEmail)
        val added = accRepo.add(account)
        if (added) {
            popTo(Nav.Sync.List)
        } else {
            events.tryEmit(AddGDriveEvents.AccountAlreadyConnected(account.email))
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Add", "GDrive", "VM")
    }
}
