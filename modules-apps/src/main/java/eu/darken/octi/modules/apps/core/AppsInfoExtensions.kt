package eu.darken.octi.modules.apps.core

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import eu.darken.octi.modules.apps.R

@get:DrawableRes
val AppsInfo.Pkg?.installerIconRes: Int
    get() = when (this?.installerPkg) {
        "org.fdroid.fdroid",
        "org.fdroid.basic",
        "com.looker.droidify",
        -> R.drawable.ic_f_droid_24
        "com.amazon.venezia" -> R.drawable.ic_amazon_24
        "com.sec.android.app.samsungapps" -> R.drawable.ic_galaxy_store_24
        null -> R.drawable.ic_human_dolly_24
        else -> R.drawable.ic_google_play_24
    }

fun AppsInfo.Pkg.getInstallerIntent(): Pair<Intent, Intent> = when (installerPkg) {
    "org.fdroid.fdroid",
    "org.fdroid.basic",
    "com.looker.droidify",
    -> {
        val main = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("fdroid://details?id=${packageName}")
            setPackage(installerPkg)
        }
        val fallback = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://f-droid.org/packages/${packageName}/")
        }
        main to fallback
    }

    "com.amazon.venezia" -> {
        val main = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("amzn://apps/android?p=${packageName}")
            setPackage("com.amazon.venezia")
        }
        val fallback = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.amazon.com/gp/mas/dl/android?p=${packageName}")
        }
        main to fallback
    }

    "com.sec.android.app.samsungapps" -> {
        val main = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("samsungapps://ProductDetail/${packageName}")
            setPackage("com.sec.android.app.samsungapps")
        }
        val fallback = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://galaxystore.samsung.com/detail/${packageName}")
        }
        main to fallback
    }

    else -> {
        val main = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=${packageName}")
            setPackage("com.android.vending")
        }
        val fallback = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=${packageName}")
        }
        main to fallback
    }
}