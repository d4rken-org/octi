package eu.darken.octi.syrvs.jserver.core

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
        @Json(name = "Username") val accountID: String,
        @Json(name = "DeviceID") val deviceID: String,
        @Json(name = "Password") val password: String
    )

    @POST("auth/register")
    suspend fun register(
        @Header("X-Account-ID") accountIDHeader: String?,
        @Header("X-Device-ID") deviceIDHeader: String?,
        @Header("X-Share-ID") shareCode: String?,
    ): RegisterResponse


    @POST("auth/share")
    suspend fun shareAccount(
        @Header("X-Account-ID") accountId: String,
    ): String

    @POST("auth/unregister")
    suspend fun unregisterDevice(
        @Header("X-Account-ID") accountIDHeader: String,
        @Header("X-Device-ID") deviceIDHeader: String,
    ): String

    @GET("module/{moduleId}")
    suspend fun readModule(
        @Header("X-Device-ID") deviceIDHeader: String,
        @Path("moduleId") moduleId: String,
    ): ResponseBody

    @POST("module/{moduleId}")
    suspend fun writeModule(
        @Header("X-Device-ID") deviceIDHeader: String,
        @Path("moduleId") moduleId: String,
    )
}