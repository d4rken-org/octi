package eu.darken.octi.sync.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class SyncDeviceId(@Json(name = "id") val id: UUID) {
    constructor(id: String) : this(UUID.fromString(id))
}