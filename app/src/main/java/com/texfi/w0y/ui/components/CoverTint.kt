package com.texfi.w0y.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.texfi.w0y.data.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Цвет обложки — настоящий, снятый с картинки.
 *
 * Обложка грузится отдельным крошечным запросом (шестнадцать пикселей по
 * стороне) и усредняется. Так «свечение» под плеером действительно
 * принадлежит этому треку: подкрашивать фон акцентом и называть это
 * цветом обложки было бы тем же обманом, что кнопка-пустышка.
 *
 * Возвращает [fallback], пока картинка не пришла или если она не пришла
 * вовсе.
 */
@Composable
fun rememberCoverTint(url: String?, fallback: Color): State<Color> {
    val context = LocalContext.current
    val tint = remember(url) { mutableStateOf(url?.let(TintCache::get) ?: fallback) }
    LaunchedEffect(url, fallback) {
        if (url == null) {
            tint.value = fallback
            return@LaunchedEffect
        }
        // Цвет уже считали — фон не ждёт загрузки и не мигает при возврате к треку.
        TintCache.get(url)?.let {
            tint.value = it
            return@LaunchedEffect
        }
        val loader: ImageLoader = SingletonImageLoader.get(context)
        val request =
            ImageRequest
                .Builder(context)
                .data(Thumbnails.sized(url, SAMPLE_PX))
                // Аппаратный битмап нельзя читать по пикселям: без этого
                // getPixels падает на настоящем устройстве.
                .allowHardware(false)
                .build()
        val sampled =
            withContext(Dispatchers.Default) {
                val result = runCatching { loader.execute(request) }.getOrNull()
                val image = (result as? SuccessResult)?.image ?: return@withContext null
                runCatching { average(image.toBitmap()) }.getOrNull()
            }
        sampled?.let { TintCache.put(url, it) }
        tint.value = sampled ?: fallback
    }
    return tint
}

/** Последние посчитанные цвета обложек. */
private object TintCache {
    private val map = object : LinkedHashMap<String, Color>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 64
    }

    @Synchronized
    fun get(url: String): Color? = map[url]

    @Synchronized
    fun put(url: String, color: Color) {
        map[url] = color
    }
}

/**
 * Средний цвет, поднятый до заметной насыщенности.
 *
 * Просто среднее по обложке почти всегда даёт серо-бурое: кадр состоит
 * не из одного цвета. Поэтому насыщенность подтягивается до минимума, а
 * яркость держится в коридоре — иначе тёмная обложка даёт чёрное пятно,
 * а светлая заливает экран белым. Считаем через HSV самого фреймворка,
 * чтобы не тащить зависимость ради трёх строк арифметики.
 */
private fun average(bitmap: android.graphics.Bitmap): Color {
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return Color.Transparent
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    var red = 0L
    var green = 0L
    var blue = 0L
    pixels.forEach { pixel ->
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = pixels.size
    val argb =
        android.graphics.Color.rgb(
            (red / count).toInt(),
            (green / count).toInt(),
            (blue / count).toInt(),
        )
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = hsv[1].coerceAtLeast(MIN_SATURATION)
    hsv[2] = hsv[2].coerceIn(MIN_VALUE, MAX_VALUE)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Цвет как основа для мягкого свечения: почти прозрачный. */
fun Color.asGlow(alpha: Float = 0.28f): Color = copy(alpha = alpha)

/** Оттенок нужен, а не разрешение: шестнадцать пикселей хватает с запасом. */
private const val SAMPLE_PX = 16
private const val MIN_SATURATION = 0.4f
private const val MIN_VALUE = 0.45f
private const val MAX_VALUE = 0.85f
