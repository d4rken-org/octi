package eu.darken.octi.common.coil

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.util.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import javax.inject.Provider
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module
class CoilModule {

    @Provides
    fun imageLoader(
        @ApplicationContext context: Context,
        appIconFetcherFactory: AppIconFetcher.Factory,
        dispatcherProvider: DispatcherProvider,
    ): ImageLoader = ImageLoader.Builder(context).apply {

        if (BuildConfigWrap.DEBUG) {
            val coilLogger = object : Logger {
                override var minLevel = Logger.Level.Verbose
                override fun log(tag: String, level: Logger.Level, message: String?, throwable: Throwable?) {
                    val priority = when (level) {
                        Logger.Level.Verbose -> Logging.Priority.VERBOSE
                        Logger.Level.Debug -> Logging.Priority.DEBUG
                        Logger.Level.Info -> Logging.Priority.INFO
                        Logger.Level.Warn -> Logging.Priority.WARN
                        Logger.Level.Error -> Logging.Priority.ERROR
                    }
                    log("Coil:$tag", priority) { "$message ${throwable?.asLog()}" }
                }
            }
            logger(coilLogger)
        }
        components {
            add(appIconFetcherFactory)
        }
    }.build()

    @Singleton
    @Provides
    fun imageLoaderFactory(imageLoaderSource: Provider<ImageLoader>): SingletonImageLoader.Factory =
        SingletonImageLoader.Factory {
            log(TAG) { "Preparing imageloader factory" }
            imageLoaderSource.get()
        }

    companion object {
        private val TAG = logTag("Coil", "Module")
    }
}
