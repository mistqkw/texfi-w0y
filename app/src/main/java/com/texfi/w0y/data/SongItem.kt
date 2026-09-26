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
)
