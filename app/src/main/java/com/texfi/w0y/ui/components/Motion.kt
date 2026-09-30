package com.texfi.w0y.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.floor

/**
 * Язык движения w0y — один на всё приложение.
 *
 * Он взят из промо-ролика: движение идёт ступенями, а не плавной кривой.
 * Прогресс делится на несколько шагов, элемент вылетает за 150–250 мс
 * с небольшим перелётом, цветовые блоки меняются резко. Все длительности,
 * числа шагов и кривые живут здесь; экраны берут их отсюда и своих не
 * заводят, чтобы стиль настраивался в одном месте.
 *
 * Ступени дёшевы: это та же анимация значения, только с квантованием
 * прогресса, — ни размытий, ни теней в реальном времени.
 */
object W0yMotion {
    /** Быстрые переходы: листание вбок, закрытие, отклик. */
    const val FAST_MS = 160

    /** Обычные: появление элементов, открытие слоёв. */
    const val MID_MS = 220

    /** Редкие крупные: раскрытие плеера. */
    const val SLOW_MS = 260

    /** Число ступеней обычного движения. */
    const val STEPS = 6

    /** Число ступеней для больших перемещений — иначе шаг виден как рывок. */
    const val WIDE_STEPS = 10

    /** Задержка между элементами «лесенки». */
    const val STAGGER_MS = 22L

    val Step: Easing = SteppedEasing(STEPS)
    val StepBack: Easing = SteppedEasing(STEPS, overshoot = true)
    val StepWide: Easing = SteppedEasing(WIDE_STEPS)
    val StepWideBack: Easing = SteppedEasing(WIDE_STEPS, overshoot = true)

    /** Нажатие: три ступени, чтобы кнопка «щёлкала», а не мялась. */
    val Press: Easing = SteppedEasing(3)
}

/**
 * Квантованный прогресс. [overshoot] добавляет перелёт на 70% — как у
 * вылетающих блоков ролика: цель пересекается и возвращается.
 */
@Immutable
class SteppedEasing(
    private val steps: Int,
    private val overshoot: Boolean = false,
) : Easing {
    override fun transform(fraction: Float): Float {
        if (fraction >= 1f) return 1f
        val stepped = floor(fraction * steps) / steps
        if (!overshoot) return stepped
        val p = stepped - 1f
        return 1f + (BACK + 1f) * p * p * p + BACK * p * p
    }

    private companion object {
        const val BACK = 1.70158f
    }
}

/** Квантует значение 0..1 на [steps] ступеней — для индикаторов и счётчиков. */
fun steppedProgress(progress: Float, steps: Int): Float {
    if (progress >= 1f) return 1f
    return floor(progress.coerceAtLeast(0f) * steps) / steps
}

/**
 * Появление «лесенкой»: элемент выезжает снизу ступенями с лёгким перелётом.
 *
 * Значение читается только в слое отрисовки, поэтому содержимое при
 * анимации не пересобирается. Индекс задаёт задержку — элементы приходят
 * по очереди, а не все разом.
 */
@Composable
fun Stagger(
    index: Int,
    modifier: Modifier = Modifier,
    stepMs: Long = W0yMotion.STAGGER_MS,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepMs)
        progress.animateTo(1f, tween(W0yMotion.MID_MS, easing = W0yMotion.StepBack))
    }
    Box(
        modifier.graphicsLayer {
            alpha = (progress.value * 3f).coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 26.dp.toPx()
        },
    ) { content() }
}

/**
 * Пиксельная «шторка» при смене вкладки: мозаика квадратов цвета акцента
 * и второго цвета, которая рассыпается за [W0yMotion.FAST_MS].
 *
 * Новый экран стоит на месте с первого кадра — шторка лежит поверх и
 * только убирается, поэтому переход не добавляет ожидания. Рисуется
 * в фазе отрисовки одной канвой, без пересборки.
 */
@Composable
fun PixelCurtain(
    trigger: Any,
    first: Color,
    second: Color,
    modifier: Modifier = Modifier,
) {
    val cover = remember { Animatable(0f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        // На первом показе шторка не нужна: переход — это смена, а не запуск.
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        cover.snapTo(CURTAIN_START)
        cover.animateTo(0f, tween(W0yMotion.FAST_MS, easing = SteppedEasing(8)))
    }
    Canvas(modifier.fillMaxSize()) {
        val level = cover.value
        if (level <= 0f) return@Canvas
        val cell = size.width / CURTAIN_COLUMNS
        val rows = (size.height / cell).toInt() + 1
        for (iy in 0 until rows) {
            for (ix in 0 until CURTAIN_COLUMNS) {
                val h = hash(ix, iy)
                if (h < level) {
                    drawRect(
                        color = if (h * 7f % 1f < 0.5f) first else second,
                        topLeft = Offset(ix * cell, iy * cell),
                        size = Size(cell + 1f, cell + 1f),
                    )
                }
            }
        }
    }
}

private const val CURTAIN_COLUMNS = 8
private const val CURTAIN_START = 0.8f

private fun hash(x: Int, y: Int): Float {
    var n = x * 374_761_393 + y * 668_265_263
    n = (n xor (n ushr 13)) * 1_274_126_177
    return ((n xor (n ushr 16)) and 0xFFFF) / 65_536f
}
