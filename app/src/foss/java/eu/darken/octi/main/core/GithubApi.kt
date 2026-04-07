package eu.darken.octi.main.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.time.Instant

interface GithubApi {

    @Serializable
    data class ReleaseInfo(
        @SerialName("name") val name: String,
        @SerialName("tag_name") val tagName: String,
        @SerialName("prerelease") val isPreRelease: Boolean,
        @SerialName("html_url") val htmlUrl: String,
        @Serializable(with = InstantSerializer::class) @SerialName("published_at") val publishedAt: Instant,
        @SerialName("body") val body: String,
        @SerialName("assets") val assets: List<Asset>,
    ) {
        @Serializable
        data class Asset(
            @SerialName("id") val id: Long,
            @SerialName("name") val name: String,
            @SerialName("label") val label: String,
            @SerialName("size") val size: Long,
            @SerialName("content_type") val contentType: String,
            @SerialName("browser_download_url") val downloadUrl: String,
        )
    }

    @GET("/repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(@Path("owner") owner: String, @Path("repo") repo: String): ReleaseInfo

    @GET("/repos/{owner}/{repo}/releases")
    suspend fun allReleases(@Path("owner") owner: String, @Path("repo") repo: String): List<ReleaseInfo>
}
