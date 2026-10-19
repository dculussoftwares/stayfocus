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
import androidx.core.view.WindowInsetsControllerCompat
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

/** Icon appearance a window had before the first live [V5Screen], plus the live screens' requests, oldest first. */
private class WindowBars(
    val status: Boolean,
    val navigation: Boolean,
) {
    val requests = mutableListOf<IconRequest>()
}

// Plain class on purpose: requests are removed by identity.
private class IconRequest(
    val lightIcons: Boolean,
)

// Main thread only. The newest live request wins; when it leaves, the one below it is applied again.
private val windowBars = WeakHashMap<Window, WindowBars>()

private fun WindowInsetsControllerCompat.applyIcons(bars: WindowBars) {
    val top = bars.requests.lastOrNull()
    // "Appearance light" means dark icons, for a light bar background.
    isAppearanceLightStatusBars = if (top != null) !top.lightIcons else bars.status
    isAppearanceLightNavigationBars = if (top != null) !top.lightIcons else bars.navigation
}

@Composable
private fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    DisposableEffect(window, view, lightIcons) {
        val controller = WindowCompat.getInsetsController(window, view)
        val bars =
            windowBars.getOrPut(window) {
                WindowBars(controller.isAppearanceLightStatusBars, controller.isAppearanceLightNavigationBars)
            }
        val request = IconRequest(lightIcons)
        bars.requests += request
        controller.applyIcons(bars)
        onDispose {
            bars.requests -= request
            controller.applyIcons(bars)
            if (bars.requests.isEmpty()) windowBars.remove(window)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
