package eu.darken.octi.common.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSettings @Inject constructor(
    @WidgetSettingsStore private val store: DataStore<Preferences>,
    private val json: Json,
) {

    private fun configKey(widgetId: Int) = stringPreferencesKey("widget_config_$widgetId")

    private fun decode(blob: String?): WidgetInstanceConfig = if (blob == null) {
        WidgetInstanceConfig.DEFAULT
    } else {
        runCatching {
            json.decodeFromString(WidgetInstanceConfig.serializer(), blob)
        }.getOrElse {
            log(TAG, WARN) { "Failed to decode widget config blob: ${it.asLog()}" }
            WidgetInstanceConfig.DEFAULT
        }
    }

    private fun encode(config: WidgetInstanceConfig): String =
        json.encodeToString(WidgetInstanceConfig.serializer(), config)

    suspend fun configValue(widgetId: Int, legacyOptions: () -> Bundle): WidgetInstanceConfig {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return WidgetInstanceConfig.DEFAULT
        val key = configKey(widgetId)
        val existing = store.data.first()[key]
        if (existing != null) return decode(existing)

        val migrated = WidgetInstanceConfig.parse(legacyOptions())
        val migratedBlob = encode(migrated)
        val winning = store.updateData { prefs ->
            if (prefs[key] != null) {
                prefs
            } else {
                prefs.toMutablePreferences().apply { this[key] = migratedBlob }.toPreferences()
            }
        }
        return decode(winning[key])
    }

    fun config(widgetId: Int): Flow<WidgetInstanceConfig> {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return flowOf(WidgetInstanceConfig.DEFAULT)
        val key = configKey(widgetId)
        return store.data
            .map { prefs -> prefs[key] }
            .distinctUntilChanged()
            .map { blob -> decode(blob) }
    }

    suspend fun update(widgetId: Int, config: WidgetInstanceConfig) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val normalized = if (config.isMaterialYou) {
            config.copy(
                presetName = WidgetTheme.MATERIAL_YOU.name,
                customBg = null,
                customAccent = null,
            )
        } else {
            config
        }
        val blob = encode(normalized)
        val key = configKey(widgetId)
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply { this[key] = blob }.toPreferences()
        }
    }

    suspend fun delete(widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                widgetIds.forEach { id -> this.remove(configKey(id)) }
            }.toPreferences()
        }
    }

    companion object {
        private val TAG = logTag("Widget", "Settings")
    }
}
