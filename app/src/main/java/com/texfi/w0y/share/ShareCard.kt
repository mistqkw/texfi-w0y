package com.texfi.w0y.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.texfi.w0y.R
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.SoundProfile
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.Sprites
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Карточка трека картинкой — то, что уходит из приложения наружу.
 *
 * Рисуется целиком на телефоне: обложка уже лежит в кэше Coil, шрифт и
 * спрайты свои, в сеть за карточкой никто не ходит. И рисуется теми же
 * правилами, что весь интерфейс, — бордер 2 пикселя по сетке, офсетная
 * тень без размытия, пиксельный шрифт только на заголовках и числах.
 * Иначе это была бы просто картинка с обложкой, а не вещь из TexFi.
 *
 * Версия звучания попадает на карточку, если она есть: смысл делиться
 * именно в том, что это твоя версия трека, а не просто трек.
 */
@Singleton
class ShareCardRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Рисует карточку и возвращает файл в кэше.
     *
     * Файл всегда один и тот же: карточки не копятся в кэше, а сам кэш
     * система вольна очистить когда захочет — там и должно лежать то, что
     * уже ушло в чужое приложение.
     */
    suspend fun render(song: SongItem, sound: SoundProfile?): File = withContext(Dispatchers.Default) {
        val cover = loadCover(song.thumbnailUrl)
        val bitmap = createBitmap(song, sound, cover)
        val file = File(context.cacheDir, FILE_NAME)
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        file
    }

    private suspend fun loadCover(url: String?): Bitmap? {
        url ?: return null
        val loader: ImageLoader = SingletonImageLoader.get(context)
        val request =
            ImageRequest
                .Builder(context)
                .data(Thumbnails.sized(url, COVER_PX))
                // Аппаратный битмап нельзя рисовать в свой Canvas.
                .allowHardware(false)
                .build()
        val result = runCatching { loader.execute(request) }.getOrNull()
        return (result as? SuccessResult)?.image?.let { runCatching { Thumbnails.squareOf(it.toBitmap(), url) }.getOrNull() }
    }

    private fun createBitmap(song: SongItem, sound: SoundProfile?, cover: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pixelFont = ResourcesCompat.getFont(context, R.font.press_start_2p)
        val fill = Paint().apply { isAntiAlias = false }

        // Фон и свечение цвета обложки — то же, что в плеере.
        fill.color = BACKGROUND
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), fill)
        val tint = cover?.let { averageColor(it) } ?: ACCENT
        val glow = Paint().apply {
            shader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    HEIGHT * GLOW_STOP,
                    withAlpha(tint, GLOW_ALPHA),
                    AndroidColor.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT * GLOW_STOP, glow)

        // Обложка с офсетной тенью и рамкой.
        val side = WIDTH - MARGIN * 2
        val coverRect = RectF(MARGIN.toFloat(), MARGIN.toFloat(), (MARGIN + side).toFloat(), (MARGIN + side).toFloat())
        fill.color = SHADOW
        canvas.drawRect(coverRect.left + SHADOW_OFFSET, coverRect.top + SHADOW_OFFSET, coverRect.right + SHADOW_OFFSET, coverRect.bottom + SHADOW_OFFSET, fill)
        if (cover != null) {
            canvas.drawBitmap(cover, null, coverRect, Paint().apply { isFilterBitmap = true })
        } else {
            fill.color = SURFACE
            canvas.drawRect(coverRect, fill)
        }
        val border = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = BORDER.toFloat()
            color = BORDER_COLOR
            isAntiAlias = false
        }
        canvas.drawRect(coverRect, border)

        var y = coverRect.bottom + SHADOW_OFFSET + BLOCK_GAP

        // Название — пиксельным шрифтом: это заголовок, его разглядывают.
        // Длинное режем по словам, а не по буквам.
        val titlePaint = textPaint(pixelFont, TITLE_SIZE, TEXT)
        y = drawWrapped(canvas, song.title.uppercase(), titlePaint, MARGIN.toFloat(), y, side, TITLE_LINES)

        y += LINE_GAP
        val artistPaint = textPaint(null, ARTIST_SIZE, TEXT_MUTED)
        y = drawWrapped(canvas, song.artist, artistPaint, MARGIN.toFloat(), y, side, ARTIST_LINES)

        // Метка версии: крупное число — пиксельным шрифтом, как баланс в m0ney.
        val version = sound?.takeIf { !it.isPlain }
        if (version != null) {
            y += BLOCK_GAP
            val label = buildString {
                append(version.label)
                if (version.reverb != Reverb.OFF) {
                    append(" · ")
                    append(context.getString(version.reverb.label).uppercase())
                }
            }
            val chipPaint = textPaint(pixelFont, CHIP_SIZE, BACKGROUND)
            val bounds = Rect()
            chipPaint.getTextBounds(label, 0, label.length, bounds)
            val chip = RectF(
                MARGIN.toFloat(),
                y,
                MARGIN + bounds.width() + CHIP_PADDING * 2f,
                y + bounds.height() + CHIP_PADDING * 2f,
            )
            fill.color = ACCENT
            canvas.drawRect(chip, fill)
            canvas.drawText(label, chip.left + CHIP_PADDING, chip.bottom - CHIP_PADDING, chipPaint)
            y = chip.bottom + LINE_GAP

            val notePaint = textPaint(null, NOTE_SIZE, TEXT_MUTED)
            canvas.drawText(context.getString(R.string.share_card_version), MARGIN.toFloat(), y + NOTE_SIZE, notePaint)
        }

        // Подвал: знак w0y и откуда он. Знак рисуем по той же сетке, что в
        // приложении, а не картинкой — сетка одна на всю экосистему.
        drawSprite(canvas, Sprites.markW0y, MARGIN, HEIGHT - MARGIN - MARK_SIDE, MARK_SIDE, ACCENT)
        val footPaint = textPaint(pixelFont, FOOT_SIZE, TEXT)
        canvas.drawText(
            "w0y",
            (MARGIN + MARK_SIDE + FOOT_GAP).toFloat(),
            (HEIGHT - MARGIN - MARK_SIDE / 2 + FOOT_SIZE / 3).toFloat(),
            footPaint,
        )
        val sourcePaint = textPaint(null, NOTE_SIZE, TEXT_MUTED)
        canvas.drawText(
            context.getString(R.string.share_card_source),
            (MARGIN + MARK_SIDE + FOOT_GAP).toFloat(),
            (HEIGHT - MARGIN).toFloat(),
            sourcePaint,
        )
        return bitmap
    }

    private fun textPaint(typeface: Typeface?, size: Int, color: Int) =
        Paint().apply {
            this.color = color
            textSize = size.toFloat()
            this.typeface = typeface ?: Typeface.DEFAULT_BOLD
            // Пиксельный шрифт сглаживать нельзя: он тут же перестаёт быть
            // пиксельным. Обычный — наоборот, без сглаживания не читается.
            isAntiAlias = typeface == null
        }

    /** Рисует текст по словам, не больше [maxLines] строк. Возвращает новый низ. */
    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        top: Float,
        width: Int,
        maxLines: Int,
    ): Float {
        val words = text.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return top
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= width || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                lines += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        val shown = lines.take(maxLines)
        val height = paint.textSize * 1.35f
        shown.forEachIndexed { index, value ->
            // Последняя влезающая строка получает многоточие, если текст
            // на этом не кончился: обрыв на полуслове читается как сбой.
            val out =
                if (index == shown.lastIndex && lines.size > maxLines) "$value…" else value
            canvas.drawText(out, x, top + height * (index + 1) - paint.textSize * 0.25f, paint)
        }
        return top + height * shown.size
    }

    /** Спрайт по сетке символов: «#» — закрашенная клетка. */
    private fun drawSprite(canvas: Canvas, rows: List<String>, left: Int, top: Int, side: Int, color: Int) {
        val cell = side.toFloat() / rows.size
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = false
        }
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, symbol ->
                if (symbol != '#') return@forEachIndexed
                val x = left + columnIndex * cell
                val yTop = top + rowIndex * cell
                // Клетки с нахлёстом, а не с зазором: на субпиксельной
                // сетке между ними иначе появляются щели, и контур
                // рассыпается.
                canvas.drawRect(x, yTop, x + cell + 1f, yTop + cell + 1f, paint)
            }
        }
    }

    private fun averageColor(bitmap: Bitmap): Int {
        val scaled = runCatching { bitmap.scale() }.getOrNull() ?: return ACCENT
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled !== bitmap) scaled.recycle()
        var red = 0L
        var green = 0L
        var blue = 0L
        pixels.forEach {
            red += (it shr 16) and 0xFF
            green += (it shr 8) and 0xFF
            blue += it and 0xFF
        }
        val count = pixels.size.coerceAtLeast(1)
        val argb = AndroidColor.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.4f)
        hsv[2] = hsv[2].coerceIn(0.45f, 0.85f)
        return AndroidColor.HSVToColor(hsv)
    }

    private fun Bitmap.scale(): Bitmap =
        if (width <= SAMPLE_PX && height <= SAMPLE_PX) this else Bitmap.createScaledBitmap(this, SAMPLE_PX, SAMPLE_PX, true)

    private fun withAlpha(color: Int, alpha: Float): Int =
        AndroidColor.argb(
            (alpha * 255).toInt().coerceIn(0, 255),
            AndroidColor.red(color),
            AndroidColor.green(color),
            AndroidColor.blue(color),
        )

    private companion object {
        const val FILE_NAME = "w0y-card.png"

        /** 4:5 — то, что не обрезают ни сторис, ни лента. */
        const val WIDTH = 1080
        const val HEIGHT = 1350
        const val MARGIN = 72
        const val COVER_PX = 720
        const val SAMPLE_PX = 16
        const val SHADOW_OFFSET = 14f
        const val BORDER = 6
        const val BLOCK_GAP = 44f
        const val LINE_GAP = 14f
        const val TITLE_SIZE = 46
        const val TITLE_LINES = 2
        const val ARTIST_SIZE = 40
        const val ARTIST_LINES = 1
        const val CHIP_SIZE = 38
        const val CHIP_PADDING = 18
        const val NOTE_SIZE = 28
        const val FOOT_SIZE = 32
        const val FOOT_GAP = 22
        const val MARK_SIDE = 72
        const val GLOW_ALPHA = 0.32f
        const val GLOW_STOP = 0.6f

        const val BACKGROUND = 0xFF0D0D11.toInt()
        const val SURFACE = 0xFF20202A.toInt()
        const val SHADOW = 0xFF05050A.toInt()
        const val BORDER_COLOR = 0xFF2E2E3A.toInt()
        const val ACCENT = 0xFF4A7DFB.toInt()
        const val TEXT = 0xFFF2F2F5.toInt()
        const val TEXT_MUTED = 0xFF8A8A96.toInt()
    }
}
