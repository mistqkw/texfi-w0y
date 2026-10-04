package com.texfi.w0y.ui.screens

import com.texfi.w0y.ui.theme.styledSurface
import com.texfi.w0y.ui.theme.styledClip
import com.texfi.w0y.ui.theme.styledBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.Accent
import com.texfi.w0y.ui.components.PixelSlider
import com.texfi.w0y.ui.components.SegmentedBar
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel

/**
 * Выбор цветовой схемы по цвету, а не по слову.
 *
 * Ряд квадратных образцов с жёсткой тенью; выбранный обведён и отмечен
 * квадратом. Схема применяется сразу и целиком — сам интерфейс вокруг и
 * есть предпросмотр; маленький блок под рядом показывает кнопку, полосу и
 * чип в выбранной паре цветов. Свой цвет задаётся ползунком оттенка или
 * HEX-кодом.
 */
@Composable
internal fun AccentPicker(
    selected: Accent,
    customArgb: Int,
    onSelect: (Accent) -> Unit,
    onCustom: (Int) -> Unit,
) {
    val colors = LocalW0yColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Accent.entries.forEach { entry ->
                val fill = if (entry == Accent.CUSTOM) Color(customArgb) else Color(entry.accent)
                Swatch(
                    fill = fill,
                    selected = entry == selected,
                    custom = entry == Accent.CUSTOM,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(entry) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(selected.label),
            style = PixelSectionLabel,
            color = colors.accent,
        )
        Spacer(Modifier.height(10.dp))
        Preview()
        if (selected == Accent.CUSTOM) {
            Spacer(Modifier.height(12.dp))
            CustomEditor(customArgb, onCustom)
        }
    }
}

@Composable
private fun Swatch(
    fill: Color,
    selected: Boolean,
    custom: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.aspectRatio(1f).padding(end = 3.dp, bottom = 3.dp)) {
        // Жёсткая тень без размытия — как у всех карточек TexFi.
        if (!com.texfi.w0y.ui.theme.isSmooth) Box(Modifier.matchParent().offset(3.dp, 3.dp).background(colors.shadow))
        Box(
            Modifier
                .matchParent()
                .pressScale(interaction, pressed = 0.9f)
                .background(fill)
                .border(if (selected) 3.dp else 2.dp, if (selected) colors.text else colors.border)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(8.dp).background(colors.text))
            } else if (custom) {
                Text("#", style = PixelSectionLabel, color = colors.background)
            }
        }
    }
}

private fun Modifier.matchParent(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(1f))

/** Предпросмотр пары цветов: главная кнопка, полоса и выбранный чип. */
@Composable
private fun Preview() {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .styledSurface(0)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(colors.accent)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.accent_preview),
                style = PixelSectionLabel,
                color = colors.background,
            )
        }
        Spacer(Modifier.width(12.dp))
        SegmentedBar(
            progress = { 0.6f },
            lit = colors.secondary,
            dim = colors.border,
            modifier = Modifier.weight(1f),
            height = 6.dp,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .border(2.dp, colors.accent)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("ABC", style = MaterialTheme.typography.labelMedium, color = colors.accent)
        }
    }
}

/** Свой цвет: оттенок ползунком и HEX-кодом; обе ручки правят одно значение. */
@Composable
private fun CustomEditor(argb: Int, onCustom: (Int) -> Unit) {
    val colors = LocalW0yColors.current
    val current = Color(argb)
    val hue = remember(argb) { hueOf(current) }
    var text by remember(argb) { mutableStateOf(hex(current)) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.accent_custom_hue),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        PixelSlider(
            value = hue,
            range = 0f..359f,
            step = 1f,
            onValueChange = { onCustom(Color.hsv(it, SATURATION, 1f).toArgb()) },
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#", style = PixelSectionLabel, color = colors.accent)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .styledSurface(0)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { input ->
                        val clean = input.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                        text = clean
                        parseHex(clean)?.let(onCustom)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val SATURATION = 0.65f

private fun hex(color: Color): String = "%06X".format(color.toArgb() and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    if (text.length != 6) return null
    val value = text.toIntOrNull(16) ?: return null
    return 0xFF000000.toInt() or value
}

private fun hueOf(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta < 1e-4f) return 0f
    val raw =
        when (max) {
            r -> ((g - b) / delta) % 6f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        }
    return ((raw * 60f) + 360f) % 360f
}
