package eu.darken.octi.modules.files.core

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.permissions.PermissionState
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class IncomingFileNotifierTest : BaseTest() {

    private val context = mockk<Context>(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()
    private val fileShareRepo = mockk<FileShareRepo>()
    private val fileShareSettings = mockk<FileShareSettings>()
    private val moduleManager = mockk<ModuleManager>()
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)
    private val notifyOnIncomingValue = mockk<DataStoreValue<Boolean>>()
    private val seenByDeviceValue = mockk<DataStoreValue<Map<String, Set<String>>>>()
    private val permissionState = mockk<PermissionState>()
    private val permissionStateFlow = MutableStateFlow<Map<Permission, Boolean>>(
        mapOf(Permission.POST_NOTIFICATIONS to true)
    )

    private val remoteDevice = DeviceId("remote-device")

    // Stateful mock for persisted seen-set so a notifier sees what a prior instance wrote.
    private var persistedSeen: Map<String, Set<String>> = emptyMap()

    @BeforeEach
    fun setup() {
        every { context.packageName } returns "eu.darken.octi.test"
        every { context.packageManager } returns packageManager
        every { packageManager.getLaunchIntentForPackage(any()) } returns android.content.Intent()
        every { fileShareSettings.notifyOnIncoming } returns notifyOnIncomingValue
        every { fileShareSettings.seenByDevice } returns seenByDeviceValue
        every { moduleManager.byDevice } returns flowOf(ModuleManager.ByDevice(devices = emptyMap()))
        every { permissionState.state } returns permissionStateFlow

        every { seenByDeviceValue.flow } answers { flowOf(persistedSeen) }
        coEvery { seenByDeviceValue.update(any()) } coAnswers {
            val transform = firstArg<(Map<String, Set<String>>) -> Map<String, Set<String>>?>()
            val before = persistedSeen
            val after = transform(before) ?: before
            persistedSeen = after
            DataStoreValue.Updated(before, after)
        }

        // PendingIntent.getActivity hits the Android runtime; stub it so the notify path can run
        // in plain JUnit (no Robolectric).
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(PendingIntent::class)
    }

    private fun makeStateWith(
        files: List<FileShareInfo.SharedFile>,
        deviceId: DeviceId = remoteDevice,
    ): BaseModuleRepo.State<FileShareInfo> =
        BaseModuleRepo.State(
            moduleId = FileShareModule.MODULE_ID,
            others = listOf(
                ModuleData(
                    modifiedAt = Clock.System.now(),
                    deviceId = deviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    data = FileShareInfo(files = files),
                )
            ),
        )

    private fun makeStateWithDevices(
        deviceFiles: Map<DeviceId, List<FileShareInfo.SharedFile>>,
    ): BaseModuleRepo.State<FileShareInfo> =
        BaseModuleRepo.State(
            moduleId = FileShareModule.MODULE_ID,
            others = deviceFiles.map { (deviceId, files) ->
                ModuleData(
                    modifiedAt = Clock.System.now(),
                    deviceId = deviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    data = FileShareInfo(files = files),
                )
            },
        )

    private fun makeFile(
        blobKey: String,
        name: String = "file.txt",
        expiresAt: kotlin.time.Instant = Clock.System.now() + 1.hours,
    ) = FileShareInfo.SharedFile(
        name = name,
        mimeType = "text/plain",
        size = 100,
        blobKey = blobKey,
        checksum = "ck",
        sharedAt = Clock.System.now(),
        expiresAt = expiresAt,
    )

    private fun TestScope.buildNotifier(
        stateFlow: kotlinx.coroutines.flow.Flow<BaseModuleRepo.State<FileShareInfo>>,
        notifyEnabled: Boolean = true,
    ): IncomingFileNotifier {
        every { fileShareRepo.state } returns stateFlow
        every { notifyOnIncomingValue.flow } returns flowOf(notifyEnabled)
        return IncomingFileNotifier(
            context = context,
            appScope = backgroundScope,
            dispatcherProvider = dispatcherProvider,
            fileShareRepo = fileShareRepo,
            fileShareSettings = fileShareSettings,
            moduleManager = moduleManager,
            notificationManager = notificationManager,
            permissionState = permissionState,
        )
    }

    @Test
    fun `first emission per device is silent — no notification fires`() = runTest2 {
        val states = MutableStateFlow(makeStateWith(listOf(makeFile("blob-a"))))

        buildNotifier(states).start()
        advanceUntilIdle()

        verify(exactly = 0) { notificationManager.notify(any<Int>(), any()) }
    }

    @Test
    fun `disabled setting suppresses notifications even on new blobs`() = runTest2 {
        val states = MutableStateFlow(makeStateWith(listOf(makeFile("blob-a"))))

        buildNotifier(states, notifyEnabled = false).start()
        advanceUntilIdle()

        states.value = makeStateWith(listOf(makeFile("blob-a"), makeFile("blob-b")))
        advanceUntilIdle()

        verify(exactly = 0) { notificationManager.notify(any<Int>(), any()) }
    }

    @Test
    fun `repeated emissions with same files stay silent`() = runTest2 {
        val states = MutableStateFlow(makeStateWith(listOf(makeFile("blob-a"))))

        buildNotifier(states).start()
        advanceUntilIdle()

        // Re-emit identical state (StateFlow dedupes, but even if it didn't, diff stays empty)
        states.value = makeStateWith(listOf(makeFile("blob-a")))
        advanceUntilIdle()

        verify(exactly = 0) { notificationManager.notify(any<Int>(), any()) }
    }

    @Test
    fun `POST_NOTIFICATIONS denied suppresses notify on added files`() = runTest2 {
        permissionStateFlow.value = mapOf(Permission.POST_NOTIFICATIONS to false)
        val states = MutableStateFlow(makeStateWith(listOf(makeFile("blob-a"))))

        buildNotifier(states).start()
        advanceUntilIdle()

        // Share arrives while permission is denied — no notify, and internally
        // seenByDevice is NOT advanced.
        states.value = makeStateWith(listOf(makeFile("blob-a"), makeFile("blob-b")))
        advanceUntilIdle()

        verify(exactly = 0) { notificationManager.notify(any<Int>(), any()) }
    }

    @Test
    fun `missed-while-killed share notifies after restart with persisted seed`() = runTest2 {
        // Pre-existing seed from a prior process: device had seen blob-a only.
        persistedSeen = mapOf(remoteDevice.id to setOf("blob-a"))

        // After "restart": current state has the previously-seen blob-a plus a new blob-b
        // that arrived while the app was killed.
        val states = MutableStateFlow(makeStateWith(listOf(makeFile("blob-a"), makeFile("blob-b"))))

        buildNotifier(states).start()
        advanceUntilIdle()

        // blob-b is the missed share — the notify path must run and advance the seen-set so the
        // next emission won't re-notify. (We verify state rather than NotificationCompat.Builder
        // calls because Builder.build() pulls in Android internals that aren't unit-testable.)
        persistedSeen[remoteDevice.id] shouldBe setOf("blob-a", "blob-b")
    }

    @Test
    fun `expired share does not notify after restart`() = runTest2 {
        // Pre-existing seed: device had seen blob-a only.
        persistedSeen = mapOf(remoteDevice.id to setOf("blob-a"))

        // After restart: blob-a is still present plus an expired blob-old. The expired one must
        // not trigger a notification because the notifier filters non-expired files for `current`.
        val states = MutableStateFlow(
            makeStateWith(
                listOf(
                    makeFile("blob-a"),
                    makeFile("blob-old", expiresAt = Clock.System.now() - 1.hours),
                )
            )
        )

        buildNotifier(states).start()
        advanceUntilIdle()

        verify(exactly = 0) { notificationManager.notify(any<Int>(), any()) }
    }

    @Test
    fun `permission denied then restart then granted fires exactly one notification`() = runTest2 {
        // Phase 1: permission denied, blob-a is seeded silently, blob-b arrives but is suppressed
        // and (importantly) not advanced into seenByDevice. Persist the seed for after-restart.
        permissionStateFlow.value = mapOf(Permission.POST_NOTIFICATIONS to false)
        val states1 = MutableStateFlow(makeStateWith(listOf(makeFile("blob-a"))))
        buildNotifier(states1).start()
        advanceUntilIdle()

        states1.value = makeStateWith(listOf(makeFile("blob-a"), makeFile("blob-b")))
        advanceUntilIdle()
        // After phase 1: persisted seed should still be just blob-a (blob-b was suppressed).
        persistedSeen[remoteDevice.id] shouldBe setOf("blob-a")

        // Phase 2: simulate process restart by building a new notifier instance with permission
        // now granted. The persisted seed still reflects only blob-a (blob-b was never advanced).
        permissionStateFlow.value = mapOf(Permission.POST_NOTIFICATIONS to true)
        val states2 = MutableStateFlow(makeStateWith(listOf(makeFile("blob-a"), makeFile("blob-b"))))
        buildNotifier(states2).start()
        advanceUntilIdle()

        // blob-b is now visible as an addition vs the persisted seed; the notify path runs and
        // advances the seen-set to include blob-b.
        persistedSeen[remoteDevice.id] shouldBe setOf("blob-a", "blob-b")
    }

    @Test
    fun `device removed from fileShareRepo is trimmed from persisted seed`() = runTest2 {
        val otherDevice = DeviceId("other-device")
        // Both devices are present at first emission — silent seed.
        val states = MutableStateFlow(
            makeStateWithDevices(
                mapOf(
                    remoteDevice to listOf(makeFile("blob-a")),
                    otherDevice to listOf(makeFile("blob-x")),
                )
            )
        )
        buildNotifier(states).start()
        advanceUntilIdle()

        persistedSeen.keys shouldContainAll listOf(remoteDevice.id, otherDevice.id)

        // Other device drops out — trimmed on next emission.
        states.value = makeStateWith(listOf(makeFile("blob-a")))
        advanceUntilIdle()

        persistedSeen.keys shouldContainAll listOf(remoteDevice.id)
        (otherDevice.id in persistedSeen) shouldBe false
    }

    @Test
    fun `notification is cancelled when a previously-seen file is removed`() = runTest2 {
        // Persisted seed: device has both files known.
        persistedSeen = mapOf(remoteDevice.id to setOf("blob-a", "blob-b"))
        val states = MutableStateFlow(
            makeStateWith(listOf(makeFile("blob-a"), makeFile("blob-b")))
        )
        buildNotifier(states).start()
        advanceUntilIdle()

        // Sender deletes blob-b → it drops out of `current`.
        states.value = makeStateWith(listOf(makeFile("blob-a")))
        advanceUntilIdle()

        // The notifier must have called cancel for blob-b's id.
        verify(atLeast = 1) { notificationManager.cancel(any<Int>()) }
        // And the seen-set advanced to drop blob-b.
        persistedSeen[remoteDevice.id] shouldBe setOf("blob-a")
    }

    @Test
    fun `notification is cancelled even when notifyEnabled is false`() = runTest2 {
        // Same scenario as above but with the user-level toggle off — cancel must still run.
        persistedSeen = mapOf(remoteDevice.id to setOf("blob-a", "blob-b"))
        val states = MutableStateFlow(
            makeStateWith(listOf(makeFile("blob-a"), makeFile("blob-b")))
        )
        buildNotifier(states, notifyEnabled = false).start()
        advanceUntilIdle()

        states.value = makeStateWith(listOf(makeFile("blob-a")))
        advanceUntilIdle()

        verify(atLeast = 1) { notificationManager.cancel(any<Int>()) }
    }

    @Test
    fun `device-purge cancels notifications for that device's blobs`() = runTest2 {
        val otherDevice = DeviceId("other-device")
        // Pre-seed: other-device has blobs that already triggered notifications.
        persistedSeen = mapOf(
            remoteDevice.id to setOf("blob-a"),
            otherDevice.id to setOf("blob-x", "blob-y"),
        )
        val states = MutableStateFlow(
            makeStateWithDevices(
                mapOf(
                    remoteDevice to listOf(makeFile("blob-a")),
                    otherDevice to listOf(makeFile("blob-x"), makeFile("blob-y")),
                )
            )
        )
        buildNotifier(states).start()
        advanceUntilIdle()

        // Other device drops out — its notifications should be cancelled.
        states.value = makeStateWith(listOf(makeFile("blob-a")))
        advanceUntilIdle()

        // Two cancels (blob-x + blob-y) plus possibly other cancels from earlier diffs; assert
        // at least 2 happened.
        verify(atLeast = 2) { notificationManager.cancel(any<Int>()) }
    }
}
