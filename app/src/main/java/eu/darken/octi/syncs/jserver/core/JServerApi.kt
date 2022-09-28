package eu.darken.octi.syncs.jserver.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface JServerApi {

    @JsonClass(generateAdapter = true)
    data class RegisterResponse(
        @Json(name = "username") val accountID: String,
        @Json(name = "password") val password: String
    )

    @POST("auth/register")
    suspend fun register(
        @Header("X-Device-ID") deviceID: String,
        @Query("share") shareCode: String? = null,
    ): RegisterResponse

    @JsonClass(generateAdapter = true)
    data class ShareCodeResponse(
        @Json(name = "shareCode") val shareCode: String,
    )

    @POST("auth/share")
    suspend fun createShareCode(
        @Header("X-Device-ID") deviceID: String,
    ): ShareCodeResponse

    @JsonClass(generateAdapter = true)
    data class DevicesResponse(
        @Json(name = "count") val count: Int,
        @Json(name = "items") val items: List<Device>,
    ) {
        @JsonClass(generateAdapter = true)
        data class Device(
            @Json(name = "id") val id: String
        )
    }

    @GET("devices")
    suspend fun getDeviceList(
        @Header("X-Device-ID") deviceID: String,
    ): DevicesResponse

    @GET("module/{moduleId}")
    suspend fun readModule(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") deviceId: String,
    ): ResponseBody

    @POST("module/{moduleId}")
    suspend fun writeModule(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") deviceId: String,
        @Body payload: RequestBody,
    )
}