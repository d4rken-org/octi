package eu.darken.octi.modules.files.core

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.DeviceId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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

    private val remoteDevice = DeviceId("remote-device")

    @BeforeEach
    fun setup() {
        every { context.packageName } returns "eu.darken.octi.test"
        every { context.packageManager } returns packageManager
        every { packageManager.getLaunchIntentForPackage(any()) } returns android.content.Intent()
        every { fileShareSettings.notifyOnIncoming } returns notifyOnIncomingValue
        every { moduleManager.byDevice } returns flowOf(ModuleManager.ByDevice(devices = emptyMap()))
    }

    private fun makeStateWith(files: List<FileShareInfo.SharedFile>): BaseModuleRepo.State<FileShareInfo> =
        BaseModuleRepo.State(
            moduleId = FileShareModule.MODULE_ID,
            others = listOf(
                ModuleData(
                    modifiedAt = Clock.System.now(),
                    deviceId = remoteDevice,
                    moduleId = FileShareModule.MODULE_ID,
                    data = FileShareInfo(files = files),
                )
            ),
        )

    private fun makeFile(blobKey: String, name: String = "file.txt") = FileShareInfo.SharedFile(
        name = name,
        mimeType = "text/plain",
        size = 100,
        blobKey = blobKey,
        checksum = "ck",
        sharedAt = Clock.System.now(),
        expiresAt = Clock.System.now() + 1.hours,
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
}
