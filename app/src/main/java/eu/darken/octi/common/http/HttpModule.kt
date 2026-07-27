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
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
        connectTimeout(TIMEOUT.toJavaDuration())
        readTimeout(TIMEOUT.toJavaDuration())
        writeTimeout(TIMEOUT.toJavaDuration())
        // Bounds a call end-to-end once the dispatcher starts it — redirects, retries and full body
        // consumption — none of which the per-socket timeouts above cover on their own.
        //
        // What it does NOT cover, verified against OkHttp 4.9.1 in WebSocketCallTimeoutTest: time
        // an async call spends *queued* in the Dispatcher. RealCall.enqueue() only calls
        // callStart(); the call timeout is entered in AsyncCall.run(), i.e. when the call is
        // actually dispatched. With maxRequestsPerHost = 5 and one slot permanently held by the
        // live-mode WebSocket, a queued call can therefore still wait arbitrarily long. That gap is
        // closed one level up, by the connector processor's per-command bound.
        callTimeout(CALL_TIMEOUT.toJavaDuration())
        retryOnConnectionFailure(true)
        addInterceptor(loggingInterceptor)
        addInterceptor(userAgentInterceptor)
    }.build()

    /**
     * Separate client for streaming transfers — see [StreamingClient] for why the total-call bound
     * and body logging of [baseHttpClient] are unusable here.
     */
    @StreamingClient
    @Singleton
    @Provides
    fun streamingHttpClient(
        userAgentInterceptor: UserAgentInterceptor,
    ): OkHttpClient = OkHttpClient().newBuilder().apply {
        // No cache: blob bodies are large, encrypted and consumed exactly once.
        connectTimeout(TIMEOUT.toJavaDuration())
        readTimeout(TIMEOUT.toJavaDuration())
        writeTimeout(TIMEOUT.toJavaDuration())
        // Total-call bound disabled on purpose. The response body is consumed by the caller after
        // the call returns (decryption in the blob store), so any finite value here would abort
        // large or slow transfers mid-body. The per-socket timeouts above still guarantee progress.
        callTimeout(Duration.ZERO)
        retryOnConnectionFailure(true)
        addInterceptor(
            HttpLoggingInterceptor { log(TAG, VERBOSE) { it } }.apply {
                // HEADERS, never BODY: the body interceptor buffers the whole streamed payload.
                level = HttpLoggingInterceptor.Level.HEADERS
            }
        )
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
        private val TIMEOUT = 20.seconds
        private val CALL_TIMEOUT = 60.seconds
    }
}
