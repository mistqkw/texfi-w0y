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
    /** Стеклянная тема: скругления, полупрозрачность, без офсетной тени. */
    val glass: Boolean = false,
)

/**
 * Тёмная — графит, а не чернота: карточки заметно светлее фона. Раньше
 * фон был #0D0D11, и на экране телефона тёмная тема от чёрной не
 * отличалась ничем — это было замечено первым же посторонним тестером.
 */
private val DarkColors =
    W0yColors(
        background = Color(0xFF15151B),
        surface = Color(0xFF1F1F27),
        surfaceHigh = Color(0xFF2A2A34),
        border = Color(0xFF383845),
        shadow = Color(0xFF0A0A0E),
        accent = TexFiBlue,
        accentDeep = TexFiBlueDeep,
        secondary = W0yAmber,
        text = W0yText,
        textMuted = W0yTextMuted,
    )

/**
 * Чёрная для AMOLED: чёрный не только фон, но и карточки. Серые подложки
 * держат пиксели включёнными и съедают весь смысл темы; карточку здесь
 * очерчивает только рамка, а тень — тонкая ступенька цвета рамки.
 */
private val OledColors =
    DarkColors.copy(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceHigh = Color(0xFF101014),
        border = Color(0xFF2C2C36),
        shadow = Color(0xFF17171D),
    )

/**
 * Стекло — экспериментальная тема для тех, кому пиксельной резкости мало.
 * Поверхности полупрозрачные поверх глубокого фона с цветными пятнами,
 * рамка — светлый блик сверху. Чужой для TexFi язык, поэтому спрятана.
 */
private val GlassColors =
    W0yColors(
        background = Color(0xFF0A0E1C),
        surface = Color(0x14FFFFFF),
        surfaceHigh = Color(0x24FFFFFF),
        border = Color(0x33FFFFFF),
        shadow = Color(0x00000000),
        accent = TexFiBlue,
        accentDeep = TexFiBlueDeep,
        secondary = W0yAmber,
        text = W0yText,
        textMuted = Color(0xFFA3A8BC),
        glass = true,
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
            ThemeMode.GLASS -> GlassColors
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
            // Material-компоненты (диалоги, меню) полупрозрачными быть не
            // должны: у них нет своего фона, и сквозь них читался бы экран.
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.background,
                secondary = colors.secondary,
                background = colors.background,
                onBackground = colors.text,
                surface = if (colors.glass) GlassOpaqueSurface else colors.surface,
                onSurface = colors.text,
                surfaceVariant = if (colors.glass) GlassOpaqueSurface else colors.surfaceHigh,
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

private val GlassOpaqueSurface = Color(0xFF1A2036)
