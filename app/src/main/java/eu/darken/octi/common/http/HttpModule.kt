package eu.darken.octi.common.http

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class HttpModule {

    @Reusable
    @Provides
    fun loggingInterceptor(): HttpLoggingInterceptor {
        val logger = HttpLoggingInterceptor.Logger {
            log(TAG, VERBOSE) { it }
        }
        return HttpLoggingInterceptor(logger).apply {
            level = (HttpLoggingInterceptor.Level.BODY)
        }
    }

    @Singleton
    @Provides
    fun baseHttpClient(
        @BaseCache cache: Cache,
        loggingInterceptor: HttpLoggingInterceptor,
        userAgentInterceptor: UserAgentInterceptor,
    ): OkHttpClient = OkHttpClient().newBuilder().apply {
        cache(cache)
        connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        retryOnConnectionFailure(true)
        addInterceptor(loggingInterceptor)
        addInterceptor(userAgentInterceptor)
    }.build()

    @BaseCache
    @Provides
    @Singleton
    fun baseHttpCache(@ApplicationContext context: Context): Cache {
        val cacheDir = File(context.cacheDir, "http_base_cache")
        return Cache(cacheDir, 1024L * 1024L * 20) // 20 MB
    }

    @Qualifier
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    annotation class BaseCache

    companion object {
        private val TAG = logTag("Http")
        const val TIMEOUT_SECONDS = 20L
    }
}