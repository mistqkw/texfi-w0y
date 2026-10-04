package com.texfi.w0y.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.UiStyle

/**
 * Всё, чем стили отличаются друг от друга, — в одном месте.
 *
 * Экраны не знают, какой стиль включён: они берут карточку, кнопку,
 * переключатель и иконку по имени, а форма, рамка, тень и движение
 * приходят отсюда. Поэтому второй стиль не дублирует ни одного экрана, и
 * новый экран сразу рисуется в обоих.
 */
@Immutable
data class StyleTokens(
    val style: UiStyle,
    /** Карточки, панели, плитки. */
    val card: Shape,
    /** Толщина рамки карточки; 0 — без рамки. */
    val cardBorder: Dp,
    /** Смещённая тень без размытия; 0 — тени нет. */
    val cardShadow: Dp,
    val button: Shape,
    val buttonBorder: Dp,
    val buttonShadow: Dp,
    /** Мелкие элементы: чипы, сегменты, бейджи. */
    val chip: Shape,
    /** Обложки в строках и плитках. */
    val cover: Shape,
    /** Нижние панели и диалоги. */
    val sheet: Shape,
    /** Плавающая навигация и мини-плеер. */
    val bar: Shape,
    val switchTrack: Shape,
    val switchThumb: Shape,
    val sliderThumb: Shape,
    /** Насколько уменьшается нажатый элемент вместо вдавливания в тень. */
    val pressScale: Float,
    /** Ступенчатое движение (Pixel) или плавное (Smooth). */
    val stepped: Boolean,
    /** Сегментный прогресс (Pixel) или сплошная полоса (Smooth). */
    val segmented: Boolean,
)

val PixelTokens =
    StyleTokens(
        style = UiStyle.PIXEL,
        card = RoundedCornerShape(8.dp),
        cardBorder = 2.dp,
        cardShadow = 4.dp,
        button = RoundedCornerShape(8.dp),
        buttonBorder = 2.dp,
        buttonShadow = 4.dp,
        chip = RectangleShape,
        cover = RoundedCornerShape(4.dp),
        sheet = RectangleShape,
        bar = RectangleShape,
        switchTrack = RectangleShape,
        switchThumb = RectangleShape,
        sliderThumb = RectangleShape,
        pressScale = 1f,
        stepped = true,
        segmented = true,
    )

val SmoothTokens =
    StyleTokens(
        style = UiStyle.SMOOTH,
        card = RoundedCornerShape(22.dp),
        cardBorder = 0.dp,
        cardShadow = 0.dp,
        button = RoundedCornerShape(50),
        buttonBorder = 0.dp,
        buttonShadow = 0.dp,
        chip = RoundedCornerShape(50),
        cover = RoundedCornerShape(12.dp),
        sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        bar = RoundedCornerShape(28.dp),
        switchTrack = RoundedCornerShape(50),
        switchThumb = CircleShape,
        sliderThumb = CircleShape,
        pressScale = 0.96f,
        stepped = false,
        segmented = false,
    )

/** Токены стиля; ветка на каждый стиль обязательна — компилятор не даст забыть новый. */
fun tokensFor(style: UiStyle): StyleTokens =
    when (style) {
        UiStyle.PIXEL -> PixelTokens
        UiStyle.SMOOTH -> SmoothTokens
    }

val LocalStyleTokens = staticCompositionLocalOf { PixelTokens }

/** Короткий доступ из компонентов. */
val styleTokens: StyleTokens
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current

val isSmooth: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current.style == UiStyle.SMOOTH
