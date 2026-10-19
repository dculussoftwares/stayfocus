package com.dculus.stayfocused.core.ui.v5.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.util.WeakHashMap

/**
 * Root of a migrated screen: [V5Theme], the background ([V5Colors.background] unless [background] is set) and
 * the status/navigation bar icon colour. [darkContent] means the screen is dark (welcome, scan, blocked, kids
 * QR), so the bar icons are light; otherwise they are dark. Content handles insets with [v5StatusBarPadding]
 * and [v5NavigationBarPadding].
 */
@Composable
fun V5Screen(
    darkContent: Boolean,
    modifier: Modifier = Modifier,
    background: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    V5Theme {
        SystemBarIcons(lightIcons = darkContent)
        val base = modifier.fillMaxSize()
        Box(
            modifier =
                if (background != null) {
                    base.background(background)
                } else {
                    base.background(V5Theme.colors.background)
                },
            content = content,
        )
    }
}

/** Status-bar inset plus [extra]. The prototype's top padding includes its 46 px fake status bar: use `top - 46`. */
@Composable
fun Modifier.v5StatusBarPadding(extra: Dp = 0.dp): Modifier =
    windowInsetsPadding(WindowInsets.statusBars).padding(top = extra)

/** Navigation-bar inset plus [extra]. */
@Composable
fun Modifier.v5NavigationBarPadding(extra: Dp = 0.dp): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = extra)

/** Icon appearance a window had before the first live [V5Screen], restored when the last one leaves. */
private class SavedBars(
    val status: Boolean,
    val navigation: Boolean,
    var screens: Int = 0,
)

// Main thread only. Counting keeps a screen that is still fading out from undoing the one fading in.
private val savedBars = WeakHashMap<Window, SavedBars>()

@Composable
private fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    DisposableEffect(window, view, lightIcons) {
        val controller = WindowCompat.getInsetsController(window, view)
        val saved =
            savedBars.getOrPut(window) {
                SavedBars(controller.isAppearanceLightStatusBars, controller.isAppearanceLightNavigationBars)
            }
        saved.screens++
        // "Appearance light" means dark icons, for a light bar background.
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
        onDispose {
            saved.screens--
            if (saved.screens == 0) {
                savedBars.remove(window)
                controller.isAppearanceLightStatusBars = saved.status
                controller.isAppearanceLightNavigationBars = saved.navigation
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
