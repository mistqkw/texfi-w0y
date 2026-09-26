package com.texfi.w0y.data

/**
 * Выбор чистой версии среди результатов поиска.
 *
 * Отдельно от репозитория, потому что ошибается здесь не сеть, а правило
 * сравнения: поиск по запросу «название исполнитель clean» охотно
 * подсовывает чужой кавер или другую песню того же артиста, и проверять
 * это надо тестами, а не на слух.
 */
object CleanMatch {
    fun pick(original: SongItem, candidates: List<SongItem>): SongItem? {
        val core = normalize(original.title)
        if (core.isBlank()) return null
        val artistKey = normalize(original.artist).split(' ').firstOrNull().orEmpty()
        return candidates.firstOrNull { candidate ->
            if (candidate.explicit || candidate.id == original.id) return@firstOrNull false
            val title = normalize(candidate.title)
            if (title.isBlank()) return@firstOrNull false
            val sameSong = title == core || title.contains(core) || core.contains(title)
            val sameArtist = artistKey.isBlank() || normalize(candidate.artist).contains(artistKey)
            sameSong && sameArtist
        }
    }

    /** Название без скобок и знаков: «Трек (feat. X) [Clean]» и «трек» должны совпасть. */
    fun normalize(text: String): String =
        text
            .lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
