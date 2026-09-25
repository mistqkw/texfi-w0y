package com.texfi.w0y.data

import com.metrolist.innertubex.models.MusicResponsiveListItemRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Разбор ответов YouTube Music обходом дерева, а не по точным путям.
 *
 * YouTube регулярно переставляет обёртки вокруг одних и тех же карточек
 * (вкладки, секции, «полки»). Поиск нужных рендереров по всему дереву
 * переживает такие перестановки, а жёсткий путь ломается и возвращает
 * пустой список — ровно это уже случилось на вкладках поиска.
 */
object YtJson {
    val json = Json { ignoreUnknownKeys = true }

    /** Все объекты с таким ключом на любой глубине. */
    fun JsonElement.findAll(key: String): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject ->
                    element.forEach { (name, value) ->
                        if (name == key && value is JsonObject) found += value
                        walk(value)
                    }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return found
    }

    fun JsonElement.firstString(key: String): String? {
        var result: String? = null
        fun walk(element: JsonElement) {
            if (result != null) return
            when (element) {
                is JsonObject ->
                    element.forEach { (name, value) ->
                        if (result != null) return@forEach
                        if (name == key && value is JsonPrimitive && value.isString) {
                            result = value.content
                        } else {
                            walk(value)
                        }
                    }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return result
    }

    /** Треки из любой выдачи: поиска, плейлиста, лайков, рекомендаций. */
    fun songs(root: JsonElement): List<SongItem> =
        root
            .findAll("musicResponsiveListItemRenderer")
            .mapNotNull { obj ->
                runCatching {
                    json.decodeFromJsonElement(MusicResponsiveListItemRenderer.serializer(), obj)
                }.getOrNull()?.toSong()
            }.distinctBy { it.id }

    /** Плейлисты и альбомы: карточки-плитки. */
    fun playlistCards(root: JsonElement): List<PlaylistCard> =
        root
            .findAll("musicTwoRowItemRenderer")
            .mapNotNull { obj ->
                val browseId =
                    obj["navigationEndpoint"]
                        ?.jsonObject
                        ?.get("browseEndpoint")
                        ?.jsonObject
                        ?.get("browseId")
                        ?.let { (it as? JsonPrimitive)?.content }
                        ?: return@mapNotNull null
                if (!browseId.startsWith("VL") && !browseId.startsWith("MPRE")) return@mapNotNull null
                val title = obj["title"]?.firstString("text") ?: return@mapNotNull null
                PlaylistCard(
                    browseId = browseId,
                    title = title,
                    subtitle = obj["subtitle"]?.firstString("text"),
                    thumbnailUrl = obj["thumbnailRenderer"]?.firstString("url"),
                )
            }.distinctBy { it.browseId }

    private fun MusicResponsiveListItemRenderer.toSong(): SongItem? {
        val videoId =
            playlistItemData?.videoId
                ?: navigationEndpoint?.watchEndpoint?.videoId
                ?: return null
        val columns =
            flexColumns.mapNotNull { column ->
                column.musicResponsiveListItemFlexColumnRenderer.text?.runs
            }
        val title = columns.firstOrNull()?.firstOrNull()?.text ?: return null
        // Вторая колонка — «исполнитель • альбом • длительность», разделители
        // приходят отдельными ранами, поэтому фильтруем их, а не режем строку.
        val details =
            columns
                .getOrNull(1)
                ?.map { it.text }
                ?.filter { it.isNotBlank() && it != " • " }
                .orEmpty()
        val duration = details.lastOrNull()?.takeIf { it.contains(':') }
        val meaningful = details.filterNot { it == duration }
        return SongItem(
            id = videoId,
            title = title,
            artist = meaningful.firstOrNull().orEmpty(),
            album = meaningful.getOrNull(1),
            durationText = duration,
            thumbnailUrl =
                thumbnail
                    ?.musicThumbnailRenderer
                    ?.thumbnail
                    ?.thumbnails
                    ?.maxByOrNull { it.width ?: 0 }
                    ?.url,
        )
    }
}

/** Карточка плейлиста или альбома из аккаунта или выдачи. */
data class PlaylistCard(
    val browseId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
)
