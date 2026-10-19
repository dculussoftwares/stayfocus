@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.dculus.stayfocused.core.ui.icon.PackageIconLoader
import com.dculus.stayfocused.core.ui.icon.packageIconUri
import com.dculus.stayfocused.core.ui.icon.tileLetter
import com.dculus.stayfocused.core.ui.v5.theme.V5AppBrandColors
import com.dculus.stayfocused.core.ui.v5.theme.V5Colors
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme

/** Corner radius of an app tile: .34 × size (13 at 38 dp, 16 at 48, 26 at 76). */
private const val TILE_RADIUS_RATIO = 0.34f
private const val TILE_LETTER_RATIO = 0.4f
private const val STACK_MAX = 3
private val STACK_OVERLAP = (-8).dp
private val STACK_BORDER = 2.dp

/** Background and letter colour of a letter tile (V5_SPEC §3). */
@Immutable
data class AppTileColors(
    val background: Color,
    val letter: Color,
)

/**
 * Colours for the letter fallback: the prototype's brand colour for well-known packages (Snapchat has an ink letter),
 * otherwise a stable pick from a palette of the v5 accent colours hashed from [pkg].
 */
fun appTileColors(
    pkg: String,
    colors: V5Colors = V5Colors(),
    brand: V5AppBrandColors = V5AppBrandColors(),
): AppTileColors {
    val known =
        when (pkg) {
            "com.instagram.android" -> brand.instagram
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> brand.tiktok
            "com.google.android.youtube" -> brand.youtube
            "com.snapchat.android" -> brand.snapchat
            "com.reddit.frontpage" -> brand.reddit
            "com.twitter.android" -> brand.x
            "com.whatsapp" -> brand.whatsapp
            "com.netflix.mediaclient" -> brand.netflix
            else -> null
        }
    if (known != null) {
        return AppTileColors(known, if (known == brand.snapchat) colors.ink else colors.onGradient)
    }
    val palette =
        listOf(colors.orange, colors.violet, colors.pink, colors.green, colors.inkGlow, colors.red, colors.pinkText)
    return AppTileColors(palette[Math.floorMod(pkg.hashCode(), palette.size)], colors.onGradient)
}

/**
 * An app's launcher icon (via the shared package-icon loader) clipped to a rounded square of radius .34 × [size], or
 * a letter on its brand / hashed colour while loading, on failure and in previews. [label] gives the letter and the
 * accessibility name; without it the tile is decorative and the letter comes from the package name.
 */
@Composable
fun AppIconTile(
    pkg: String,
    size: Dp,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val shape = RoundedCornerShape(size * TILE_RADIUS_RATIO)
    val fallback: @Composable () -> Unit = { LetterTile(pkg, label, size, shape) }
    if (LocalInspectionMode.current) {
        Box(modifier) { fallback() }
        return
    }
    val context = LocalContext.current
    val loader = remember(context) { PackageIconLoader.get(context) }
    SubcomposeAsyncImage(
        model = remember(pkg) { packageIconUri(pkg) },
        imageLoader = loader,
        contentDescription = label,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).clip(shape),
        loading = { fallback() },
        error = { fallback() },
    )
}

@Composable
private fun LetterTile(
    pkg: String,
    label: String?,
    size: Dp,
    shape: RoundedCornerShape,
) {
    val tile = appTileColors(pkg, V5Theme.colors)
    Box(
        modifier = Modifier.size(size).clip(shape).background(tile.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tileLetter(label, pkg),
            style =
                V5Theme.type.bodyL.copy(
                    fontSize = (size.value * TILE_LETTER_RATIO).sp,
                    fontWeight = FontWeight.Bold,
                ),
            color = tile.letter,
        )
    }
}

/**
 * Up to three overlapping [AppIconTile]s (overlap 8 dp, 2 dp white border, each [size] square in total); extra packages
 * are not shown. [labels] optionally maps a package to its app name for the letter fallback.
 */
@Composable
fun AppIconStack(
    pkgs: List<String>,
    size: Dp,
    modifier: Modifier = Modifier,
    labels: Map<String, String> = emptyMap(),
) {
    val shape = RoundedCornerShape(size * TILE_RADIUS_RATIO)
    Row(
        modifier = modifier.padding(start = -STACK_OVERLAP),
        horizontalArrangement = Arrangement.spacedBy(STACK_OVERLAP),
    ) {
        pkgs.take(STACK_MAX).forEach { pkg ->
            Box(
                modifier =
                    Modifier
                        .size(
                            size,
                        ).border(STACK_BORDER, V5Theme.colors.surface, shape)
                        .padding(STACK_BORDER),
            ) {
                AppIconTile(pkg, size - STACK_BORDER * 2, label = labels[pkg])
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 260)
@Composable
internal fun AppIconTilesPreview() {
    V5PreviewSurface(gradient = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconTile("com.instagram.android", 38.dp, label = "Instagram")
            AppIconTile("com.snapchat.android", 38.dp, label = "Snapchat")
            AppIconTile("com.zhiliaoapp.musically", 48.dp, label = "TikTok")
            AppIconTile("com.example.unknown", 48.dp)
            AppIconTile("com.reddit.frontpage", 76.dp, label = "Reddit")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconStack(listOf("com.instagram.android", "com.zhiliaoapp.musically"), 38.dp)
            AppIconStack(
                listOf(
                    "com.instagram.android",
                    "com.google.android.youtube",
                    "com.snapchat.android",
                    "com.reddit.frontpage",
                ),
                38.dp,
            )
        }
    }
}
