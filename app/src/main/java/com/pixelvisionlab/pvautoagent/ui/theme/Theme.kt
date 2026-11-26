// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.ui.theme

import android.app.Activity
import com.pixelvisionlab.pvautoagent.settings.AppThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark color scheme: near-black background + near-white primary action (buttons) + blue functional accent
private val DarkColorScheme = darkColorScheme(
    primary = Neutral50,          // Primary button/Material fill: near-white in dark mode, with dark text
    onPrimary = Neutral950,
    primaryContainer = Neutral800,
    onPrimaryContainer = Neutral50,
    secondary = Neutral400,
    onSecondary = Neutral900,
    secondaryContainer = Neutral800,
    onSecondaryContainer = Neutral300,
    tertiary = AccentBlue,        // Functional accent blue
    onTertiary = Neutral0,
    tertiaryContainer = AccentBlueContainerDark,
    onTertiaryContainer = AccentBlue,
    background = PVAutoAgentBackground,
    surface = PVAutoAgentSurface,
    surfaceVariant = Neutral800,
    surfaceDim = Neutral950,
    surfaceBright = Neutral800,
    surfaceContainerLowest = Neutral950,
    surfaceContainerLow = Neutral850,
    surfaceContainer = Neutral850,
    surfaceContainerHigh = Neutral800,
    surfaceContainerHighest = Neutral750,
    surfaceTint = Color.Transparent,
    onBackground = PVAutoAgentOnBackground,
    onSurface = PVAutoAgentOnSurface,
    onSurfaceVariant = Neutral400,
    outline = Neutral700,
    outlineVariant = Neutral750,
    error = StatusError,
    onError = Neutral0,
    errorContainer = StatusErrorContainerDark,
    onErrorContainer = StatusOnErrorContainerDark,
)

// Light color scheme: minimal light background + graphite-black primary action (buttons) + blue functional accent
private val LightColorScheme = lightColorScheme(
    primary = Neutral900,         // Primary button/accent: graphite black
    onPrimary = Neutral0,
    primaryContainer = Neutral200,
    onPrimaryContainer = Neutral900,
    secondary = Neutral600,
    onSecondary = Neutral0,
    secondaryContainer = Neutral100,
    onSecondaryContainer = Neutral700,
    tertiary = AccentBlue,        // Functional accent blue
    onTertiary = Neutral0,
    tertiaryContainer = AccentBlueContainerLight,
    onTertiaryContainer = AccentBlueVariant,
    // Canvas < card < floating layer each step up only slightly; for the LightFloating floating layer see pvautoagentFloatingSurfaceColor.
    background = LightCanvas,
    surface = LightCard,
    surfaceVariant = LightContainerHigh,
    surfaceDim = LightContainerHigh,
    surfaceBright = LightFloating,
    surfaceContainerLowest = LightFloating,
    surfaceContainerLow = LightContainerLow,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHighest,
    surfaceTint = Color.Transparent,
    onBackground = Neutral900,
    onSurface = Neutral900,
    onSurfaceVariant = Neutral500,
    outline = LightContainerHighest,
    outlineVariant = LightContainerHigh,
    error = StatusError,
    onError = Neutral0,
)

/**
 * Lets child components know whether dark mode is currently active (used for glass effects and similar)
 */
val LocalAppIsDark = staticCompositionLocalOf { false }

/**
 * PVAutoAgent theme
 * Consumes the theme mode supplied by the settings repository.
 */
@Composable
fun PVAutoAgentTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    // Configure the status bar style
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    // When Material3 does not override it, the text selection highlight defaults to colorScheme.primary — in the light theme primary is graphite black
    // (see LightColorScheme above), so selected text looks like a block of deep black was painted over it — both dark and ugly. Selection is in fact already named as
    // one of the intended uses of AccentBlue in its comment, so here it is explicitly overridden to a blue semi-transparent highlight that looks consistent and good in both dark and light themes.
    val selectionColors = TextSelectionColors(
        handleColor = AccentBlue,
        backgroundColor = AccentBlue.copy(alpha = 0.35f)
    )

    CompositionLocalProvider(
        LocalAppIsDark provides isDark,
        LocalAppMotionEnabled provides rememberAppMotionEnabled(),
        LocalTextSelectionColors provides selectionColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(isDark) { pvautoagentTypography(isDark) },
            shapes = PVAutoAgentShapes,
            content = content
        )
    }
}
