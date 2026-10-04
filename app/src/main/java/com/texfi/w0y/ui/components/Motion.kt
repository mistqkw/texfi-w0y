package com.texfi.w0y.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
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

    /** Ступени включены (Pixel) или нет (Smooth). Ставит тема. */
    @Volatile
    var stepped: Boolean = true
}

private val SmoothCurve = androidx.compose.animation.core.FastOutSlowInEasing

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
        // В плавном стиле те же анимации идут без ступеней: кривые одни на
        // всё приложение, поэтому переключаются здесь, а не на каждом экране.
        if (!W0yMotion.stepped) return SmoothCurve.transform(fraction)
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
 * Спокойное появление экрана при смене вкладки: он проявляется и чуть
 * поднимается в четыре ступени за 140 мс.
 *
 * Без цветных блоков и вспышек: переход не должен отвлекать — он только
 * показывает, что экран сменился. Новый экран стоит на месте с первого
 * кадра и уже виден (не меньше трети яркости), поэтому ожидания нет.
 * Значение читается в слое отрисовки, содержимое не пересобирается.
 */
@Composable
fun Modifier.softEnter(trigger: Any): Modifier {
    val progress = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        // На первом показе переход не нужен: это запуск, а не смена.
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ENTER_MS, easing = SteppedEasing(ENTER_STEPS)))
    }
    return graphicsLayer {
        alpha = ENTER_MIN_ALPHA + (1f - ENTER_MIN_ALPHA) * progress.value
        translationY = (1f - progress.value) * ENTER_RISE.toPx()
    }
}

private const val ENTER_MS = 140
private const val ENTER_STEPS = 4
private const val ENTER_MIN_ALPHA = 0.35f
private val ENTER_RISE = 10.dp
