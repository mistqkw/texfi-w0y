package com.texfi.w0y.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember

/**
 * Фаза бесконечной анимации, не вызывающая рекомпозицию.
 *
 * Разница с `rememberInfiniteTransition().animateFloat()` не косметическая.
 * Там значение читается в теле composable, и значит composable пересобирается
 * на каждом кадре — вместе со всеми `Modifier`-лямбдами внутри: у фона из-за
 * этого каждый кадр заново считалась «закэшированная» сетка точек, а у
 * индикатора воспроизведения менялась высота столбиков, то есть список
 * переизмерялся 120 раз в секунду.
 *
 * Здесь возвращается состояние: **читать его нужно внутри лямбды отрисовки**
 * (`Canvas {}`, `drawBehind {}`, `graphicsLayer {}`). Тогда кадр обновляет
 * только слой, минуя композицию и разметку.
 *
 * Второе: [framesPerSecond]. Медленной анимации 120 кадров в секунду не нужно —
 * мерцание за девять секунд на глаз неотличимо от тридцати обновлений, зато
 * приложение перестаёт перерисовываться на каждом vsync просто потому, что на
 * экране есть фон.
 *
 * Часы берутся из [withInfiniteAnimationFrameNanos], а не из `delay`, чтобы
 * анимация останавливалась, когда окно не рисуется, и не будила телефон в фоне.
 *
 * [enabled] = false останавливает часы совсем: фаза замирает, и кадровый
 * колбэк не заводится. Нужно там, где анимацию можно выключить настройкой.
 */
@Composable
fun rememberAnimationPhase(
    periodMillis: Int,
    framesPerSecond: Int = 30,
    enabled: Boolean = true,
): FloatState {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMillis, framesPerSecond, enabled) {
        if (!enabled) return@LaunchedEffect
        val period = periodMillis * 1_000_000L
        val step = 1_000_000_000L / framesPerSecond
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (nanos - last >= step) {
                    last = nanos
                    // Фаза считается от абсолютного времени, а не набегает
                    // шагами: пропущенный тик тогда не сдвигает анимацию.
                    phase.floatValue = (nanos % period) / period.toFloat() * TWO_PI
                }
            }
        }
    }
    return phase
}

const val TWO_PI = 6.2831855f
