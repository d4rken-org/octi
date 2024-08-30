package eu.darken.octi.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

inline fun <reified T> moshiReader(
    moshi: Moshi,
    defaultValue: T,
): (Any?) -> T {
    val adapter = moshi.adapter(T::class.java)
    return { rawValue ->
        rawValue as String?
        rawValue?.let { adapter.fromJson(it) } ?: defaultValue
    }
}

inline fun <reified T> moshiWriter(
    moshi: Moshi,
): (T) -> Any? {
    val adapter = moshi.adapter(T::class.java)
    return { newValue: T ->
        newValue?.let { adapter.toJson(it) }
    }
}

inline fun <reified T : Any?> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T = null as T,
    moshi: Moshi,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = moshiReader(moshi, defaultValue),
    writer = moshiWriter(moshi),
)


inline fun <reified T> moshiSetReader(
    moshi: Moshi,
    defaultValue: Set<T> = emptySet(),
): (Any?) -> Set<T> {
    val type = Types.newParameterizedType(Set::class.java, T::class.java)
    val adapter = moshi.adapter<Set<T>>(type)
    return { rawValue ->
        rawValue as String?
        rawValue?.let { adapter.fromJson(it) } ?: defaultValue
    }
}

inline fun <reified T> moshiSetWriter(
    moshi: Moshi,
): (Set<T>?) -> Any? {
    val type = Types.newParameterizedType(Set::class.java, T::class.java)
    val adapter = moshi.adapter<Set<T>>(type)
    return { newValue: Set<T>? ->
        newValue.let { adapter.toJson(it) }
    }
}

inline fun <reified T : Any> DataStore<Preferences>.createSetValue(
    key: String,
    defaultValue: Set<T> = emptySet(),
    moshi: Moshi,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = moshiSetReader(moshi, defaultValue),
    writer = moshiSetWriter(moshi),
)


inline fun <reified T> moshiListReader(
    moshi: Moshi,
    defaultValue: List<T>,
): (Any?) -> List<T> {
    val type = Types.newParameterizedType(List::class.java, T::class.java)
    val adapter = moshi.adapter<List<T>>(type)
    return { rawValue ->
        rawValue as String?
        rawValue?.let { adapter.fromJson(it) } ?: defaultValue
    }
}

inline fun <reified T> moshiListWriter(
    moshi: Moshi,
): (List<T>) -> Any? {
    val type = Types.newParameterizedType(List::class.java, T::class.java)
    val adapter = moshi.adapter<List<T>>(type)
    return { newValue: List<T> ->
        newValue.let { adapter.toJson(it) }
    }
}

inline fun <reified T : Any> DataStore<Preferences>.createListValue(
    key: String,
    defaultValue: List<T> = emptyList(),
    moshi: Moshi,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = moshiListReader(moshi, defaultValue),
    writer = moshiListWriter(moshi),
)