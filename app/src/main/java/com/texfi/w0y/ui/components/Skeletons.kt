package com.texfi.w0y.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Скелетон строки трека: форма будущего списка вместо пустоты со спиннером.
 * Пульсация медленная (900 мс) — быстрая на пиксельной графике читается
 * как мигание ошибки.
 */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.surfaceHigh),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.surface),
            )
        }
    }
}
