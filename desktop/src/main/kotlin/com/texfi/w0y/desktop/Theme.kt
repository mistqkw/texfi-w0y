package com.texfi.w0y.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/** Состояние темы: меняется в настройках, всё нарисованное перерисовывается само. */
object ThemeState {
    var mode by androidx.compose.runtime.mutableStateOf(ThemeMode.DARK)
    var accent by androidx.compose.runtime.mutableStateOf(Accent.BLUE)
    var custom by androidx.compose.runtime.mutableStateOf(0xFFA06CFF.toInt())
}

/**
 * Токены TexFi: графитовый фон, синий и песочный — те же значения, что в Android-теме.
 * Свойства читаются при отрисовке, поэтому смена темы подхватывается сразу.
 */
object C {
    private val light get() = ThemeState.mode == ThemeMode.LIGHT
    private val oled get() = ThemeState.mode == ThemeMode.OLED
    private val accent get() = ThemeState.accent

    val Blue: Color get() = if (accent == Accent.CUSTOM) Color(ThemeState.custom) else Color(accent.accent)
    val BlueDeep: Color get() = if (accent == Accent.CUSTOM) androidx.compose.ui.graphics.lerp(Color(ThemeState.custom), Color.Black, 0.45f) else Color(accent.deep)
    val BlueLight = Color(0xFF7FB5FF)
    val Sand: Color get() = if (light && accent == Accent.BLUE) Color(0xFFB98232) else Color(accent.secondary)
    val SandDeep = Color(0xFF8A5F26)
    val Background: Color get() = if (light) Color(0xFFF7F1E4) else if (oled) Color(0xFF000000) else Color(0xFF15151B)
    val Surface: Color get() = if (light) Color(0xFFFFFDF7) else if (oled) Color(0xFF000000) else Color(0xFF1F1F27)
    val SurfaceHigh: Color get() = if (light) Color(0xFFEDE4D2) else if (oled) Color(0xFF101014) else Color(0xFF2A2A34)
    val Border: Color get() = if (light) Color(0xFFD5C7AC) else if (oled) Color(0xFF2C2C36) else Color(0xFF383845)
    val Shadow: Color get() = if (light) Color(0xFFC9B99B) else if (oled) Color(0xFF17171D) else Color(0xFF0A0A0E)
    val Text: Color get() = if (light) Color(0xFF191921) else Color(0xFFF6F1E5)
    val Muted: Color get() = if (light) Color(0xFF6B6455) else Color(0xFF8A8A96)
    val Danger = Color(0xFFFF5A5A)

    /** Текст на синей заливке кнопки — светлый в любой теме. */
    val OnAccent = Color(0xFFF6F1E5)
}

val PixelFont = FontFamily(Font(resource = "press_start_2p.ttf"))

/** Обычный текст — читаемый; пиксельный шрифт только для заголовков и подписей разделов. */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.Text,
    size: Int = 14,
    pixel: Boolean = false,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style =
            TextStyle(
                color = color,
                fontSize = size.sp,
                fontFamily = if (pixel) PixelFont else FontFamily.Default,
                fontWeight = weight,
                lineHeight = (size * 1.35f).sp,
            ),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Txt(text.uppercase(), modifier, color = C.Blue, size = 11, pixel = true)

/** Карточка с жёсткой границей и сдвинутой тенью без размытия. */
@Composable
fun PixelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .drawBehind {
                drawRect(C.Shadow, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = size)
            }.background(C.Surface, RoundedCornerShape(6.dp))
            .border(2.dp, C.Border, RoundedCornerShape(6.dp))
            .padding(14.dp),
    ) { content() }
}

/** Кнопка: при нажатии тень «уезжает» внутрь, как на телефоне. */
@Composable
fun PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill = if (primary) C.Blue else C.SurfaceHigh
    val shadow = if (primary) C.BlueDeep else C.Shadow
    Box(
        modifier
            .hoverable(source)
            .drawBehind {
                drawRect(shadow, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = size)
            }.background(if (hovered && enabled) fill.copy(alpha = 0.9f) else fill, RoundedCornerShape(4.dp))
            .border(2.dp, if (primary) C.BlueDeep else C.Border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Txt(text.uppercase(), color = if (enabled) (if (primary) C.OnAccent else C.Text) else C.Muted, size = 10, pixel = true) }
}

enum class Glyph { Play, Pause, Next, Prev, Heart, HeartOff, Search, Plus, Close, Queue, Home, Library, Volume, Trash, Download, Check, Timer, Mic, Wave, Gear }

/** Иконки рисуются кодом: ни растровых ассетов, ни шрифта с символами. */
@Composable
fun Icon(glyph: Glyph, color: Color = C.Text, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).drawBehind {
            val w = this.size.width
            val h = this.size.height
            fun tri(a: Offset, b: Offset, c: Offset) =
                drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
            when (glyph) {
                Glyph.Play -> tri(Offset(w * .25f, h * .12f), Offset(w * .25f, h * .88f), Offset(w * .88f, h * .5f))
                Glyph.Pause -> {
                    drawRect(color, Offset(w * .2f, h * .14f), Size(w * .22f, h * .72f))
                    drawRect(color, Offset(w * .58f, h * .14f), Size(w * .22f, h * .72f))
                }
                Glyph.Next -> {
                    tri(Offset(w * .12f, h * .18f), Offset(w * .12f, h * .82f), Offset(w * .66f, h * .5f))
                    drawRect(color, Offset(w * .7f, h * .18f), Size(w * .16f, h * .64f))
                }
                Glyph.Prev -> {
                    tri(Offset(w * .88f, h * .18f), Offset(w * .88f, h * .82f), Offset(w * .34f, h * .5f))
                    drawRect(color, Offset(w * .14f, h * .18f), Size(w * .16f, h * .64f))
                }
                Glyph.Heart, Glyph.HeartOff -> {
                    val cell = w / 7f
                    val rows = listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000")
                    rows.forEachIndexed { y, row ->
                        row.forEachIndexed { x, ch ->
                            if (ch == '1') {
                                val fillCell = glyph == Glyph.Heart
                                if (fillCell) {
                                    drawRect(color, Offset(x * cell, (y + .5f) * cell), Size(cell + .6f, cell + .6f))
                                } else {
                                    val edge = y == 0 || y == rows.lastIndex || row.getOrNull(x - 1) != '1' || row.getOrNull(x + 1) != '1' ||
                                        rows.getOrNull(y - 1)?.getOrNull(x) != '1' || rows.getOrNull(y + 1)?.getOrNull(x) != '1'
                                    if (edge) drawRect(color, Offset(x * cell, (y + .5f) * cell), Size(cell + .6f, cell + .6f))
                                }
                            }
                        }
                    }
                }
                Glyph.Search -> {
                    drawCircle(color, radius = w * .3f, center = Offset(w * .42f, h * .42f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .12f))
                    drawLine(color, Offset(w * .64f, h * .64f), Offset(w * .9f, h * .9f), strokeWidth = w * .14f)
                }
                Glyph.Gear -> {
                    val cell = w / 7f
                    val rows = listOf("0010100", "0111110", "1110111", "1100011", "1110111", "0111110", "0010100")
                    rows.forEachIndexed { y, row ->
                        row.forEachIndexed { x, ch -> if (ch == '1') drawRect(color, Offset(x * cell, y * cell), Size(cell + .6f, cell + .6f)) }
                    }
                }
                Glyph.Plus -> {
                    drawRect(color, Offset(w * .43f, h * .12f), Size(w * .14f, h * .76f))
                    drawRect(color, Offset(w * .12f, h * .43f), Size(w * .76f, h * .14f))
                }
                Glyph.Close -> {
                    drawLine(color, Offset(w * .2f, h * .2f), Offset(w * .8f, h * .8f), strokeWidth = w * .13f)
                    drawLine(color, Offset(w * .8f, h * .2f), Offset(w * .2f, h * .8f), strokeWidth = w * .13f)
                }
                Glyph.Queue -> {
                    for (i in 0..2) drawRect(color, Offset(w * .12f, h * (.2f + i * .26f)), Size(w * .76f, h * .12f))
                }
                Glyph.Home -> {
                    tri(Offset(w * .5f, h * .1f), Offset(w * .08f, h * .5f), Offset(w * .92f, h * .5f))
                    drawRect(color, Offset(w * .22f, h * .5f), Size(w * .56f, h * .38f))
                }
                Glyph.Library -> {
                    drawRect(color, Offset(w * .12f, h * .15f), Size(w * .76f, h * .26f))
                    drawRect(color, Offset(w * .12f, h * .55f), Size(w * .76f, h * .26f))
                }
                Glyph.Volume -> {
                    drawRect(color, Offset(w * .1f, h * .38f), Size(w * .22f, h * .24f))
                    tri(Offset(w * .32f, h * .38f), Offset(w * .32f, h * .62f), Offset(w * .6f, h * .82f))
                    tri(Offset(w * .32f, h * .38f), Offset(w * .6f, h * .18f), Offset(w * .6f, h * .82f))
                    drawRect(color, Offset(w * .72f, h * .36f), Size(w * .1f, h * .28f))
                }
                Glyph.Download -> {
                    drawRect(color, Offset(w * .43f, h * .1f), Size(w * .14f, h * .46f))
                    tri(Offset(w * .22f, h * .46f), Offset(w * .78f, h * .46f), Offset(w * .5f, h * .76f))
                    drawRect(color, Offset(w * .15f, h * .84f), Size(w * .7f, h * .1f))
                }
                Glyph.Check -> {
                    drawLine(color, Offset(w * .15f, h * .52f), Offset(w * .4f, h * .76f), strokeWidth = w * .14f)
                    drawLine(color, Offset(w * .4f, h * .76f), Offset(w * .86f, h * .22f), strokeWidth = w * .14f)
                }
                Glyph.Timer -> {
                    drawCircle(color, radius = w * .38f, center = Offset(w * .5f, h * .54f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .1f))
                    drawLine(color, Offset(w * .5f, h * .54f), Offset(w * .5f, h * .3f), strokeWidth = w * .1f)
                    drawLine(color, Offset(w * .5f, h * .54f), Offset(w * .66f, h * .62f), strokeWidth = w * .1f)
                    drawRect(color, Offset(w * .4f, h * .04f), Size(w * .2f, h * .09f))
                }
                Glyph.Mic -> {
                    drawRect(color, Offset(w * .12f, h * .15f), Size(w * .76f, h * .1f))
                    drawRect(color, Offset(w * .12f, h * .4f), Size(w * .76f, h * .1f))
                    drawRect(color, Offset(w * .12f, h * .65f), Size(w * .46f, h * .1f))
                }
                Glyph.Wave -> {
                    listOf(.35f, .6f, .9f, .6f, .35f).forEachIndexed { i, hh ->
                        drawRect(color, Offset(w * (.1f + i * .17f), h * (.5f - hh / 2)), Size(w * .1f, h * hh))
                    }
                }
                Glyph.Trash -> {
                    drawRect(color, Offset(w * .2f, h * .3f), Size(w * .6f, h * .56f))
                    drawRect(color, Offset(w * .12f, h * .18f), Size(w * .76f, h * .1f))
                }
            }
        },
    )
}

/** Квадратная иконка-кнопка с подсветкой при наведении. */
@Composable
fun IconButton(glyph: Glyph, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Text, size: Dp = 20.dp, box: Dp = 34.dp) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier
            .size(box)
            .hoverable(source)
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) C.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, if (hovered) C.Text else color, size) }
}

private val ThumbRegex = Regex("=[wsh]\\d+[^/]*$")

/** Обложка: googleusercontent просим нужного размера, остальное берём как есть. */
fun sizedThumb(url: String?, px: Int): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
            url.substringBefore('=') + "=w$px-h$px-l90-rj"
        url.contains("i.ytimg.com/pl_c/") -> url
        url.contains("i.ytimg.com") ->
            url.substringBefore('?').replace(Regex("/(default|mqdefault|sddefault|maxresdefault|hq720)\\.jpg"), "/hqdefault.jpg")
        else -> url
    }
}

@Composable
fun Cover(url: String?, size: Dp, modifier: Modifier = Modifier, circle: Boolean = false) {
    val shape = if (circle) CircleShape else RoundedCornerShape(4.dp)
    Box(modifier.size(size).clip(shape).background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
        Icon(Glyph.Queue, C.Border, size / 3)
        val model = sizedThumb(url, (size.value * 2).toInt().coerceAtLeast(120))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
fun hoverBackground(source: MutableInteractionSource): Color {
    val hovered by source.collectIsHoveredAsState()
    return if (hovered) C.Surface else Color.Transparent
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(C.Border))

@Composable
fun Gap(w: Int = 0, h: Int = 0) = Box(Modifier.width(w.dp).height(h.dp))

@Composable
fun RowCenter(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, content = content)

fun Modifier.pad(h: Int = 0, v: Int = 0) = padding(horizontal = h.dp, vertical = v.dp)
fun Modifier.shift(x: Int = 0, y: Int = 0) = offset(x.dp, y.dp)

/** Ползунок из сегментов, как громкость на телефоне: значение в [range], шаг [step]. */
@Composable
fun PixelSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier, color: Color = C.Blue, step: Float = 0.05f, segments: Int = 24) {
    fun at(x: Float, width: Int): Float {
        val raw = range.start + (x / width).coerceIn(0f, 1f) * (range.endInclusive - range.start)
        return (Math.round(raw / step) * step).coerceIn(range.start, range.endInclusive)
    }
    Box(
        modifier.height(18.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(at(it.x, size.width)) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> onChange(at(c.position.x, size.width)) } }
            .drawBehind {
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (segments - 1)) / segments
                val filled = (((value - range.start) / (range.endInclusive - range.start)) * segments).toInt()
                repeat(segments) { i ->
                    drawRect(if (i < filled) color else C.SurfaceHigh, Offset(i * (w + gap), size.height / 2 - 4.dp.toPx()), Size(w, 8.dp.toPx()))
                }
            },
    )
}

/** Главный цвет обложки: средний по уменьшенной картинке, без почти чёрных и серых пикселей. */
@Composable
fun rememberCoverColor(url: String?): Color? {
    val color by androidx.compose.runtime.produceState<Color?>(null, url) {
        value = null
        val model = sizedThumb(url, 120) ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val img = javax.imageio.ImageIO.read(java.net.URI(model).toURL()) ?: return@runCatching null
                var r = 0L; var g = 0L; var b = 0L; var n = 0L
                val step = (img.width / 16).coerceAtLeast(1)
                for (x in 0 until img.width step step) for (y in 0 until img.height step step) {
                    val p = img.getRGB(x, y)
                    val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
                    val mx = maxOf(pr, pg, pb); val mn = minOf(pr, pg, pb)
                    if (mx < 40 || mx - mn < 25) continue
                    r += pr; g += pg; b += pb; n++
                }
                if (n == 0L) null else Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
            }.getOrNull()
        }
    }
    return color
}
