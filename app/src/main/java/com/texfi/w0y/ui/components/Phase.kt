package com.texfi.w0y.ui.components

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.currentStateAsState
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
 * Между тиками — сон, а не подписка на каждый кадр экрана: раньше цикл
 * просыпался на каждый vsync (60–120 раз в секунду), чтобы обновить фазу
 * 12–30 раз. Часы идут, только пока экран на переднем плане: в фоне и при
 * выключенном экране они стоят и не будят телефон.
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
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateAsState()
    val visible = state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    LaunchedEffect(periodMillis, framesPerSecond, enabled, visible) {
        if (!enabled || !visible) return@LaunchedEffect
        val period = periodMillis * 1_000_000L
        val stepMs = 1000L / framesPerSecond
        while (true) {
            val nanos = System.nanoTime()
            // Фаза считается от абсолютного времени, а не набегает шагами:
            // пропущенный тик тогда не сдвигает анимацию.
            phase.floatValue = (nanos % period) / period.toFloat() * TWO_PI
            kotlinx.coroutines.delay(stepMs)
        }
    }
    return phase
}

const val TWO_PI = 6.2831855f
