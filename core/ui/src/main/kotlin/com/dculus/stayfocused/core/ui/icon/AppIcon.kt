package com.dculus.stayfocused.core.ui.icon

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.memory.MemoryCache
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/**
 * An app's launcher icon, or a coloured letter tile (as in the design prototype) while it loads,
 * when the package is unknown, or in previews.
 *
 * @param label shown as the tile letter; the package name is used when null or blank.
 */
@Composable
fun AppIcon(
    pkg: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 40.dp,
) {
    val cornerRadius = size * 0.33f
    val tile: @Composable () -> Unit = { LetterTile(pkg, label, size, cornerRadius) }
    if (LocalInspectionMode.current) {
        Box(modifier) { tile() }
        return
    }
    val context = LocalContext.current
    val loader = remember(context) { PackageIconLoader.get(context) }
    SubcomposeAsyncImage(
        model = remember(pkg) { packageIconUri(pkg) },
        imageLoader = loader,
        contentDescription = label,
        contentScale = ContentScale.Fit,
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius)),
        loading = { tile() },
        error = { tile() },
    )
}

@Composable
private fun LetterTile(
    pkg: String,
    label: String?,
    size: Dp,
    cornerRadius: Dp,
) {
    val colors = StayFocusedTheme.colors
    val tints = colors.appTints
    val tint = tints[Math.floorMod(pkg.hashCode(), tints.size)]
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tileLetter(label, pkg),
            style = StayFocusedTheme.type.display.copy(fontSize = (size.value * 0.4f).sp),
            color = colors.onAccent,
        )
    }
}

@Preview(widthDp = 220, heightDp = 72)
@Composable
internal fun AppIconPreview() {
    StayFocusedTheme {
        Row(
            modifier = Modifier.background(StayFocusedTheme.colors.background).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(pkg = "com.instagram.android", label = "Instagram")
            AppIcon(pkg = "com.reddit.frontpage", label = "Reddit")
            AppIcon(pkg = "com.example.unknown")
        }
    }
}

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
