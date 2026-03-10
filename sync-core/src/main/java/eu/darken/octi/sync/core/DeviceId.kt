package eu.darken.octi.sync.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@Parcelize
data class DeviceId(@SerialName("id") val id: String = UUID.randomUUID().toString()) : Parcelable
