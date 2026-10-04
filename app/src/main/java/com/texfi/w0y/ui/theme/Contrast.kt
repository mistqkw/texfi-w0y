package com.texfi.w0y.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Контраст по WCAG: (L1 + 0.05) / (L2 + 0.05), от 1 до 21.
 *
 * Обычный текст должен давать не меньше 4.5:1. Свой цвет акцента человек
 * выбирает любой, поэтому подписи на нём и текст его цветом не задаются
 * руками, а выводятся отсюда.
 */
object Contrast {
    const val TEXT = 4.5f

    fun ratio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** Чёрный или белый — что читается на [background] лучше. */
    fun on(background: Color, dark: Color = Color(0xFF111116), light: Color = Color.White): Color =
        if (ratio(dark, background) >= ratio(light, background)) dark else light

    /**
     * [color], сдвинутый к чёрному или белому ровно настолько, чтобы на
     * [background] получилось не меньше [target]. Уже читаемый цвет не
     * трогается — оттенок акцента остаётся узнаваемым.
     */
    fun ensure(color: Color, background: Color, target: Float = TEXT): Color {
        if (ratio(color, background) >= target) return color
        val toward = if (background.luminance() > 0.4f) Color.Black else Color.White
        var lo = 0f
        var hi = 1f
        repeat(16) {
            val mid = (lo + hi) / 2
            if (ratio(lerp(color, toward, mid), background) >= target) hi = mid else lo = mid
        }
        return lerp(color, toward, hi)
    }
}
