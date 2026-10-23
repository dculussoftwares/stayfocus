package com.dculus.stayfocused.core.ui.icon

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache

/** First letter of [label] (or of the package's last segment), upper-cased. */
internal fun tileLetter(
    label: String?,
    pkg: String,
): String {
    val source = label?.trim().orEmpty().ifEmpty { pkg.substringAfterLast('.') }
    return source.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
}

/** One shared [ImageLoader] for launcher icons, with an in-memory cache. */
internal object PackageIconLoader {
    private const val MEMORY_CACHE_PERCENT = 0.1

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(PackageIconFetcher.Factory(context)) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
            .build()
}
