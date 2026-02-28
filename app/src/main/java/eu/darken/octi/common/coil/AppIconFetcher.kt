package eu.darken.octi.common.coil

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import eu.darken.octi.R
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.getIcon2
import eu.darken.octi.modules.apps.core.AppsInfo
import javax.inject.Inject

class AppIconFetcher @Inject constructor(
    private val packageManager: PackageManager,
    private val data: AppsInfo.Pkg,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        log { "Fetching $data" }
        val baseIcon = packageManager.getIcon2(data.packageName)
            ?: ContextCompat.getDrawable(options.context, R.drawable.ic_baseline_apps_24)!!

        return ImageFetchResult(
            image = baseIcon.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory @Inject constructor(
        private val packageManager: PackageManager,
    ) : Fetcher.Factory<AppsInfo.Pkg> {

        override fun create(
            data: AppsInfo.Pkg,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = AppIconFetcher(packageManager, data, options)
    }
}
