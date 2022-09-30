package eu.darken.octi.sync.core

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class ConnectorId(
    @Json(name = "type") val type: String,
    @Json(name = "subtype") val subtype: String,
    @Json(name = "account") val account: String,
) : Parcelable {

    val idString: String
        get() = "$type-$subtype-$account"

}