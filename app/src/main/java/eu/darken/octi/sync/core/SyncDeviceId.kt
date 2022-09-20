package eu.darken.octi.sync.core

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import java.util.*

@JsonClass(generateAdapter = true)
@Parcelize
data class SyncDeviceId(@Json(name = "id") val id: String = UUID.randomUUID().toString()) : Parcelable