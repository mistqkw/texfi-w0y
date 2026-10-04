package com.texfi.w0y.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * «Жидкое стекло» плавного стиля.
 *
 * Поверхность полупрозрачная — сквозь неё виден фон цвета обложки; по
 * верхней кромке яркий блик, к низу он гаснет — так читается толщина
 * стекла; в верхнем углу мягкий отсвет. Всё это градиенты, посчитанные
 * один раз на размер: размытия в реальном времени нет, и скорость
 * отрисовки та же, что у плоской карточки. В матовом варианте Smooth
 * поверхность просто плотная.
 */
@Composable
fun Modifier.liquidGlass(shape: Shape, tint: Color? = null): Modifier {
    val colors = LocalW0yColors.current
    val light = colors.background.luminance() > 0.5f
    val base = tint ?: colors.surface
    // Матовый вариант Smooth: та же форма, плотная заливка без бликов.
    if (!LocalStyleTokens.current.glass) return clip(shape).background(base)
    val fill = base.copy(alpha = if (light) 0.72f else 0.62f)
    val rimTop = Color.White.copy(alpha = if (light) 0.9f else 0.32f)
    val rimBottom = Color.White.copy(alpha = if (light) 0.25f else 0.04f)
    val sheen = Color.White.copy(alpha = if (light) 0.35f else 0.07f)
    return clip(shape)
        .background(fill)
        .drawWithCache {
            val gloss =
                Brush.radialGradient(
                    colors = listOf(sheen, Color.Transparent),
                    center = Offset(size.width * 0.15f, 0f),
                    radius = maxOf(size.width, size.height) * 0.9f,
                )
            val fall = Brush.verticalGradient(listOf(Color.White.copy(alpha = sheen.alpha * 0.6f), Color.Transparent), endY = size.height * 0.5f)
            onDrawBehind {
                drawRect(gloss)
                drawRect(fall)
            }
        }.border(1.dp, Brush.verticalGradient(listOf(rimTop, rimBottom)), shape)
}

/**
 * Подложка элемента по стилю: в Pixel — заливка с рамкой и квадратными
 * углами, в Smooth — стекло с крупным скруглением.
 */
@Composable
fun Modifier.styledSurface(corner: Int, color: Color = LocalW0yColors.current.surface): Modifier =
    if (isSmooth) {
        liquidGlass(androidx.compose.foundation.shape.RoundedCornerShape(minOf(corner * 5 / 2 + 6, 24).dp), color)
    } else {
        styledClip(corner).background(color).styledBorder(corner)
    }
