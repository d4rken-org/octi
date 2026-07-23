package eu.darken.octi.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.core.OurSku
import eu.darken.octi.common.upgrade.core.UpgradeRepoGplay
import eu.darken.octi.common.upgrade.core.billing.ItemAlreadyOwnedBillingException
import eu.darken.octi.common.upgrade.core.billing.Sku
import eu.darken.octi.common.upgrade.core.billing.SkuDetails
import eu.darken.octi.common.widget.WidgetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoGplay,
    private val widgetManagers: Set<@JvmSuppressWildcards WidgetManager>,
    private val webpageTool: WebpageTool,
    private val clock: Clock,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    val events = SingleEventFlow<UpgradeEvents>()

    private val widgetsRefreshedForUpgrade = AtomicBoolean(false)

    private fun nowMs(): Long = clock.now().toEpochMilliseconds()

    // ONE authoritative admission guard for all mutually-exclusive billing actions. A single CAS
    // from null closes the TOCTOU race that two independent "check the other flag, then set mine"
    // flags have when the coroutines run on different threads (Dispatchers.Default).
    private enum class Operation { RESTORE, PURCHASE }

    private val activeOperation = MutableStateFlow<Operation?>(null)

    // null until the host reports whether this is the manage route. The auto-close collector waits
    // for it, so a manage visit can never race a premature navUp().
    private val manageRoute = MutableStateFlow<Boolean?>(null)
    private val autoClose = MutableStateFlow<Boolean?>(null)

    // Free user on the manage route: reveal the offers only when asked. Persisted so a process
    // recreation while looking at the offers doesn't bounce back to the status page.
    private val viewingOffers = MutableStateFlow(handle.get<Boolean>(KEY_SHOW_OFFERS) ?: false)

    fun initialize(forced: Boolean, manage: Boolean) {
        if (manageRoute.value != null) return
        log(TAG) { "initialize(forced=$forced, manage=$manage)" }
        manageRoute.value = manage
        // Sales route auto-closes on first Pro; a forced or manage visit stays open on purpose.
        autoClose.value = !forced && !manage
    }

    // Purchase actions stay disabled until the first billing reconciliation of this process (or a
    // bounded fallback so a Play outage can't brick the buttons): the initially-empty purchase state
    // must not let an owner on a fresh install double-buy.
    private val settled: StateFlow<Boolean> = merge(
        upgradeRepo.isSettled.filter { it },
        flow {
            delay(SETTLE_FALLBACK_MS)
            emit(true)
        },
    ).stateIn(vmScope, SharingStarted.Eagerly, false)

    // One aggregate SKU-detail query per ViewModel lifetime, both types concurrently. Failures
    // resolve to null details — owners/grace render price-independently, acquisition users get the
    // fallback purchase UI.
    private val skuQueries = flow {
        emit(SkuQueryState())
        val result = coroutineScope {
            val iap = async { querySkuDetailsSafe(OurSku.Iap.PRO_UPGRADE) }
            val sub = async { querySkuDetailsSafe(OurSku.Sub.PRO_UPGRADE) }
            SkuQueryState(done = true, iap = iap.await(), sub = sub.await())
        }
        emit(result)
    }.shareIn(vmScope, SharingStarted.Lazily, replay = 1)

    private suspend fun querySkuDetailsSafe(sku: Sku): SkuDetails? = try {
        withTimeoutOrNull(SKU_QUERY_TIMEOUT_MS) {
            upgradeRepo.querySkus(sku).firstOrNull()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "Failed to query SKU ${sku.id}: ${e.asLog()}" }
        null
    }

    // Re-evaluates the grace presentation when the open episode crosses the diagnostics threshold —
    // every other combined flow is distinct-until-changed and would never re-fire.
    private val graceTick = upgradeRepo.proUnconfirmedSince
        .flatMapLatest { stamp ->
            flow {
                emit(Unit)
                if (stamp > 0L) {
                    val remaining = stamp + GRACE_DIAGNOSTICS_AFTER_MS - nowMs()
                    if (remaining > 0) {
                        delay(remaining)
                        emit(Unit)
                    }
                }
            }
        }

    private data class BillingState(
        val info: UpgradeRepoGplay.Info?,
        val wasEverPro: Boolean,
        val proUnconfirmedSince: Long,
    )

    private val billingState = combine(
        upgradeRepo.upgradeInfo,
        upgradeRepo.wasEverPro,
        upgradeRepo.proUnconfirmedSince,
        graceTick,
    ) { info, wasEverPro, unconfirmedSince, _ ->
        BillingState(
            info = info,
            wasEverPro = wasEverPro,
            proUnconfirmedSince = unconfirmedSince,
        )
    }

    private data class UiInputs(
        val settled: Boolean,
        val restoring: Boolean,
        val busy: Boolean,
        val manage: Boolean,
        val viewingOffers: Boolean,
    )

    private val uiInputs = combine(
        settled,
        activeOperation,
        upgradeRepo.autoRestoreInProgress,
        manageRoute.filterNotNull(),
        viewingOffers,
    ) { isSettled, operation, autoRestoring, manage, offers ->
        UiInputs(
            settled = isSettled,
            restoring = operation == Operation.RESTORE,
            // The invisible already-owned reconciliation runs off the VM's guard — treat it as busy
            // so the buy buttons stay disabled while it resolves.
            busy = operation == Operation.PURCHASE || autoRestoring,
            manage = manage,
            viewingOffers = offers,
        )
    }

    val state: StateFlow<UpgradeUiState> = combine(
        billingState,
        skuQueries,
        uiInputs,
    ) { billing, skus, inputs ->
        val info = billing.info
        val ownership = info?.toOwnership() ?: Ownership()
        val grace = if (info?.isPro == true && !ownership.ownsAnything) {
            GraceHint(
                showDiagnostics = billing.proUnconfirmedSince > 0L &&
                    nowMs() - billing.proUnconfirmedSince >= GRACE_DIAGNOSTICS_AFTER_MS,
            )
        } else {
            null
        }

        // Owners and grace users render price-independently: their status view must not degrade to a
        // spinner (or an error) just because the SKU pricing queries failed or are slow.
        val priceIndependent = ownership.ownsAnything || grace != null
        if (!priceIndependent && !skus.done) {
            UpgradeUiState.Loading
        } else {
            toLoadedState(
                skus = skus,
                ownership = ownership,
                grace = grace,
                // Hidden while a grace period or an actual purchase keeps the user Pro.
                showRestoreBanner = billing.wasEverPro && info?.isPro != true,
                settled = inputs.settled,
                restoreInProgress = inputs.restoring,
                verificationInProgress = inputs.busy,
                manageMode = inputs.manage,
                viewingOffers = inputs.viewingOffers,
            )
        }
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5_000), UpgradeUiState.Loading)

    init {
        // Sales route: close once the user is Pro (purchase completed, or they were Pro all along).
        // Manage/forced route: never auto-close — it exists to LOOK at the status.
        autoClose
            .filterNotNull()
            .flatMapLatest { shouldClose ->
                if (shouldClose) upgradeRepo.upgradeInfo else emptyFlow()
            }
            .filter { it.isPro }
            .take(1)
            .onEach {
                log(TAG) { "User is pro on the sales route, navigating back" }
                refreshWidgetsForUpgrade()
                navUp()
            }
            .launchInViewModel()
    }

    fun onSeeUpgradeOptions() {
        log(TAG) { "onSeeUpgradeOptions()" }
        handle[KEY_SHOW_OFFERS] = true
        viewingOffers.value = true
    }

    fun onGoIap(activity: Activity) {
        log(TAG, INFO) { "onGoIap()" }
        launch {
            runExclusive {
                // ALWAYS verify against a fresh SUBS-only query, not just for known subscribers: the
                // replayed ownership state can be stale or still empty right after process start, and
                // a renewing subscriber must never double-buy. Fails closed.
                val subscriptions = try {
                    withTimeoutOrNull(VERIFY_TIMEOUT_MS) { upgradeRepo.queryCurrentSubscriptions() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Subscription verification failed: ${e.asLog()}" }
                    errorEvents.emit(e)
                    return@runExclusive
                }
                when {
                    subscriptions == null -> {
                        log(TAG, WARN) { "Subscription verification timed out" }
                        events.tryEmit(UpgradeEvents.SubscriptionCheckFailed)
                    }

                    subscriptions.any { it.isAutoRenewing } -> {
                        log(TAG, INFO) { "Subscription still set to renew -> blocking IAP purchase" }
                        events.tryEmit(UpgradeEvents.SubscriptionStillRenewing)
                    }

                    else -> launchBillingFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
                }
            }
        }
    }

    fun onGoSubscription(activity: Activity) {
        log(TAG, INFO) { "onGoSubscription()" }
        launch {
            runExclusive { launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.BASE_OFFER) }
        }
    }

    fun onGoSubscriptionTrial(activity: Activity) {
        log(TAG, INFO) { "onGoSubscriptionTrial()" }
        launch {
            runExclusive { launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER) }
        }
    }

    // Single-flight for purchase actions: the guard is held from the tap until the Play sheet launch
    // has resolved, so repeated taps can't stack verification queries or billing flows, and a
    // restore can't overlap either. Atomic single CAS from null — no check-then-set race.
    private suspend fun runExclusive(block: suspend () -> Unit) {
        if (!activeOperation.compareAndSet(expect = null, update = Operation.PURCHASE)) {
            log(TAG) { "Purchase action ignored, another operation is in flight" }
            return
        }
        try {
            block()
        } finally {
            activeOperation.value = null
        }
    }

    private suspend fun launchBillingFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        try {
            upgradeRepo.launchBillingFlowNow(activity, sku, offer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ItemAlreadyOwnedBillingException) {
            // Play returned "already owned" as the immediate launch result (not an async event, so
            // the purchaseFailures auto-restore never sees it). Reconcile by restoring now, and only
            // surface the error if the EXACT launched SKU still doesn't show up — a grace-only isPro
            // or a different owned SKU doesn't explain this failure.
            log(TAG, INFO) { "Already owned on launch, reconciling ${sku.id}" }
            val restored = try {
                withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
            } catch (c: CancellationException) {
                throw c
            } catch (re: Exception) {
                log(TAG, WARN) { "Reconcile restore failed: ${re.asLog()}" }
                null
            }
            if (restored?.upgrades?.any { it.sku.id == sku.id } == true) {
                log(TAG, INFO) { "Reconciled already-owned ${sku.id}" }
                refreshWidgetsForUpgrade()
                events.tryEmit(UpgradeEvents.RestoreSucceeded)
            } else {
                errorEvents.emit(e)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "launchBillingFlow(${sku.id}) failed: ${e.asLog()}" }
            errorEvents.emit(e)
        }
    }

    fun restorePurchase() = launch {
        // Same authoritative guard as purchases: a single CAS from null admits at most one operation,
        // so a restore can't overlap an in-flight verification/launch and vice versa (no stacked
        // result dialogs), and repeated restore taps collapse to one.
        if (!activeOperation.compareAndSet(expect = null, update = Operation.RESTORE)) {
            log(TAG) { "restorePurchase() ignored, another operation is in flight" }
            return@launch
        }
        log(TAG, INFO) { "restorePurchase()" }

        try {
            // Pad the round-trip to a minimum visible duration, CONCURRENTLY with the real query (a
            // pad, not an add-on): warm caches can answer instantly, and a spinner that flashes for a
            // single frame leaves the user unsure whether anything actually happened.
            val restored = coroutineScope {
                val minVisible = async { delay(RESTORE_MIN_VISIBLE_MS) }
                val result = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
                minVisible.await()
                result
            }
            when {
                restored == null -> {
                    // Play never answered in time; the restore-failed message already suggests
                    // waiting / clearing the Play cache, which fits a timeout too.
                    log(TAG, WARN) { "Restore purchase timed out" }
                    events.tryEmit(UpgradeEvents.RestoreFailed)
                }

                // An actual returned purchase is required — a grace-only isPro means Play still
                // couldn't confirm anything, which is not a successful restore.
                restored.upgrades.isNotEmpty() -> {
                    log(TAG, INFO) { "Restored purchase :))" }
                    refreshWidgetsForUpgrade()
                    events.tryEmit(UpgradeEvents.RestoreSucceeded)
                }

                else -> {
                    log(TAG, WARN) { "Restore purchase found no purchase" }
                    events.tryEmit(UpgradeEvents.RestoreFailed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play/billing error (e.g. service unavailable): surface the proper error dialog instead
            // of the generic "restore failed" message, so the user can tell the two cases apart.
            log(TAG, WARN) { "Restore purchase errored: ${e.asLog()}" }
            errorEvents.emit(e)
        } finally {
            activeOperation.value = null
        }
    }

    fun onManageSubscription() {
        log(TAG, INFO) { "onManageSubscription()" }
        webpageTool.open(PLAY_SUBSCRIPTION_URL)
    }

    fun onContactSupport() {
        log(TAG, INFO) { "onContactSupport()" }
        navTo(Nav.Settings.ContactSupport)
    }

    fun onResume() {
        // Returning from Play (e.g. after cancelling renewal on the Manage page) must reflect the new
        // renewal state promptly — a disabled switch button can't re-run the gate itself, so refresh
        // the SUBS state here to heal the ownership UI and unlock the switch.
        val current = state.value
        val hasSub = (current as? UpgradeUiState.Loaded)?.ownership?.subscription != null
        if (!hasSub) return
        launch {
            try {
                withTimeoutOrNull(VERIFY_TIMEOUT_MS) { upgradeRepo.queryCurrentSubscriptions() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Resume subscription refresh failed: ${e.asLog()}" }
            }
        }
    }

    private suspend fun refreshWidgetsForUpgrade() {
        if (!widgetsRefreshedForUpgrade.compareAndSet(false, true)) return

        log(TAG) { "refreshWidgetsForUpgrade()" }
        for (manager in widgetManagers) {
            try {
                manager.refreshWidgets()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to refresh widgets after upgrade: ${e.asLog()}" }
            }
        }
    }

    companion object {
        internal const val RESTORE_TIMEOUT_MS = 15_000L
        // Long enough that the user believes a round-trip to Play happened, short enough not to drag.
        internal const val RESTORE_MIN_VISIBLE_MS = 1_500L
        internal const val VERIFY_TIMEOUT_MS = 10_000L
        // The first SKU query after a Play sign-in has been observed to take >8s.
        internal const val SKU_QUERY_TIMEOUT_MS = 15_000L
        internal const val SETTLE_FALLBACK_MS = 10_000L
        internal val GRACE_DIAGNOSTICS_AFTER_MS = 24.hours.inWholeMilliseconds
        internal val PLAY_SUBSCRIPTION_URL =
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${OurSku.Sub.PRO_UPGRADE.id}&package=${BuildConfigWrap.APPLICATION_ID}"
        private const val KEY_SHOW_OFFERS = "upgrade.manage.showOffers"
        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}
