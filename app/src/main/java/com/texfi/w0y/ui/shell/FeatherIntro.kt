package com.texfi.w0y.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Заставка: перо с иконки падает и втыкается.
 *
 * Берётся сам файл иконки, а не похожая нарисованная фигура: на экране
 * должно приземлиться ровно то перо, которое человек нажал на рабочем
 * столе. Всё вместе укладывается в секунду — запуск важнее эффекта, и
 * анимация не ждёт, пока её досмотрят: приложение под ней уже готово.
 */
@Composable
fun FeatherIntro(onFinished: () -> Unit) {
    val colors = LocalW0yColors.current
    val drop = remember { Animatable(START_HEIGHT) }
    val tilt = remember { Animatable(START_TILT) }
    val line = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { tilt.animateTo(0f, tween(durationMillis = 620, easing = EaseOutBack)) }
        // Пружина вместо ровного хода: перо должно воткнуться, а не съехать.
        drop.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessLow),
        )
        line.animateTo(1f, tween(200))
        delay(140)
        fade.animateTo(0f, tween(240))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(300.dp)
                        .graphicsLayer {
                            translationY = drop.value
                            rotationZ = tilt.value
                        },
            )
            Spacer(Modifier.height(0.dp))
            // Планка под пером — та же, что под заголовками экранов:
            // заставка заканчивается тем, с чего начинается интерфейс.
            Box(
                Modifier
                    .width((70 * line.value).dp)
                    .height(3.dp)
                    .background(colors.accent),
            )
        }
    }
}

/** Высота падения в пикселях слоя: примерно треть экрана на любом телефоне. */
private const val START_HEIGHT = -620f
private const val START_TILT = -22f
