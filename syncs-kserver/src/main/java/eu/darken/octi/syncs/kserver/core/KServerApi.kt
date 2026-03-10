package eu.darken.octi.syncs.kserver.core

import eu.darken.octi.sync.core.DeviceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface KServerApi {

    @Serializable
    data class RegisterResponse(
        @SerialName("account") val accountID: String,
        @SerialName("password") val password: String,
    )

    @POST("account")
    suspend fun register(
        @Header("X-Device-ID") deviceID: String,
        @Query("share") shareCode: String? = null,
    ): RegisterResponse

    @DELETE("account")
    suspend fun delete(
        @Header("X-Device-ID") deviceID: String,
    )

    @Serializable
    data class ShareCodeResponse(
        @SerialName("code") val shareCode: String,
    )

    @POST("account/share")
    suspend fun createShareCode(
        @Header("X-Device-ID") deviceID: String,
    ): ShareCodeResponse

    @Serializable
    data class DevicesResponse(
        @SerialName("devices") val devices: List<Device>,
    ) {
        @Serializable
        data class Device(
            @SerialName("id") val id: String,
            @SerialName("version") val version: String?,
        )
    }

    @GET("devices")
    suspend fun getDeviceList(
        @Header("X-Device-ID") deviceID: String,
    ): DevicesResponse

    @Serializable
    data class ResetRequest(
        @SerialName("targets") val targets: Set<DeviceId>,
    )

    @POST("devices/reset")
    suspend fun resetDevices(
        @Header("X-Device-ID") deviceId: String,
        @Body targets: ResetRequest,
    )

    @DELETE("devices/{deviceId}")
    suspend fun deleteDevice(
        @Header("X-Device-ID") callerDeviceId: String,
        @Path("deviceId") target: String,
    )

    @GET("module/{moduleId}")
    suspend fun readModule(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
    ): Response<ResponseBody>

    @POST("module/{moduleId}")
    suspend fun writeModule(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") deviceId: String,
        @Query("device-id") targetDeviceId: String,
        @Body payload: RequestBody,
    )

}
