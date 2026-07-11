package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory snooze state for dismissible dashboard guidance cards.
 * Process death clears it — snoozes last for the app session, not forever.
 */
@Singleton
class DashboardCardSnoozer @Inject constructor() {

    private val _snoozedCards = MutableStateFlow(emptySet<Card>())
    val snoozedCards: Flow<Set<Card>> = _snoozedCards

    fun snooze(card: Card) {
        log(TAG) { "snooze($card)" }
        _snoozedCards.value += card
    }

    enum class Card {
        SYNC_SETUP,
        SYNCED_ALONE,
    }

    companion object {
        private val TAG = logTag("Dashboard", "CardSnoozer")
    }
}
