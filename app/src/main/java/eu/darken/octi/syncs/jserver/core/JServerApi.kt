package eu.darken.octi.syncs.jserver.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface JServerApi {

    @JsonClass(generateAdapter = true)
    data class RegisterResponse(
        @Json(name = "username") val accountID: String,
        @Json(name = "deviceID") val deviceID: String,
        @Json(name = "password") val password: String
    )

    @POST("auth/register")
    suspend fun register(
        @Header("X-Device-ID") deviceID: String,
    ): RegisterResponse

    @POST("auth/share")
    suspend fun createLinkCode(
        @Header("X-Device-ID") deviceID: String,
    ): String

    @GET("auth/devices")
    suspend fun getDeviceList(): List<String>

    @GET("module/{moduleId}")
    suspend fun readModule(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") deviceId: String,
    ): ResponseBody

    @POST("module/{moduleId}")
    suspend fun writeModule(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") deviceId: String,
    )
}