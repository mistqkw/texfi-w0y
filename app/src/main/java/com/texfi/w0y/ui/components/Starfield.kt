package com.texfi.w0y.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Фон экосистемы: точечная сетка и редкие звёзды, как на сайте TexFi.
 *
 * Рисуется под всем интерфейсом и виден только в промежутках между
 * карточками — ровно так же, как на сайте: фон не спорит с содержимым,
 * но чёрный перестаёт быть просто пустотой.
 *
 * Звёзды расставлены по фиксированному зерну: случайные при каждом входе
 * прыгали бы с экрана на экран, и это читалось бы как мусор на матрице.
 */
@Composable
fun Starfield(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val stars = remember { generateStars() }
    val transition = rememberInfiniteTransition(label = "starfield")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Restart),
        label = "twinkle",
    )

    Box(modifier) {
        // Сетка и свечение вынесены в отдельный слой намеренно: они не
        // зависят от фазы мерцания, и на общем узле пересчитывались бы
        // каждый кадр — четыре сотни точек впустую при каждом кадре
        // анимации. Плавность интерфейса здесь дороже краткости кода.
        Box(
            Modifier.matchParentSize().drawWithCache {
                val glow =
                    Brush.radialGradient(
                        colors = listOf(colors.accent.copy(alpha = GLOW_ALPHA), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.9f,
                    )
                onDrawBehind {
                    drawRect(glow)
                    drawDotGrid(colors.border.copy(alpha = GRID_ALPHA), GRID_STEP_PX)
                }
            },
        )
        Canvas(Modifier.matchParentSize()) {
            stars.forEach { star ->
                // Мерцание пологое: резкое моргание на пиксельной графике
                // читается как ошибка отрисовки, а не как звезда.
                val brightness = 0.35f + 0.65f * abs(sin(phase * star.speed + star.offset))
                drawCircle(
                    color = if (star.accent) colors.accent else Color.White,
                    radius = star.radius * density,
                    center = Offset(star.x * size.width, star.y * size.height),
                    alpha = star.alpha * brightness,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDotGrid(color: Color, stepPx: Float) {
    val step = stepPx * density
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(color = color, radius = 1f * density, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val speed: Float,
    val offset: Float,
    val accent: Boolean,
)

private fun generateStars(): List<Star> {
    val random = Random(STAR_SEED)
    return List(STAR_COUNT) {
        Star(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radius = 0.7f + random.nextFloat() * 0.9f,
            alpha = 0.18f + random.nextFloat() * 0.3f,
            speed = 0.6f + random.nextFloat(),
            offset = random.nextFloat() * 6f,
            // Несколько синих среди белых — тот же приём, что на сайте.
            accent = random.nextFloat() < 0.22f,
        )
    }
}

private const val STAR_SEED = 0x7E5F1
private const val STAR_COUNT = 46
private const val GRID_ALPHA = 0.30f

/** Свечение под заголовком: тот же приём, что у героя на сайте. */
private const val GLOW_ALPHA = 0.10f
private val GRID_STEP_PX = 26.dp.value
