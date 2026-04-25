package eu.darken.octi.common.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetSettingsStore

private val Context.widgetSettingsDataStore by preferencesDataStore(name = "widget_settings")

@Module
@InstallIn(SingletonComponent::class)
object WidgetSettingsModule {

    @Provides
    @Singleton
    @WidgetSettingsStore
    fun widgetSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.widgetSettingsDataStore
}
