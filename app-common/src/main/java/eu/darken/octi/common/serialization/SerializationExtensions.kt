package eu.darken.octi.common.serialization

import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.toByteString

inline fun <reified T> Json.fromJson(json: String): T = decodeFromString(json)

inline fun <reified T> Json.toJson(value: T): String = encodeToString(value)

inline fun <reified T> Json.fromJson(raw: ByteString): T = decodeFromString(raw.utf8())

inline fun <reified T> Json.toByteString(value: T): ByteString =
    encodeToString(value).encodeToByteArray().toByteString()
