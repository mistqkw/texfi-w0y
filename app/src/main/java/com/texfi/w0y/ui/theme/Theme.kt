package com.texfi.w0y.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Токены, которых нет в Material: пиксельная граница, офсетная тень и
 * собственный акцент приложения. Material-схема остаётся для компонентов,
 * которые берём готовыми, всё пиксельное читает эти значения.
 */
@Immutable
data class W0yColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val shadow: Color,
    val accent: Color,
    val accentDeep: Color,
    val secondary: Color,
    val text: Color,
    val textMuted: Color,
)

val LocalW0yColors = staticCompositionLocalOf {
    W0yColors(
        background = W0yBlack,
        surface = W0ySurface,
        surfaceHigh = W0ySurfaceHigh,
        border = W0yBorder,
        shadow = Color(0xFF05050A),
        accent = TexFiBlue,
        accentDeep = TexFiBlueDeep,
        secondary = W0yAmber,
        text = W0yText,
        textMuted = W0yTextMuted,
    )
}

private val DarkScheme = darkColorScheme(
    primary = TexFiBlue,
    onPrimary = W0yBlack,
    secondary = W0yAmber,
    background = W0yBlack,
    onBackground = W0yText,
    surface = W0ySurface,
    onSurface = W0yText,
    surfaceVariant = W0ySurfaceHigh,
    onSurfaceVariant = W0yTextMuted,
    outline = W0yBorder,
    error = W0yDanger,
)

@Composable
fun W0yTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalW0yColors provides LocalW0yColors.current) {
        MaterialTheme(
            colorScheme = DarkScheme,
            typography = W0yTypography,
            content = content,
        )
    }
}
