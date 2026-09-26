package com.texfi.w0y.data

import com.texfi.w0y.data.db.ArtistPlays
import com.texfi.w0y.data.db.W0yDao
import javax.inject.Inject
import javax.inject.Singleton

/** За какой срок считаем итоги. */
enum class StatsPeriod(val label: String, val days: Int?) {
    WEEK("НЕДЕЛЯ", 7),
    MONTH("МЕСЯЦ", 30),
    ALL("ВСЁ ВРЕМЯ", null),
}

/** Итоги прослушивания за период. */
data class ListeningStats(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val plays: Int = 0,
    val minutes: Int = 0,
    val topSongs: List<Pair<SongItem, Int>> = emptyList(),
    val topArtists: List<ArtistPlays> = emptyList(),
    val since: Long = 0L,
)

/**
 * Свои итоги вместо годового «рекапа».
 *
 * Всё считается на телефоне по локальной истории и доступно в любой день,
 * а не раз в декабре. Никуда не отправляется: данных для этого экрана у
 * сервиса и не спрашиваем.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val dao: W0yDao,
) {
    suspend fun stats(period: StatsPeriod): ListeningStats {
        val since = period.days?.let { System.currentTimeMillis() - it * DAY_MS } ?: 0L
        val minutes =
            dao
                .playedDurations(since)
                .sumOf { row -> durationSeconds(row.durationText) * row.plays } / 60
        return ListeningStats(
            period = period,
            plays = dao.playsSince(since),
            minutes = minutes,
            topSongs = dao.topSongsSince(since).map { it.song.toItem() to it.plays },
            topArtists = dao.topArtistsSince(since),
            since = dao.firstPlayAt() ?: since,
        )
    }

    /**
     * «3:47» или «1:02:11» в секунды.
     *
     * Длительность хранится строкой ровно в том виде, в каком её показал
     * YouTube: своего поля с секундами в выдаче нет, а выдумывать его
     * разбором потока ради статистики — лишние запросы.
     */
    private fun durationSeconds(text: String?): Int {
        val parts = text?.split(':')?.mapNotNull { it.trim().toIntOrNull() } ?: return 0
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
