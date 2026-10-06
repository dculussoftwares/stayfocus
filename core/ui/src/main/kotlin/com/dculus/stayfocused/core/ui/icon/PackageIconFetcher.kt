package com.dculus.stayfocused.core.ui.icon

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.toUri

/** Coil data for an installed app's launcher icon: `package:<applicationId>`. */
fun packageIconUri(pkg: String): Uri = "$PACKAGE_SCHEME:$pkg".toUri()

internal const val PACKAGE_SCHEME = "package"
private const val ICON_PX = 192

/**
 * Loads a launcher icon through [PackageManager] for `package:` URIs.
 * Results are cached in Coil's memory cache (keyed by the URI).
 * A package that is not installed (or not visible) throws [PackageManager.NameNotFoundException],
 * which Coil reports as an error so the caller can show the fallback tile.
 */
internal class PackageIconFetcher(
    private val context: Context,
    private val pkg: String,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val icon = context.packageManager.getApplicationIcon(pkg)
        return ImageFetchResult(
            // Rasterised at a fixed size: the cache key is the URI, so it must not depend on request size.
            image = icon.toBitmap(width = ICON_PX, height = ICON_PX).asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val context: Context,
    ) : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val pkg = data.path?.takeIf { data.scheme == PACKAGE_SCHEME && it.isNotBlank() }
            return pkg?.let { PackageIconFetcher(context.applicationContext, it) }
        }
    }
}
