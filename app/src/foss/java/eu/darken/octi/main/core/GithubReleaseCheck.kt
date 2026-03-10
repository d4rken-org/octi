package eu.darken.octi.main.core

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Reusable
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.RetrofitJson
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Inject

@Reusable
class GithubReleaseCheck @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val baseHttpClient: OkHttpClient,
    @RetrofitJson private val retrofitJson: Json,
) {

    private val api: GithubApi by lazy {
        Retrofit.Builder().apply {
            baseUrl("https://api.github.com")
            client(baseHttpClient)
            addConverterFactory(ScalarsConverterFactory.create())
            addConverterFactory(retrofitJson.asConverterFactory("application/json".toMediaType()))
        }.build().create(GithubApi::class.java)
    }

    /**
     * Only returns non-pre-releases
     */
    suspend fun latestRelease(
        owner: String,
        repo: String,
    ): GithubApi.ReleaseInfo? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "latestRelease(owner=$owner, repo=$repo)..." }
        return@withContext try {
            api.latestRelease(owner, repo).also {
                log(TAG) { "latestRelease(owner=$owner, repo=$repo) is $it" }
            }
        } catch (e: HttpException) {
            if (e.code() == 404) {
                log(TAG, WARN) { "No release available." }
                return@withContext null
            }
            throw e
        }
    }

    suspend fun allReleases(
        owner: String,
        repo: String,
    ): List<GithubApi.ReleaseInfo> = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "allReleases(owner=$owner, repo=$repo)..." }
        return@withContext api.allReleases(owner, repo).also {
            log(TAG) { "allReleases(owner=$owner, repo=$repo) is ${it.size} releases" }
        }
    }

    companion object {
        private val TAG = logTag("Updater", "Github", "ReleaseCheck")
    }
}
