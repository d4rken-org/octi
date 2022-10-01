package eu.darken.octi.common

import androidx.annotation.Keep
import java.lang.reflect.Field
import java.time.Instant


// Can't be const because that prevents them from being mocked in tests
@Suppress("MayBeConstant")
@Keep
object BuildConfigWrap {
    val APPLICATION_ID = getBuildConfigValue("PACKAGENAME") as String
    val DEBUG: Boolean = BuildConfig.DEBUG
    val BUILD_TYPE: BuildType = when (val typ = getBuildConfigValue("BUILD_TYPE") as String) {
        "debug" -> BuildType.DEV
        "beta" -> BuildType.BETA
        "release" -> BuildType.RELEASE
        else -> throw IllegalArgumentException("Unknown buildtype: $typ")
    }

    enum class BuildType {
        DEV,
        BETA,
        RELEASE,
        ;
    }

    val FLAVOR: Flavor = when (val flav = getBuildConfigValue(("FLAVOR"))) {
        "gplay" -> Flavor.GPLAY
        "foss" -> Flavor.FOSS
        else -> throw IllegalStateException("Unknown flavor: $flav")
    }

    enum class Flavor {
        GPLAY,
        FOSS,
        ;
    }

    val BUILD_TIME: Instant = Instant.parse(getBuildConfigValue("BUILDTIME") as String)

    val VERSION_CODE: Long = (getBuildConfigValue("VERSION_CODE") as Int).toLong()
    val VERSION_NAME: String = getBuildConfigValue("VERSION_NAME") as String
    val GIT_SHA: String = getBuildConfigValue("GITSHA") as String

    val VERSION_DESCRIPTION: String = "v$VERSION_NAME ($VERSION_CODE) ~ $GIT_SHA/$FLAVOR/$BUILD_TYPE"

    private fun getBuildConfigValue(fieldName: String): Any? = try {
        val c = Class.forName("eu.darken.octi.BuildConfig")
        val f: Field = c.getDeclaredField(fieldName).apply {
            isAccessible = true
        }
        f.get(null)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
