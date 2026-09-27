package com.texfi.w0y.data

/** Какую готовую переделку искать. */
enum class EditKind(
    val query: String,
    /** По этим словам в названии переделка отличается от оригинала. */
    val markers: List<String>,
) {
    SLOWED("slowed", listOf("slowed", "замедл")),
    SPED_UP("sped up", listOf("sped up", "speed up", "sped-up", "spedup", "nightcore", "ускор")),
}

/**
 * Выбор переделки среди выдачи.
 *
 * Правило: в названии есть и сама песня, и метка переделки. Исполнитель
 * у фанатских роликов — это канал залившего, поэтому сначала ищем того,
 * у кого артист встречается в названии или подписи, и только потом
 * соглашаемся на совпадение одного названия.
 */
object EditMatch {
    fun pick(original: SongItem, kind: EditKind, candidates: List<SongItem>): SongItem? {
        val core = CleanMatch.normalize(original.title)
        if (core.isBlank()) return null
        val artistKey = CleanMatch.normalize(original.artist).split(' ').firstOrNull().orEmpty()
        val matching =
            candidates.filter { candidate ->
                if (candidate.id == original.id) return@filter false
                val raw = candidate.title.lowercase()
                kind.markers.any { it in raw } && CleanMatch.normalize(raw).contains(core)
            }
        return matching.firstOrNull { candidate ->
            artistKey.isNotBlank() &&
                (CleanMatch.normalize(candidate.title).contains(artistKey) || CleanMatch.normalize(candidate.artist).contains(artistKey))
        } ?: matching.firstOrNull()
    }
}
