package eu.darken.octi.sync.core.provider.jserver

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
        @Json(name = "accountID") val accountID: String,
        @Json(name = "deviceID") val deviceID: String,
        @Json(name = "password") val password: String
    )

    @POST("auth/register")
    fun register(
        @Header("X-Account-ID") accountIDHeader: String?,
        @Header("X-Device-ID") deviceIDHeader: String?,
        @Header("X-Share-ID") shareCode: String?,
    ): RegisterResponse


    @POST("auth/share")
    fun shareAccount(
        @Header("X-Account-ID") accountId: String,
    ): String

    @POST("auth/unregister")
    fun unregisterDevice(
        @Header("X-Account-ID") accountIDHeader: String,
        @Header("X-Device-ID") deviceIDHeader: String,
    ): String

    @GET("module/{moduleId}")
    fun readModule(
        @Header("X-Device-ID") deviceIDHeader: String,
        @Path("moduleId") moduleId: String,
    ): ResponseBody

    @POST("module/{moduleId}")
    fun writeModule(
        @Header("X-Device-ID") deviceIDHeader: String,
        @Path("moduleId") moduleId: String,
    )
}