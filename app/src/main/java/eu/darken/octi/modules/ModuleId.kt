package eu.darken.octi.modules

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class ModuleId(@Json(name = "id") val id: String) : Parcelable