package eu.darken.octi.modules.power.core.alert

import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.modules.power.PowerModule
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerRepo
import eu.darken.octi.modules.power.core.PowerSettings
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PowerAlertManagerTest : BaseTest() {

    private val powerRepo = mockk<PowerRepo>()
    private val powerSettings = mockk<PowerSettings>()
    private val notifications = mockk<PowerAlertNotifications>(relaxed = true)
    private val metaRepo = mockk<MetaRepo>()

    private val remoteDevice = DeviceId("remote")
    private val selfDevice = DeviceId("self")

    @Test
    fun `active alert is notified only once despite repeated power updates`() = runTest2 {
        val rule = BatteryHighAlertRule(deviceId = remoteDevice, threshold = 0.85f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(
            setOf(PowerAlertRule.Event(id = rule.id, triggeredAt = Instant.parse("2026-05-02T12:31:07.195Z")))
        )
        val powerStates = MutableStateFlow(
            powerState(remote = powerInfo(status = PowerInfo.Status.CHARGING, level = 94, currentNow = -536190))
        )

        setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        powerStates.value = powerState(
            remote = powerInfo(status = PowerInfo.Status.CHARGING, level = 94, currentNow = -100000),
            self = powerInfo(status = PowerInfo.Status.CHARGING, level = 80, currentNow = 196875),
        )
        advanceTimeBy(1_100)
        powerStates.value = powerState(
            remote = powerInfo(status = PowerInfo.Status.CHARGING, level = 95, currentNow = -100000),
            self = powerInfo(status = PowerInfo.Status.CHARGING, level = 80, currentNow = 25781),
        )
        advanceTimeBy(1_100)
        advanceUntilIdle()

        coVerify(exactly = 1) { notifications.show(rule, any(), any()) }
    }

    @Test
    fun `recovered alert can notify again after retriggering`() = runTest2 {
        val rule = BatteryHighAlertRule(deviceId = remoteDevice, threshold = 0.85f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(emptySet<PowerAlertRule.Event>())
        val powerStates = MutableStateFlow(
            powerState(remote = powerInfo(status = PowerInfo.Status.CHARGING, level = 94))
        )

        setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        powerStates.value = powerState(remote = powerInfo(status = PowerInfo.Status.DISCHARGING, level = 79))
        advanceTimeBy(1_100)
        advanceUntilIdle()

        powerStates.value = powerState(remote = powerInfo(status = PowerInfo.Status.CHARGING, level = 94))
        advanceTimeBy(1_100)
        advanceUntilIdle()

        coVerify(exactly = 2) { notifications.show(rule, any(), any()) }
        coVerify(exactly = 1) { notifications.dismiss(rule) }
    }

    @Test
    fun `stale peer data does not raise an alert and clears an active event`() = runTest2 {
        val rule = BatteryLowAlertRule(deviceId = remoteDevice, threshold = 0.3f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(setOf(PowerAlertRule.Event(id = rule.id)))
        val powerStates = MutableStateFlow(
            powerState(
                remote = powerInfo(status = PowerInfo.Status.DISCHARGING, level = 4),
                remoteModifiedAt = Clock.System.now() - 25.hours,
            )
        )

        setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        coVerify(exactly = 0) { notifications.show(any(), any(), any()) }
        coVerify { notifications.dismiss(rule) }
        events.flow.value shouldBe emptySet()
    }

    @Test
    fun `stale peer data does not re-notify a dismissed alert`() = runTest2 {
        val rule = BatteryLowAlertRule(deviceId = remoteDevice, threshold = 0.3f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(
            setOf(
                PowerAlertRule.Event(
                    id = rule.id,
                    dismissedAt = Clock.System.now(),
                    notifiedAt = Clock.System.now(),
                )
            )
        )
        // Stale data that also looks "recovered" (charging): the freshness gate must handle it, not the
        // recovery branch, which would delete the event and let a later low reading re-trigger a fresh one.
        val powerStates = MutableStateFlow(
            powerState(
                remote = powerInfo(status = PowerInfo.Status.CHARGING, level = 50),
                remoteModifiedAt = Clock.System.now() - 25.hours,
            )
        )

        setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        coVerify(exactly = 0) { notifications.show(any(), any(), any()) }
        events.flow.value shouldBe emptySet()
    }

    @Test
    fun `already-notified alert is not re-shown on a fresh evaluation`() = runTest2 {
        // Simulates a process restart: the event was already delivered (notifiedAt set) and the peer is
        // still low with fresh data. The persisted notifiedAt must prevent a re-post.
        val rule = BatteryLowAlertRule(deviceId = remoteDevice, threshold = 0.3f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(setOf(PowerAlertRule.Event(id = rule.id, notifiedAt = Clock.System.now())))
        val powerStates = MutableStateFlow(
            powerState(remote = powerInfo(status = PowerInfo.Status.DISCHARGING, level = 20))
        )

        setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        coVerify(exactly = 0) { notifications.show(any(), any(), any()) }
    }

    @Test
    fun `fresh low battery triggers once and stamps notifiedAt`() = runTest2 {
        val rule = BatteryLowAlertRule(deviceId = remoteDevice, threshold = 0.3f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(emptySet<PowerAlertRule.Event>())
        val powerStates = MutableStateFlow(
            powerState(remote = powerInfo(status = PowerInfo.Status.DISCHARGING, level = 20))
        )

        setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        coVerify(exactly = 1) { notifications.show(rule, any(), any()) }
        events.flow.value.single().notifiedAt shouldNotBe null
    }

    @Test
    fun `disabling an alert clears its event and dismisses the notification`() = runTest2 {
        val rule = BatteryLowAlertRule(deviceId = remoteDevice, threshold = 0.3f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(setOf(PowerAlertRule.Event(id = rule.id, notifiedAt = Clock.System.now())))
        // No power data for the peer -> processAlerts leaves the event untouched, isolating the setter.
        val powerStates = MutableStateFlow(powerState(remote = null))

        val manager = setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        manager.setBatteryLowAlert(remoteDevice, null)
        advanceUntilIdle()

        events.flow.value shouldBe emptySet()
        rules.flow.value shouldBe emptySet()
        coVerify { notifications.dismiss(any()) }
    }

    @Test
    fun `lowering the threshold below the current level dismisses the stale notification`() = runTest2 {
        val rule = BatteryLowAlertRule(deviceId = remoteDevice, threshold = 0.3f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(setOf(PowerAlertRule.Event(id = rule.id, notifiedAt = Clock.System.now())))
        val powerStates = MutableStateFlow(
            powerState(remote = powerInfo(status = PowerInfo.Status.DISCHARGING, level = 20))
        )

        val manager = setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        // Battery is 20%; lowering the alert to 10% means it no longer triggers, so the previously shown
        // notification must be cancelled rather than left visible.
        manager.setBatteryLowAlert(remoteDevice, 0.1f)
        advanceUntilIdle()

        coVerify(exactly = 1) { notifications.dismiss(any()) }
        coVerify(exactly = 0) { notifications.show(any(), any(), any()) }
        events.flow.value shouldBe emptySet()
    }

    @Test
    fun `stale charging data clears a high battery alert without notifying`() = runTest2 {
        val rule = BatteryHighAlertRule(deviceId = remoteDevice, threshold = 0.85f)
        val rules = statefulValue(setOf<PowerAlertRule>(rule))
        val events = statefulValue(setOf(PowerAlertRule.Event(id = rule.id)))
        val powerStates = MutableStateFlow(
            powerState(
                remote = powerInfo(status = PowerInfo.Status.CHARGING, level = 95),
                remoteModifiedAt = Clock.System.now() - 25.hours,
            )
        )

        setupManager(rules, events, powerStates)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        coVerify(exactly = 0) { notifications.show(any(), any(), any()) }
        coVerify { notifications.dismiss(rule) }
        events.flow.value shouldBe emptySet()
    }

    private fun kotlinx.coroutines.test.TestScope.setupManager(
        rules: StatefulValue<Set<PowerAlertRule>>,
        events: StatefulValue<Set<PowerAlertRule.Event>>,
        powerStates: MutableStateFlow<BaseModuleRepo.State<PowerInfo>>,
    ): PowerAlertManager {
        every { powerRepo.state } returns powerStates
        every { metaRepo.state } returns MutableStateFlow(
            BaseModuleRepo.State(
                moduleId = MetaModule.MODULE_ID,
                others = listOf(metaData(remoteDevice)),
            )
        )
        every { powerSettings.alertRules } returns rules.value
        every { powerSettings.alertEvents } returns events.value
        coEvery { notifications.show(any(), any(), any()) } just runs
        coEvery { notifications.dismiss(any()) } just runs

        return PowerAlertManager(
            appScope = backgroundScope,
            powerRepo = powerRepo,
            powerSettings = powerSettings,
            notifications = notifications,
            metaRepo = metaRepo,
        )
    }

    private fun powerState(
        remote: PowerInfo?,
        self: PowerInfo = powerInfo(status = PowerInfo.Status.CHARGING, level = 80),
        remoteModifiedAt: Instant = Clock.System.now(),
    ): BaseModuleRepo.State<PowerInfo> = BaseModuleRepo.State(
        moduleId = PowerModule.MODULE_ID,
        self = ModuleData(
            modifiedAt = Clock.System.now(),
            deviceId = selfDevice,
            moduleId = PowerModule.MODULE_ID,
            data = self,
        ),
        others = listOfNotNull(
            remote?.let {
                ModuleData(
                    modifiedAt = remoteModifiedAt,
                    deviceId = remoteDevice,
                    moduleId = PowerModule.MODULE_ID,
                    data = it,
                )
            }
        ),
    )

    private fun powerInfo(
        status: PowerInfo.Status,
        level: Int,
        scale: Int = 100,
        currentNow: Int? = null,
    ) = PowerInfo(
        status = status,
        battery = PowerInfo.Battery(
            level = level,
            scale = scale,
            health = 2,
            temp = 33.5f,
        ),
        chargeIO = PowerInfo.ChargeIO(
            currentNow = currentNow,
            currenAvg = null,
            fullSince = null,
            fullAt = null,
            emptyAt = null,
        ),
    )

    private fun metaData(deviceId: DeviceId) = ModuleData(
        modifiedAt = Clock.System.now(),
        deviceId = deviceId,
        moduleId = MetaModule.MODULE_ID,
        data = MetaInfo(
            deviceLabel = "Pixel 1",
            deviceId = deviceId,
            octiVersionName = "1.0.0-beta1",
            octiGitSha = "test",
            deviceManufacturer = "Google",
            deviceName = "Pixel XL",
            deviceType = MetaInfo.DeviceType.PHONE,
            deviceBootedAt = Clock.System.now(),
            androidVersionName = "10",
            androidApiLevel = 29,
            androidSecurityPatch = "2019-10-06",
        ),
    )

    private fun <T> statefulValue(initial: T): StatefulValue<T> = StatefulValue(initial)

    private class StatefulValue<T>(initial: T) {
        val flow = MutableStateFlow(initial)
        val value = mockk<DataStoreValue<T>> {
            every { flow } returns this@StatefulValue.flow
            every { keyName } returns "test"
            coEvery { update(any()) } coAnswers {
                val transform = firstArg<(T) -> T?>()
                val old = this@StatefulValue.flow.value
                val new = transform(old) ?: old
                this@StatefulValue.flow.value = new
                DataStoreValue.Updated(old, new)
            }
        }
    }
}
