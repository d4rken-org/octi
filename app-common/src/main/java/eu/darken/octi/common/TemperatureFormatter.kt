package eu.darken.octi.common

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.core.text.util.LocalePreferences

class TemperatureFormatter(private val context: Context) {

    private val temperatureUnit: TemperatureUnit = getTemperatureUnit()

    fun formatTemperature(tempCelsius: Float): String = when (temperatureUnit) {
        TemperatureUnit.CELSIUS -> "${tempCelsius.toInt()}°C"
        TemperatureUnit.FAHRENHEIT -> "${celsiusToFahrenheit(tempCelsius).toInt()}°F"
    }

    private fun getTemperatureUnit(): TemperatureUnit = if (hasApiLevel(Build.VERSION_CODES.N)) {
        try {
            when (LocalePreferences.getTemperatureUnit()) {
                LocalePreferences.TemperatureUnit.FAHRENHEIT -> TemperatureUnit.FAHRENHEIT
                else -> TemperatureUnit.CELSIUS
            }
        } catch (_: Exception) {
            getTemperatureUnitFromLocale()
        }
    } else {
        getTemperatureUnitFromLocale()
    }

    private fun getTemperatureUnitFromLocale(): TemperatureUnit {
        val locale = if (hasApiLevel(Build.VERSION_CODES.N)) {
            @SuppressLint("NewApi")
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val country = locale.country

        return if (FAHRENHEIT_COUNTRIES.contains(country)) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
    }

    private fun celsiusToFahrenheit(celsius: Float): Float = (celsius * 9f / 5f) + 32f

    enum class TemperatureUnit {
        CELSIUS,
        FAHRENHEIT
    }

    companion object {
        private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
    }
}