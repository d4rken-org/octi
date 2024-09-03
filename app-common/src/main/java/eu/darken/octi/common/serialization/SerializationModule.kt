package eu.darken.octi.common.serialization

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.serialization.adapter.ByteStringAdapter
import eu.darken.octi.common.serialization.adapter.DurationAdapter
import eu.darken.octi.common.serialization.adapter.InstantAdapter
import eu.darken.octi.common.serialization.adapter.LocaleAdapter
import eu.darken.octi.common.serialization.adapter.OffsetDateTimeAdapter
import eu.darken.octi.common.serialization.adapter.RegexAdapter
import eu.darken.octi.common.serialization.adapter.UUIDAdapter
import eu.darken.octi.common.serialization.adapter.UriAdapter
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationModule {

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder().apply {
        add(ByteStringAdapter())
        add(DurationAdapter())
        add(InstantAdapter())
        add(LocaleAdapter())
        add(OffsetDateTimeAdapter())
        add(RegexAdapter())
        add(UriAdapter())
        add(UUIDAdapter())
    }.build()
}
