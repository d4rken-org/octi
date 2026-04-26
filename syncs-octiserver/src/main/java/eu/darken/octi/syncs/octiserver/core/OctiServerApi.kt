package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
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
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import kotlin.time.Instant

interface OctiServerApi {

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
            @SerialName("version") val version: String? = null,
            @SerialName("platform") val platform: String? = null,
            @SerialName("label") val label: String? = null,
            @SerialName("addedAt") @Serializable(with = InstantSerializer::class) val addedAt: Instant? = null,
            @SerialName("lastSeen") @Serializable(with = InstantSerializer::class) val lastSeen: Instant? = null,
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

    // --- Resumable upload sessions ---

    @Serializable
    data class CreateSessionRequest(
        @SerialName("sizeBytes") val sizeBytes: Long,
        @SerialName("hashAlgorithm") val hashAlgorithm: String? = null,
        @SerialName("hashHex") val hashHex: String? = null,
    )

    @Serializable
    data class CreateSessionResponse(
        @SerialName("blobId") val blobId: String,
        @SerialName("sessionId") val sessionId: String,
        @SerialName("offsetBytes") val offsetBytes: Long,
        @Serializable(with = InstantSerializer::class) @SerialName("expiresAt") val expiresAt: Instant,
        @SerialName("state") val state: String,
    )

    @POST("module/{moduleId}/blob-sessions")
    suspend fun createBlobSession(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
        @Body request: CreateSessionRequest,
    ): CreateSessionResponse

    // Note: the server supports `HEAD /module/{moduleId}/blob-sessions/{sessionId}` for
    // resumable-upload clients. This client currently uses chunked (not resumable) upload —
    // sessions don't survive process restart — so the HEAD probe is intentionally not wired up.
    // Add it back here when the client starts persisting session state across restarts.

    @Streaming
    @PATCH("module/{moduleId}/blob-sessions/{sessionId}")
    suspend fun appendBlobSession(
        @Path("moduleId") moduleId: String,
        @Path("sessionId") sessionId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
        @Header("Upload-Offset") offset: Long,
        @Body body: RequestBody,
    ): Response<Unit>

    @Serializable
    data class FinalizeSessionRequest(
        @SerialName("hashAlgorithm") val hashAlgorithm: String,
        @SerialName("hashHex") val hashHex: String,
    )

    @Serializable
    data class FinalizeSessionResponse(
        @SerialName("blobId") val blobId: String,
        @SerialName("sessionId") val sessionId: String,
        @SerialName("sizeBytes") val sizeBytes: Long,
        @SerialName("state") val state: String,
    )

    @POST("module/{moduleId}/blob-sessions/{sessionId}/finalize")
    suspend fun finalizeBlobSession(
        @Path("moduleId") moduleId: String,
        @Path("sessionId") sessionId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
        @Body request: FinalizeSessionRequest,
    ): FinalizeSessionResponse

    @DELETE("module/{moduleId}/blob-sessions/{sessionId}")
    suspend fun abortBlobSession(
        @Path("moduleId") moduleId: String,
        @Path("sessionId") sessionId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
    )

    // --- Blob download + list ---

    @Streaming
    @GET("module/{moduleId}/blobs/{blobId}")
    suspend fun getBlob(
        @Path("moduleId") moduleId: String,
        @Path("blobId") blobId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
    ): Response<ResponseBody>

    @Serializable
    data class BlobListResponse(
        @SerialName("moduleEtag") val moduleEtag: String,
        @SerialName("blobs") val blobs: List<BlobEntry>,
    ) {
        @Serializable
        data class BlobEntry(
            @SerialName("blobId") val blobId: String,
            @SerialName("sizeBytes") val sizeBytes: Long,
            @SerialName("hashAlgorithm") val hashAlgorithm: String? = null,
            @SerialName("hashHex") val hashHex: String? = null,
        )
    }

    @GET("module/{moduleId}/blobs")
    suspend fun listBlobs(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
    ): BlobListResponse

    @DELETE("module/{moduleId}/blobs/{blobId}")
    suspend fun deleteBlob(
        @Path("moduleId") moduleId: String,
        @Path("blobId") blobId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
        @Header("If-Match") ifMatch: String,
    ): Response<Unit>

    // --- Module commit (blob-aware PUT) ---

    @Serializable
    data class ModuleCommitRequest(
        @SerialName("documentBase64") val documentBase64: String,
        @SerialName("blobRefs") val blobRefs: List<BlobRef>,
    ) {
        @Serializable
        data class BlobRef(
            @SerialName("blobId") val blobId: String,
        )
    }

    @retrofit2.http.PUT("module/{moduleId}")
    suspend fun commitModule(
        @Path("moduleId") moduleId: String,
        @Header("X-Device-ID") callerDeviceId: String,
        @Query("device-id") targetDeviceId: String,
        @Header("If-Match") ifMatch: String?,
        @Header("If-None-Match") ifNoneMatch: String?,
        @Body request: ModuleCommitRequest,
    ): Response<Unit>

    // --- Account storage (replaces blob-quota) ---

    @Serializable
    data class AccountStorageResponse(
        @SerialName("storageApiVersion") val storageApiVersion: Int,
        @SerialName("accountQuotaBytes") val accountQuotaBytes: Long,
        @SerialName("usedBytes") val usedBytes: Long,
        @SerialName("reservedBytes") val reservedBytes: Long,
        @SerialName("availableBytes") val availableBytes: Long,
        @SerialName("maxBlobBytes") val maxBlobBytes: Long,
        @SerialName("maxModuleDocumentBytes") val maxModuleDocumentBytes: Long,
        @SerialName("maxActiveUploadSessionsPerDevice") val maxActiveUploadSessionsPerDevice: Int,
        @SerialName("idleSessionTtlSeconds") val idleSessionTtlSeconds: Long? = null,
        @SerialName("absoluteSessionTtlSeconds") val absoluteSessionTtlSeconds: Long? = null,
    )

    @GET("account/storage")
    suspend fun getAccountStorage(
        @Header("X-Device-ID") callerDeviceId: String,
    ): AccountStorageResponse

}
