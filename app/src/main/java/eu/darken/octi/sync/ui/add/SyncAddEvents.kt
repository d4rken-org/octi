package eu.darken.octi.sync.ui.add

import android.content.Intent

sealed class SyncAddEvents {
    data class SignInStart(val intent: Intent) : SyncAddEvents()
}
