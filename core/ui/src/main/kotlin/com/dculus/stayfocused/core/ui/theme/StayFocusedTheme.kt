package com.dculus.stayfocused.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Accessors for the design tokens: `StayFocusedTheme.colors / .type / .shapes / .spacing`. */
object StayFocusedTheme {
    val colors: StayFocusedColors
        @Composable @ReadOnlyComposable
        get() = LocalStayFocusedColors.current
    val type: StayFocusedTypography
        @Composable @ReadOnlyComposable
        get() = LocalStayFocusedTypography.current
    val shapes: StayFocusedShapes
        @Composable @ReadOnlyComposable
        get() = LocalStayFocusedShapes.current
    val spacing: StayFocusedSpacing
        @Composable @ReadOnlyComposable
        get() = LocalStayFocusedSpacing.current
}

internal fun StayFocusedColors.toMaterialColorScheme(): ColorScheme =
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        secondary = secondary,
        onSecondary = background,
        secondaryContainer = track,
        onSecondaryContainer = text,
        tertiary = warning,
        onTertiary = background,
        background = background,
        onBackground = text,
        surface = panel,
        onSurface = text,
        surfaceVariant = track,
        onSurfaceVariant = secondary,
        surfaceTint = accent,
        surfaceBright = panelGradientStart,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = panelGradientEnd,
        surfaceContainer = panel,
        surfaceContainerHigh = panelGradientStart,
        surfaceContainerHighest = track,
        outline = tertiary,
        outlineVariant = hairline14,
        error = alert,
        onError = background,
        errorContainer = alert,
        onErrorContainer = background,
        scrim = background,
    )

internal fun StayFocusedTypography.toMaterialTypography(): Typography =
    Typography(
        displayLarge = displayXL,
        displayMedium = displayL,
        displaySmall = displayM,
        headlineLarge = display,
        headlineMedium = displayS,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = bodyL,
        titleSmall = body,
        bodyLarge = bodyL,
        bodyMedium = body,
        bodySmall = bodyS,
        labelLarge = body,
        labelMedium = label,
        labelSmall = labelS,
    )

internal fun StayFocusedShapes.toMaterialShapes(): Shapes =
    Shapes(
        extraSmall = chip,
        small = chip,
        medium = input,
        large = card,
        extraLarge = hero,
    )

/** Dark-only theme: wraps [MaterialTheme] and provides the Stay Focused token locals. */
@Composable
fun StayFocusedTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val families =
        remember(context, inspection) {
            if (inspection) BundledFontFamilies else loadFontFamilies(context)
        }
    val colors = remember { StayFocusedColors() }
    val type = remember(families) { stayFocusedTypography(families) }
    val shapes = remember { StayFocusedShapes() }
    val spacing = remember { StayFocusedSpacing() }
    CompositionLocalProvider(
        LocalStayFocusedColors provides colors,
        LocalStayFocusedTypography provides type,
        LocalStayFocusedShapes provides shapes,
        LocalStayFocusedSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = type.toMaterialTypography(),
            shapes = shapes.toMaterialShapes(),
            content = content,
        )
    }
}
