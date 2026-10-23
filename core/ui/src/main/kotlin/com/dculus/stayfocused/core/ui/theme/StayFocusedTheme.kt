package com.dculus.stayfocused.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/** Accessors for the v5 tokens: `StayFocusedTheme.colors / .type / .shapes / .spacing`. */
object StayFocusedTheme {
    val colors: V5Colors
        @Composable @ReadOnlyComposable
        get() = LocalV5Colors.current
    val type: V5Type
        @Composable @ReadOnlyComposable
        get() = LocalV5Type.current
    val shapes: V5Shapes
        @Composable @ReadOnlyComposable
        get() = LocalV5Shapes.current
    val spacing: V5Spacing
        @Composable @ReadOnlyComposable
        get() = LocalV5Spacing.current
}

internal fun V5Colors.toMaterialColorScheme(): ColorScheme =
    lightColorScheme(
        primary = ink,
        onPrimary = surface,
        primaryContainer = ink,
        onPrimaryContainer = surface,
        secondary = orange,
        onSecondary = surface,
        secondaryContainer = track,
        onSecondaryContainer = ink,
        tertiary = violet,
        onTertiary = surface,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = track,
        onSurfaceVariant = textSecondary,
        surfaceTint = ink,
        surfaceBright = surface,
        surfaceDim = background,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surfaceRaised,
        surfaceContainer = surfaceRaised,
        surfaceContainerHigh = track,
        surfaceContainerHighest = track,
        outline = textSecondary,
        outlineVariant = hairline,
        error = error,
        onError = surface,
        errorContainer = error,
        onErrorContainer = surface,
        scrim = ink,
    )

internal fun V5Type.toMaterialTypography(): Typography =
    Typography(
        displayLarge = displayXL,
        displayMedium = displayL,
        displaySmall = hero,
        headlineLarge = title,
        headlineMedium = headline,
        headlineSmall = heading,
        titleLarge = cardTitle,
        titleMedium = bodyL,
        titleSmall = label,
        bodyLarge = bodyL,
        bodyMedium = body,
        bodySmall = label,
        labelLarge = label,
        labelMedium = label,
        labelSmall = tag,
    )

internal fun V5Shapes.toMaterialShapes(): Shapes =
    Shapes(
        extraSmall = tag,
        small = tag,
        medium = input,
        large = card,
        extraLarge = hero,
    )

/** Light v5 theme: provides the token locals and a matching Material 3 scheme for any M3 widget inside. */
@Composable
fun StayFocusedTheme(content: @Composable () -> Unit) {
    val colors = remember { V5Colors() }
    val type = remember { v5Type() }
    val shapes = remember { V5Shapes() }
    val spacing = remember { V5Spacing() }
    CompositionLocalProvider(
        LocalV5Colors provides colors,
        LocalV5Type provides type,
        LocalV5Shapes provides shapes,
        LocalV5Spacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = type.toMaterialTypography(),
            shapes = shapes.toMaterialShapes(),
            content = content,
        )
    }
}
