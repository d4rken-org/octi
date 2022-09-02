package eu.darken.octi.main.ui.dashboard

import androidx.activity.result.ActivityResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.main.ui.dashboard.items.WelcomeVH
import eu.darken.octi.sync.core.SyncRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class DashboardVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val syncRepo: SyncRepo
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    data class State(
        val items: List<DashboardAdapter.Item>,
    )

    val listItems: LiveData<State> = combine(
        syncRepo.syncData,
        syncRepo.syncData
    ) { syncData, _ ->
        val items = mutableListOf<DashboardAdapter.Item>()

        WelcomeVH.Item(
            onDismiss = {
                log(TAG) { "WelcomeVH: onDismiss()" }
            }
        ).run { items.add(this) }

        State(
            items = items
        )
    }.asLiveData2()

    fun onGoogleSignIn(result: ActivityResult) {
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account: GoogleSignInAccount? = try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            e.statusCode
            null
        }

        account!!
    }

    fun goToSyncServices() = launch {
        log(TAG) { "goToSyncServices()" }
        if (syncRepo.connectors.first().isEmpty()) {
            log(TAG) { "Connectors are empty, going to add screen directly." }
            DashboardFragmentDirections.actionDashFragmentToSyncAddFragment().navigate()
        } else {
            DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate()
        }
    }

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        syncRepo.syncAll()
    }

    companion object {
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}