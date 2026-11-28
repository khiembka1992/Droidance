// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.theme

import android.app.Activity
import com.droidance.settings.AppThemeMode
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

// Bộ màu tối: nền gần đen + thao tác chính gần trắng (nút) + nhấn mạnh chức năng màu xanh
private val DarkColorScheme = darkColorScheme(
    primary = Neutral50,          // Nút chính/fill Material: ở nền tối dùng gần trắng, chữ màu tối
    onPrimary = Neutral950,
    primaryContainer = Neutral800,
    onPrimaryContainer = Neutral50,
    secondary = Neutral400,
    onSecondary = Neutral900,
    secondaryContainer = Neutral800,
    onSecondaryContainer = Neutral300,
    tertiary = AccentBlue,        // Xanh nhấn mạnh chức năng
    onTertiary = Neutral0,
    tertiaryContainer = AccentBlueContainerDark,
    onTertiaryContainer = AccentBlue,
    background = DroidanceBackground,
    surface = DroidanceSurface,
    surfaceVariant = Neutral800,
    surfaceDim = Neutral950,
    surfaceBright = Neutral800,
    surfaceContainerLowest = Neutral950,
    surfaceContainerLow = Neutral850,
    surfaceContainer = Neutral850,
    surfaceContainerHigh = Neutral800,
    surfaceContainerHighest = Neutral750,
    surfaceTint = Color.Transparent,
    onBackground = DroidanceOnBackground,
    onSurface = DroidanceOnSurface,
    onSurfaceVariant = Neutral400,
    outline = Neutral700,
    outlineVariant = Neutral750,
    error = StatusError,
    onError = Neutral0,
    errorContainer = StatusErrorContainerDark,
    onErrorContainer = StatusOnErrorContainerDark,
)

// Bộ màu sáng: nền sáng tối giản + thao tác chính đen than chì (nút) + nhấn mạnh chức năng màu xanh
private val LightColorScheme = lightColorScheme(
    primary = Neutral900,         // Nút chính/nhấn mạnh: đen than chì
    onPrimary = Neutral0,
    primaryContainer = Neutral200,
    onPrimaryContainer = Neutral900,
    secondary = Neutral600,
    onSecondary = Neutral0,
    secondaryContainer = Neutral100,
    onSecondaryContainer = Neutral700,
    tertiary = AccentBlue,        // Xanh nhấn mạnh chức năng
    onTertiary = Neutral0,
    tertiaryContainer = AccentBlueContainerLight,
    onTertiaryContainer = AccentBlueVariant,
    // Canvas < card < lớp phủ sáng dần từng bậc nhỏ; lớp phủ LightFloating xem tại droidanceFloatingSurfaceColor.
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
 * Cho component con biết hiện có đang ở chế độ tối hay không (dùng cho hiệu ứng kính, v.v.)
 */
val LocalAppIsDark = staticCompositionLocalOf { false }

/**
 * Giao diện Droidance
 * Consumes the theme mode supplied by the settings repository.
 */
@Composable
fun DroidanceTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    // Đặt kiểu thanh trạng thái
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    // Khi Material3 không ghi đè, màu nhấn chọn văn bản mặc định kế thừa colorScheme.primary — ở giao diện sáng primary là đen than chì
    // (xem LightColorScheme ở trên), chữ được chọn trông như bị bôi một mảng đen đậm, vừa tối vừa xấu. Việc chọn vốn đã là một trong
    // những mục đích được ghi rõ trong comment của AccentBlue, nên ở đây ghi đè tường minh thành nhấn xanh bán trong suốt, cả giao diện sáng lẫn tối đều đồng nhất và dễ nhìn.
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
            typography = remember(isDark) { droidanceTypography(isDark) },
            shapes = DroidanceShapes,
            content = content
        )
    }
}
