package eu.darken.octi.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.serialization.json.Json

@PublishedApi
internal val KOTLINX_TAG = logTag("DataStore", "Kotlinx")

inline fun <reified T> kotlinxReader(
    json: Json,
    defaultValue: T,
    onErrorFallbackToDefault: Boolean = false,
): (Any?) -> T {
    return { rawValue ->
        rawValue as String?
        if (rawValue == null) {
            defaultValue
        } else if (onErrorFallbackToDefault) {
            try {
                json.decodeFromString<T>(rawValue) ?: defaultValue
            } catch (e: Exception) {
                log(KOTLINX_TAG, WARN) { "Failed to deserialize ${T::class.simpleName}, falling back to default: ${e.message}" }
                defaultValue
            }
        } else {
            json.decodeFromString<T>(rawValue) ?: defaultValue
        }
    }
}

inline fun <reified T> kotlinxWriter(
    json: Json,
): (T) -> Any? {
    return { newValue: T ->
        newValue?.let { json.encodeToString<T>(it) }
    }
}

inline fun <reified T : Any?> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T = null as T,
    json: Json,
    onErrorFallbackToDefault: Boolean = false,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = kotlinxReader(json, defaultValue, onErrorFallbackToDefault),
    writer = kotlinxWriter(json),
)

inline fun <reified T> kotlinxSetReader(
    json: Json,
    defaultValue: Set<T> = emptySet(),
    onErrorFallbackToDefault: Boolean = false,
): (Any?) -> Set<T> {
    return { rawValue ->
        rawValue as String?
        if (rawValue == null) {
            defaultValue
        } else if (onErrorFallbackToDefault) {
            try {
                json.decodeFromString<Set<T>>(rawValue)
            } catch (e: Exception) {
                log(KOTLINX_TAG, WARN) { "Failed to deserialize Set<${T::class.simpleName}>, falling back to default: ${e.message}" }
                defaultValue
            }
        } else {
            json.decodeFromString<Set<T>>(rawValue)
        }
    }
}

inline fun <reified T> kotlinxSetWriter(
    json: Json,
): (Set<T>?) -> Any? {
    return { newValue: Set<T>? ->
        newValue?.let { json.encodeToString<Set<T>>(it) }
    }
}

inline fun <reified T : Any> DataStore<Preferences>.createSetValue(
    key: String,
    defaultValue: Set<T> = emptySet(),
    json: Json,
    onErrorFallbackToDefault: Boolean = false,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = kotlinxSetReader(json, defaultValue, onErrorFallbackToDefault),
    writer = kotlinxSetWriter(json),
)

inline fun <reified T> kotlinxListReader(
    json: Json,
    defaultValue: List<T>,
    onErrorFallbackToDefault: Boolean = false,
): (Any?) -> List<T> {
    return { rawValue ->
        rawValue as String?
        if (rawValue == null) {
            defaultValue
        } else if (onErrorFallbackToDefault) {
            try {
                json.decodeFromString<List<T>>(rawValue)
            } catch (e: Exception) {
                log(KOTLINX_TAG, WARN) { "Failed to deserialize List<${T::class.simpleName}>, falling back to default: ${e.message}" }
                defaultValue
            }
        } else {
            json.decodeFromString<List<T>>(rawValue)
        }
    }
}

inline fun <reified T> kotlinxListWriter(
    json: Json,
): (List<T>) -> Any? {
    return { newValue: List<T> ->
        json.encodeToString<List<T>>(newValue)
    }
}

inline fun <reified T : Any> DataStore<Preferences>.createListValue(
    key: String,
    defaultValue: List<T> = emptyList(),
    json: Json,
    onErrorFallbackToDefault: Boolean = false,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = kotlinxListReader(json, defaultValue, onErrorFallbackToDefault),
    writer = kotlinxListWriter(json),
)
