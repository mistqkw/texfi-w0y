package com.texfi.w0y.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
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
 *
 * [animated] выключает мерцание: тогда фон рисуется один раз и приложение в
 * покое не отдаёт кадров вообще. Это настройка «живой фон» из раздела «вид».
 */
@Composable
fun Starfield(modifier: Modifier = Modifier, animated: Boolean = true) {
    val colors = LocalW0yColors.current
    val stars = remember { generateStars() }
    // Фаза читается только внутри лямбды отрисовки (см. rememberAnimationPhase):
    // иначе весь Starfield пересобирался бы на каждом кадре, и «закэшированная»
    // сетка из четырёх сотен точек пересчитывалась бы вместе с ним.
    val phase = rememberAnimationPhase(TWINKLE_PERIOD_MS, TWINKLE_FPS, enabled = animated)

    Box(modifier) {
        // Сетка и свечение — отдельным слоем: они не зависят от фазы, и этот
        // слой не перерисовывается вообще, пока не поменялся размер или тема.
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
            // В статичном режиме фаза не читается — и канва после первого
            // кадра больше не перерисовывается.
            val current = if (animated) phase.floatValue else STILL_PHASE
            stars.forEach { star ->
                // Мерцание пологое: резкое моргание на пиксельной графике
                // читается как ошибка отрисовки, а не как звезда.
                val brightness = 0.35f + 0.65f * abs(sin(current * star.speed + star.offset))
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

/** Полный цикл мерцания. Медленный намеренно: быстрый читается как помеха. */
private const val TWINKLE_PERIOD_MS = 9_000

/**
 * Кадров в секунду у мерцания. За девять секунд синус проходит полный круг —
 * двенадцати обновлений хватает, а бюджет кадра остаётся скроллу.
 */
private const val TWINKLE_FPS = 12

/** Фаза статичного фона: подобрана так, чтобы звёзды не были все тусклыми. */
private const val STILL_PHASE = 1.1f

/** Свечение под заголовком: тот же приём, что у героя на сайте. */
private const val GLOW_ALPHA = 0.10f
private val GRID_STEP_PX = 26.dp.value
