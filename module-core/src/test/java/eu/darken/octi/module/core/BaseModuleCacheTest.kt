package eu.darken.octi.module.core

import android.content.Context
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.File
import kotlin.time.Instant

class BaseModuleCacheTest : BaseTest() {

    private val context = mockk<Context>()
    private val testModuleId = ModuleId("test.module")
    private val testDeviceId = DeviceId("test-device")

    @TempDir
    lateinit var appCacheDir: File

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val serializer = object : ModuleSerializer<String> {
        override fun serialize(item: String): ByteString = item.encodeUtf8()
        override fun deserialize(raw: ByteString): String = raw.utf8()
    }

    private fun newCache(): TestModuleCache {
        every { context.cacheDir } returns appCacheDir
        return TestModuleCache(
            context = context,
            moduleId = testModuleId,
            dispatcherProvider = TestDispatcherProvider(),
            json = json,
            moduleSerializer = serializer,
        )
    }

    @Test
    fun `set recreates cache dir when deleted between writes`() = runTest2 {
        val cache = newCache()

        val first = ModuleData(
            modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
            deviceId = testDeviceId,
            moduleId = testModuleId,
            data = "payload-one",
        )
        cache.set(testDeviceId, first)
        cache.get(testDeviceId) shouldBe first

        val moduleCacheDir = File(appCacheDir, "module_cache")
        moduleCacheDir.deleteRecursively() shouldBe true
        moduleCacheDir.exists() shouldBe false

        val second = first.copy(data = "payload-two")
        cache.set(testDeviceId, second)

        cache.get(testDeviceId) shouldBe second
    }

    private class TestModuleCache(
        context: Context,
        moduleId: ModuleId,
        dispatcherProvider: DispatcherProvider,
        json: Json,
        moduleSerializer: ModuleSerializer<String>,
    ) : BaseModuleCache<String>(
        context = context,
        moduleId = moduleId,
        tag = "Test:Cache",
        dispatcherProvider = dispatcherProvider,
        json = json,
        moduleSerializer = moduleSerializer,
    )
}
