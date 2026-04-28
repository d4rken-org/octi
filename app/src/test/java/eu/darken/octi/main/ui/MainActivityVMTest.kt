package eu.darken.octi.main.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.theming.ThemeColor
import eu.darken.octi.common.theming.ThemeMode
import eu.darken.octi.common.theming.ThemeState
import eu.darken.octi.common.theming.ThemeStyle
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.modules.files.core.FileShareSettings
import eu.darken.octi.modules.files.core.IncomingShareInbox
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class MainActivityVMTest : BaseTest() {

    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()

    private val onboardingDone = mockk<DataStoreValue<Boolean>>().apply {
        every { flow } returns flowOf(true)
    }
    private val themeMode = mockk<DataStoreValue<ThemeMode>>().apply {
        every { flow } returns flowOf(ThemeMode.SYSTEM)
    }
    private val themeStyle = mockk<DataStoreValue<ThemeStyle>>().apply {
        every { flow } returns flowOf(ThemeStyle.DEFAULT)
    }
    private val themeColor = mockk<DataStoreValue<ThemeColor>>().apply {
        every { flow } returns flowOf(ThemeColor.GREEN)
    }
    private val moduleEnabled = mockk<DataStoreValue<Boolean>>().apply {
        every { flow } returns flowOf(true)
    }
    private val generalSettings = mockk<GeneralSettings>().apply {
        every { isOnboardingDone } returns onboardingDone
        every { themeMode } returns this@MainActivityVMTest.themeMode
        every { themeStyle } returns this@MainActivityVMTest.themeStyle
        every { themeColor } returns this@MainActivityVMTest.themeColor
        every { themeState } returns flowOf(ThemeState(ThemeMode.SYSTEM, ThemeStyle.DEFAULT, ThemeColor.GREEN))
    }
    private val fileShareSettings = mockk<FileShareSettings>().apply {
        every { isEnabled } returns moduleEnabled
    }
    private val syncSettings = mockk<SyncSettings>().apply {
        every { deviceId } returns DeviceId("self-device")
    }

    private fun makeVM(inbox: IncomingShareInbox = IncomingShareInbox()) = MainActivityVM(
        dispatcherProvider = dispatcherProvider,
        generalSettings = generalSettings,
        fileShareSettings = fileShareSettings,
        incomingShareInbox = inbox,
        syncSettings = syncSettings,
    )

    private fun fakeUri(scheme: String, path: String): Uri = mockk<Uri>().also {
        every { it.scheme } returns scheme
        every { it.toString() } returns "$scheme://$path"
    }

    @Test
    fun `extractStreamUris pulls single SEND EXTRA_STREAM`() {
        val uri = fakeUri("content", "a")
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri
            every { clipData } returns null
        }
        MainActivityVM.extractStreamUris(intent) shouldContainExactly listOf(uri)
    }

    @Test
    fun `extractStreamUris pulls SEND_MULTIPLE arraylist`() {
        val uriA = fakeUri("content", "a")
        val uriB = fakeUri("content", "b")
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND_MULTIPLE
            every { getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) } returns arrayListOf(uriA, uriB)
            every { clipData } returns null
        }
        MainActivityVM.extractStreamUris(intent) shouldContainExactly listOf(uriA, uriB)
    }

    @Test
    fun `extractStreamUris falls back to clipData when EXTRA_STREAM missing`() {
        val uri = fakeUri("content", "fromClip")
        val item = mockk<ClipData.Item> { every { this@mockk.uri } returns uri }
        val clip = mockk<ClipData> {
            every { itemCount } returns 1
            every { getItemAt(0) } returns item
        }
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns null
            every { clipData } returns clip
        }
        MainActivityVM.extractStreamUris(intent) shouldContainExactly listOf(uri)
    }

    @Test
    fun `extractStreamUris filters non-content-or-file schemes`() {
        val httpsUri = fakeUri("https", "example.com")
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns httpsUri
            every { clipData } returns null
        }
        MainActivityVM.extractStreamUris(intent) shouldBe emptyList()
    }

    @Test
    fun `extractStreamUris dedupes duplicates from EXTRA_STREAM and clipData`() {
        val uri = fakeUri("content", "dup")
        val item = mockk<ClipData.Item> { every { this@mockk.uri } returns uri }
        val clip = mockk<ClipData> {
            every { itemCount } returns 1
            every { getItemAt(0) } returns item
        }
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri
            every { clipData } returns clip
        }
        MainActivityVM.extractStreamUris(intent) shouldContainExactly listOf(uri)
    }

    @Test
    fun `extractStreamUris empty for text-only SEND`() {
        // Browsers' "Share page" is ACTION_SEND with EXTRA_TEXT only — no stream URI anywhere.
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns null
            every { clipData } returns null
        }
        MainActivityVM.extractStreamUris(intent) shouldBe emptyList()
    }

    @Test
    fun `handleDeeplinkIntent emits Unsupported and returns false for empty SEND`() = runTest2 {
        val vm = makeVM()
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns null
            every { clipData } returns null
        }
        vm.handleDeeplinkIntent(intent) shouldBe false
        vm.incomingShareEvents.first() shouldBe MainActivityVM.IncomingShareEvent.Unsupported
    }

    @Test
    fun `handleDeeplinkIntent emits ModuleDisabled without enqueueing when module disabled`() = runTest2 {
        every { moduleEnabled.flow } returns flowOf(false)
        val inbox = spyk(IncomingShareInbox())
        val vm = makeVM(inbox = inbox)
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns fakeUri("content", "a")
            every { clipData } returns null
        }
        vm.handleDeeplinkIntent(intent) shouldBe false
        vm.incomingShareEvents.first() shouldBe MainActivityVM.IncomingShareEvent.ModuleDisabled
        verify(exactly = 0) { inbox.enqueue(any()) }
    }

    @Test
    fun `handleDeeplinkIntent enqueues and emits IncomingShare with self deviceId`() = runTest2 {
        val vm = makeVM()
        val uri = fakeUri("content", "a")
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri
            every { clipData } returns null
        }
        vm.handleDeeplinkIntent(intent) shouldBe true
        val event = vm.incomingShareEvents.first()
        event.shouldBeInstanceOf<MainActivityVM.IncomingShareEvent.IncomingShare>()
        event.selfDeviceId shouldBe "self-device"
    }
}
