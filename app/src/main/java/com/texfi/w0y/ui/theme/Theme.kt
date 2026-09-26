package com.texfi.w0y.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.ThemeMode

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

private val DarkColors =
    W0yColors(
        background = Color(0xFF0D0D11),
        surface = Color(0xFF16161C),
        surfaceHigh = Color(0xFF20202A),
        border = Color(0xFF2E2E3A),
        shadow = Color(0xFF05050A),
        accent = TexFiBlue,
        accentDeep = TexFiBlueDeep,
        secondary = W0yAmber,
        text = W0yText,
        textMuted = W0yTextMuted,
    )

/** Чёрная тема для AMOLED: фон именно #000000, иначе смысла нет. */
private val OledColors =
    DarkColors.copy(
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0D),
        surfaceHigh = Color(0xFF14141A),
        shadow = Color(0xFF000000),
    )

/**
 * Светлая тема тёплая, а не бело-серая: голый Material-белый выглядит как
 * дефолт фреймворка, а в TexFi светлая тема всегда с тёплой бумагой.
 */
private val LightColors =
    W0yColors(
        background = Color(0xFFF7F1E4),
        surface = Color(0xFFFFFDF7),
        surfaceHigh = Color(0xFFEDE4D2),
        border = Color(0xFFD5C7AC),
        shadow = Color(0xFFC9B99B),
        accent = TexFiBlue,
        accentDeep = TexFiBlueDeep,
        secondary = Color(0xFFD9822B),
        text = Color(0xFF191921),
        textMuted = Color(0xFF6B6455),
    )

val LocalW0yColors = staticCompositionLocalOf { DarkColors }

@Composable
fun W0yTheme(
    mode: ThemeMode = ThemeMode.DARK,
    accent: Accent = Accent.BLUE,
    content: @Composable () -> Unit,
) {
    val base =
        when (mode) {
            ThemeMode.DARK -> DarkColors
            ThemeMode.OLED -> OledColors
            ThemeMode.LIGHT -> LightColors
        }
    // Схема меняет только пару акцентов: фон, поверхности и текст остаются
    // от темы. Иначе «розовая» схема на светлой теме превращалась бы
    // в отдельную, четвёртую тему, которую никто не проверял.
    val colors =
        if (accent == Accent.BLUE) {
            base
        } else {
            base.copy(
                accent = Color(accent.accent),
                accentDeep = Color(accent.deep),
                secondary = Color(accent.secondary),
            )
        }
    val scheme =
        if (mode == ThemeMode.LIGHT) {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = Color.White,
                secondary = colors.secondary,
                background = colors.background,
                onBackground = colors.text,
                surface = colors.surface,
                onSurface = colors.text,
                surfaceVariant = colors.surfaceHigh,
                onSurfaceVariant = colors.textMuted,
                outline = colors.border,
                error = W0yDanger,
            )
        } else {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.background,
                secondary = colors.secondary,
                background = colors.background,
                onBackground = colors.text,
                surface = colors.surface,
                onSurface = colors.text,
                surfaceVariant = colors.surfaceHigh,
                onSurfaceVariant = colors.textMuted,
                outline = colors.border,
                error = W0yDanger,
            )
        }

    CompositionLocalProvider(LocalW0yColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = W0yTypography,
            content = content,
        )
    }
}
