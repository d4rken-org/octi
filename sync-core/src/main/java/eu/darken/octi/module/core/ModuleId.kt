package eu.darken.octi.module.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class ModuleId(@SerialName("id") val id: String) : Parcelable
