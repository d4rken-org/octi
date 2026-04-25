package eu.darken.octi.common.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File

class WidgetSettingsTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private fun createSettings(scope: TestScope): WidgetSettings {
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir, "widget_settings.preferences_pb") },
        )
        return WidgetSettings(store, json)
    }

    private fun bundleWith(
        mode: String? = null,
        preset: String? = null,
        bg: Int? = null,
        accent: Int? = null,
        deviceIds: Array<String>? = null,
    ): Bundle = mockk<Bundle>(relaxed = true).apply {
        every { getString(WidgetInstanceConfig.KEY_THEME_MODE) } returns mode
        every { getString(WidgetInstanceConfig.KEY_THEME_PRESET) } returns preset
        every { containsKey(WidgetInstanceConfig.KEY_CUSTOM_BG) } returns (bg != null)
        every { getInt(WidgetInstanceConfig.KEY_CUSTOM_BG) } returns (bg ?: 0)
        every { containsKey(WidgetInstanceConfig.KEY_CUSTOM_ACCENT) } returns (accent != null)
        every { getInt(WidgetInstanceConfig.KEY_CUSTOM_ACCENT) } returns (accent ?: 0)
        every { getStringArray(WidgetInstanceConfig.KEY_DEVICE_FILTER_IDS) } returns deviceIds
    }

    @Test
    fun `migration parses legacy bundle and persists when key absent`() = runTest {
        val settings = createSettings(this)
        var lambdaCalls = 0
        val legacy = bundleWith(
            mode = WidgetInstanceConfig.MODE_CUSTOM,
            preset = "DARK",
            bg = -123,
            accent = -456,
            deviceIds = arrayOf("dev-a", "dev-b"),
        )

        val result = settings.configValue(42) {
            lambdaCalls++
            legacy
        }

        lambdaCalls shouldBe 1
        result shouldBe WidgetInstanceConfig(
            isMaterialYou = false,
            presetName = "DARK",
            customBg = -123,
            customAccent = -456,
            allowedDeviceIds = setOf("dev-a", "dev-b"),
        )

        // Marker is set: the next read must NOT invoke the lambda.
        val second = settings.configValue(42) {
            lambdaCalls++
            error("must not be called")
        }
        lambdaCalls shouldBe 1
        second shouldBe result
    }

    @Test
    fun `update overwrites migrated config`() = runTest {
        val settings = createSettings(this)
        settings.update(
            widgetId = 7,
            config = WidgetInstanceConfig(
                isMaterialYou = false,
                presetName = "BLUE",
                customBg = 1,
                customAccent = 2,
                allowedDeviceIds = setOf("x"),
            ),
        )

        val read = settings.configValue(7) { error("should not migrate when key already present") }
        read.presetName shouldBe "BLUE"
        read.customBg shouldBe 1
        read.allowedDeviceIds shouldBe setOf("x")
    }

    @Test
    fun `update normalizes Material You and clears custom colors`() = runTest {
        val settings = createSettings(this)
        settings.update(
            widgetId = 5,
            config = WidgetInstanceConfig(
                isMaterialYou = true,
                presetName = "DARK",
                customBg = -111,
                customAccent = -222,
                allowedDeviceIds = setOf("foo"),
            ),
        )

        val read = settings.config(5).first()
        read.isMaterialYou shouldBe true
        read.presetName shouldBe WidgetTheme.MATERIAL_YOU.name
        read.customBg shouldBe null
        read.customAccent shouldBe null
        read.allowedDeviceIds shouldBe setOf("foo")
    }

    @Test
    fun `update is no-op for INVALID_APPWIDGET_ID`() = runTest {
        val settings = createSettings(this)
        settings.update(
            widgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
            config = WidgetInstanceConfig.DEFAULT.copy(allowedDeviceIds = setOf("a")),
        )

        settings.configValue(AppWidgetManager.INVALID_APPWIDGET_ID) {
            error("must not invoke legacy lambda for INVALID id")
        } shouldBe WidgetInstanceConfig.DEFAULT
    }

    @Test
    fun `configValue returns DEFAULT for INVALID_APPWIDGET_ID without invoking lambda`() = runTest {
        val settings = createSettings(this)
        var called = false
        val result = settings.configValue(AppWidgetManager.INVALID_APPWIDGET_ID) {
            called = true
            bundleWith()
        }
        called shouldBe false
        result shouldBe WidgetInstanceConfig.DEFAULT
    }

    @Test
    fun `delete removes only specified widget ids`() = runTest {
        val settings = createSettings(this)
        settings.update(1, WidgetInstanceConfig.DEFAULT.copy(allowedDeviceIds = setOf("a")))
        settings.update(2, WidgetInstanceConfig.DEFAULT.copy(allowedDeviceIds = setOf("b")))
        settings.update(3, WidgetInstanceConfig.DEFAULT.copy(allowedDeviceIds = setOf("c")))

        settings.delete(intArrayOf(1, 3))

        // Widget 2 is intact.
        settings.configValue(2) { error("should not migrate") }.allowedDeviceIds shouldBe setOf("b")

        // Widgets 1 and 3 — key absent → migration would run with the legacy lambda.
        var migrationsTriggered = 0
        settings.configValue(1) {
            migrationsTriggered++
            bundleWith()
        }
        settings.configValue(3) {
            migrationsTriggered++
            bundleWith()
        }
        migrationsTriggered shouldBe 2
    }

    @Test
    fun `delete is no-op for empty array`() = runTest {
        val settings = createSettings(this)
        settings.update(1, WidgetInstanceConfig.DEFAULT.copy(allowedDeviceIds = setOf("a")))

        settings.delete(intArrayOf())

        settings.configValue(1) { error("should not migrate") }.allowedDeviceIds shouldBe setOf("a")
    }

    @Test
    fun `corrupt blob falls back to DEFAULT`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir, "widget_settings.preferences_pb") },
        )
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("widget_config_99")] = "not valid json"
            }.toPreferences()
        }
        val settings = WidgetSettings(store, json)

        settings.configValue(99) {
            error("should not migrate when key is present")
        } shouldBe WidgetInstanceConfig.DEFAULT
        settings.config(99).first() shouldBe WidgetInstanceConfig.DEFAULT
    }

    @Test
    fun `migration on already-migrated widget does not invoke lambda`() = runTest {
        val settings = createSettings(this)
        settings.update(
            widgetId = 17,
            config = WidgetInstanceConfig.DEFAULT.copy(allowedDeviceIds = setOf("user-set")),
        )

        val read = settings.configValue(17) {
            error("must not call legacy lambda when key already exists")
        }
        read.allowedDeviceIds shouldBe setOf("user-set")
    }
}
