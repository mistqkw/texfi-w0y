package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors

/** Вопрос с подтверждением поверх экрана — в стиле карточки текущего вида. */
@Composable
fun ConfirmPanel(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.shadow.copy(alpha = 0.85f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(
            label = title,
            modifier =
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 480.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                PixelButton(dismiss, onClick = onDismiss, fill = colors.surfaceHigh)
                PixelButton(confirm, onClick = onConfirm)
            }
        }
    }
}
