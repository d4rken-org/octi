package eu.darken.octi.sync.core

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class ConnectorId(@Json(name = "id") val id: String) : Parcelable