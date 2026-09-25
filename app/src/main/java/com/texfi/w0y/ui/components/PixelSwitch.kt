package com.texfi.w0y.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Квадратный переключатель: рамка и заливка без скруглений и без
 * «морфинга» — Material-свитч поверх пиксельной графики выглядит чужим.
 */
@Composable
fun PixelSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalW0yColors.current
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, tween(120), label = "switch")
    Box(
        Modifier
            .width(40.dp)
            .height(22.dp)
            .background(if (checked) colors.accentDeep else colors.surface)
            .border(2.dp, colors.border)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .background(if (checked) colors.accent else colors.textMuted),
        )
    }
}
