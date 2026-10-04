package com.texfi.w0y.data

import androidx.compose.runtime.Immutable

/**
 * Трек в том виде, в каком его показывает интерфейс.
 *
 * Модель неизменяемая и без ссылок на сетевые типы: Compose пропускает
 * рекомпозицию списка, если элементы стабильны, а это прямо влияет на
 * плавность прокрутки — одно из трёх требований к приложению.
 */
@Immutable
data class SongItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    /** Канал исполнителя (UC…), если YouTube его отдал: по нему открывается артист. */
    val artistId: String? = null,
    /** Альбом (MPRE…), если трек к нему относится. */
    val albumId: String? = null,
    /** Метка «E» — YouTube помечает ею записи с ненормативной лексикой. */
    val explicit: Boolean = false,
    /** Сколько раз трек слушали — строкой в том виде, как её отдал YouTube. */
    val plays: String? = null,
    /**
     * «Твоя версия» этого трека, если она задана.
     *
     * Заполняется только для треков из локальной базы: версии у трека,
     * который ни разу не включали, быть не может, и рисовать метку в
     * выдаче поиска было бы обещанием того, чего нет.
     */
    val sound: SoundProfile? = null,
    /**
     * Все исполнители трека со своими каналами — у фитов их несколько.
     * Пусто, если YouTube ссылок не дал или трек сохранён до этого поля.
     */
    val artists: List<ArtistLink> = emptyList(),
) {
    /** К кому можно перейти из трека: все артисты, а у старых записей — хотя бы первый. */
    fun artistLinks(): List<ArtistLink> =
        artists.ifEmpty { artistId?.let { listOf(ArtistLink(it, artist)) }.orEmpty() }
}

/** Исполнитель трека и его канал (UC…). */
@Immutable
data class ArtistLink(val id: String, val name: String) {
    companion object {
        private const val FIELD = '\u001F'
        private const val ITEM = '\u001E'

        /** Строкой для базы, очереди и extras плеера; null — ссылок нет. */
        fun encode(links: List<ArtistLink>): String? =
            links.takeIf { it.isNotEmpty() }?.joinToString(ITEM.toString()) { "${it.id}$FIELD${it.name}" }

        fun decode(raw: String?): List<ArtistLink> =
            raw
                ?.split(ITEM)
                ?.mapNotNull { item ->
                    val parts = item.split(FIELD, limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank()) ArtistLink(parts[0], parts[1]) else null
                }.orEmpty()
    }
}
