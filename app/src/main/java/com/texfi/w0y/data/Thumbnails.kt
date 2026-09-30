package com.texfi.w0y.data

import android.graphics.Bitmap

/**
 * Обложки в нужном размере.
 *
 * YouTube отдаёт в выдаче ссылку на ту картинку, которая нужна была его
 * собственной вёрстке, — обычно 60×60. Если показать её в списке на 48dp,
 * на плотном экране это уже мыло, а в плеере на весь экран — каша. Размер
 * зашит прямо в ссылку, поэтому его можно попросить какой угодно, не делая
 * лишних запросов.
 */
object Thumbnails {
    /**
     * Ссылка на обложку стороной не меньше [px] пикселей.
     *
     * `lh3.googleusercontent.com` и `yt3.ggpht.com` принимают размер
     * суффиксом `=w512-h512`; `i.ytimg.com` — только фиксированным набором
     * имён файлов, и там мы поднимаемся до `hqdefault`, который существует
     * всегда (`maxresdefault` у половины треков отдаёт 404, и обложка
     * просто не появилась бы).
     */
    fun sized(url: String?, px: Int): String? {
        if (url.isNullOrBlank()) return url
        return when {
            url.contains("googleusercontent.com") || url.contains("ggpht.com") -> {
                val base = url.substringBefore('=')
                "$base=w$px-h$px-l90-rj"
            }

            // Обложки плейлистов (pl_c) подписаны: без `sqp` и `rs` сервер
            // отвечает 404, поэтому их отдаём как есть.
            url.contains("i.ytimg.com/pl_c/") -> url

            // Параметры запроса отрезаем: с `sqp=` YouTube отдаёт уже
            // обрезанный кадр 16:9, без — 4:3 с полосами. Двух разных
            // картинок под одним именем быть не должно, иначе обрезка
            // полос в CoverImage срезала бы лишнее у половины видео.
            url.contains("i.ytimg.com") ->
                url
                    .substringBefore('?')
                    .replace(Regex("/(default|mqdefault|sddefault|maxresdefault|hq720)\\.jpg"), "/hqdefault.jpg")

            else -> url
        }
    }

    /**
     * Кадр из видео, а не обложка альбома: у клипов и роликов картинка
     * лежит на i.ytimg.com, у треков — на googleusercontent.
     */
    fun isVideoFrame(url: String?): Boolean = url?.contains("i.ytimg.com") == true

    /**
     * Квадрат из загруженной обложки — для своих холстов (виджет, карточка),
     * где нет ContentScale. У кадра видео 4:3 сначала отрезаются полосы.
     */
    fun squareOf(bitmap: Bitmap, url: String?): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val letterboxed = isVideoFrame(url) && kotlin.math.abs(w * 3 - h * 4) <= w / 50
        val contentH = if (letterboxed) w * 9 / 16 else h
        val side = minOf(w, contentH)
        if (side == w && side == h) return bitmap
        return Bitmap.createBitmap(bitmap, (w - side) / 2, (h - side) / 2, side, side)
    }

    /** Размеры, которые реально встречаются в интерфейсе. */
    const val ROW = 128
    const val TILE = 384
    const val HERO = 768
}
