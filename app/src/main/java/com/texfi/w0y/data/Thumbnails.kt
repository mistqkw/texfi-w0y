package com.texfi.w0y.data

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

            url.contains("i.ytimg.com") ->
                url.replace(Regex("/(default|mqdefault|sddefault|maxresdefault)\\.jpg"), "/hqdefault.jpg")

            else -> url
        }
    }

    /** Размеры, которые реально встречаются в интерфейсе. */
    const val ROW = 128
    const val TILE = 384
    const val HERO = 768
}
