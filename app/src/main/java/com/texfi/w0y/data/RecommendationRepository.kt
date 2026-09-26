package com.texfi.w0y.data

import com.texfi.w0y.data.db.W0yDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Что включить дальше — бета.
 *
 * Основа — радиостанция YouTube по текущему треку: там уже учтены жанр и
 * звучание, и выдумывать свой «алгоритм похожести» поверх чужого каталога
 * было бы самообманом. А вот порядок мы меняем под конкретного слушателя:
 * приложение знает, кого он слушает чаще всего и что искал, и поднимает
 * такие треки вперёд. Поэтому первым обычно играет не просто похожее,
 * а похожее из того, чем человек уже интересовался.
 */
@Singleton
class RecommendationRepository @Inject constructor(
    private val youtube: YouTubeRepository,
    private val dao: W0yDao,
    private val searchHistory: SearchHistoryRepository,
) {
    suspend fun continuation(
        seed: SongItem,
        exclude: Set<String> = emptySet(),
        limit: Int = 25,
    ): List<SongItem> {
        val candidates =
            youtube
                .radio(seed.id)
                .filterNot { it.id in exclude || it.id == seed.id }
        if (candidates.isEmpty()) return emptyList()

        val taste = dao.topArtists().associate { it.artist.lowercase(Locale.ROOT) to it.plays }
        val words =
            searchHistory.recent
                .first()
                .flatMap { it.lowercase(Locale.ROOT).split(' ') }
                .filter { it.length >= MIN_WORD }
                .toSet()
        val seedArtist = seed.artist.lowercase(Locale.ROOT)

        return candidates
            // sortedByDescending устойчива: при равном счёте сохраняется
            // порядок YouTube, то есть его собственная оценка похожести.
            .sortedByDescending { candidate ->
                val artist = candidate.artist.lowercase(Locale.ROOT)
                var score = 0
                taste[artist]?.let { plays -> score += FAMILIAR_ARTIST * plays.coerceAtMost(PLAYS_CAP) }
                if (artist.isNotBlank() && artist == seedArtist) score += SAME_ARTIST
                val haystack = "$artist ${candidate.title.lowercase(Locale.ROOT)}"
                if (words.any { it in haystack }) score += SEARCHED
                score
            }.take(limit)
    }

    private companion object {
        /** Вес знакомого исполнителя за каждое прослушивание, но не больше потолка. */
        const val FAMILIAR_ARTIST = 3
        const val PLAYS_CAP = 5

        /** Тот же исполнитель — самое надёжное совпадение по звучанию. */
        const val SAME_ARTIST = 4

        /** Совпадение с недавним поиском. */
        const val SEARCHED = 6

        const val MIN_WORD = 3
    }
}
