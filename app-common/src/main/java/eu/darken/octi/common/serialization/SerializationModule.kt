package eu.darken.octi.common.serialization

import android.net.Uri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.serialization.serializer.ByteStringSerializer
import eu.darken.octi.common.serialization.serializer.DurationSerializer
import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.common.serialization.serializer.LocaleSerializer
import eu.darken.octi.common.serialization.serializer.OffsetDateTimeSerializer
import eu.darken.octi.common.serialization.serializer.RegexSerializer
import eu.darken.octi.common.serialization.serializer.UUIDSerializer
import eu.darken.octi.common.serialization.serializer.UriSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okio.ByteString
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        serializersModule = SerializersModule {
            contextual(ByteString::class, ByteStringSerializer)
            contextual(Instant::class, InstantSerializer)
            contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
            contextual(Duration::class, DurationSerializer)
            contextual(UUID::class, UUIDSerializer)
            contextual(Uri::class, UriSerializer)
            contextual(Locale::class, LocaleSerializer)
            contextual(Regex::class, RegexSerializer)
        }
    }

    @Provides
    @Singleton
    @RetrofitJson
    fun retrofitJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        serializersModule = SerializersModule {
            contextual(ByteString::class, ByteStringSerializer)
            contextual(Instant::class, InstantSerializer)
            contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
            contextual(Duration::class, DurationSerializer)
            contextual(UUID::class, UUIDSerializer)
            contextual(Uri::class, UriSerializer)
            contextual(Locale::class, LocaleSerializer)
            contextual(Regex::class, RegexSerializer)
        }
    }
}
