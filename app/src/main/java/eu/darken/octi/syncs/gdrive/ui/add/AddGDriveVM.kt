package eu.darken.octi.syncs.gdrive.ui.add

import androidx.activity.result.ActivityResult
import androidx.lifecycle.SavedStateHandle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.syncs.gdrive.core.GoogleAccount
import eu.darken.octi.syncs.gdrive.core.GoogleAccountRepo
import javax.inject.Inject

@HiltViewModel
class AddGDriveVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val accRepo: GoogleAccountRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    val events = SingleLiveEvent<AddGDriveEvents>()

    fun startSignIn() {
        log(TAG) { "startSignIn()" }
        events.postValue(AddGDriveEvents.SignInStart(accRepo.startNewAuth()))
    }

    fun onGoogleSignIn(result: ActivityResult) = launch {
        log(TAG) { "onGoogleSignIn(result=$result)" }

        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
        accRepo.add(GoogleAccount(account))
        navEvents.postValue(null)
    }

    companion object {
        private val TAG = logTag("Sync", "Add", "Fragment", "VM")
    }
}