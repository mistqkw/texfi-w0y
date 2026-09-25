package com.texfi.w0y.data

import com.metrolist.innertubex.models.MusicResponsiveListItemRenderer
import com.metrolist.innertubex.models.MusicShelfRenderer
import com.metrolist.innertubex.models.response.SearchResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Разбор ответа поиска YouTube Music.
 *
 * Библиотека отдаёт вкладки результатов сырым JSON, поэтому до полок
 * приходится доходить руками. Всё, что не распозналось, молча пропускается:
 * YouTube регулярно добавляет в выдачу новые типы карточек, и падать из-за
 * незнакомой карточки приложение не должно.
 */
object SearchParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Фильтр «только песни» для запроса поиска. */
    const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

    fun songs(response: SearchResponse): List<SongItem> {
        val shelves = shelves(response)
        return shelves.flatMap { shelf ->
            shelf.contents.orEmpty().mapNotNull { content ->
                content.musicResponsiveListItemRenderer?.toSong()
            }
        }
    }

    private fun shelves(response: SearchResponse): List<MusicShelfRenderer> {
        val continuation = response.continuationContents?.musicShelfContinuation
        if (continuation != null) {
            return listOfNotNull(decodeShelf(continuation))
        }
        val sectionList =
            response.contents?.sectionListRenderer
                ?: response.contents
                    ?.tabbedSearchResultsRenderer
                    // «tabs» — массив, а не объект: на этом месте разбор
                    // молча возвращал пустую выдачу.
                    ?.let { it["tabs"] as? JsonArray }
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.obj("tabRenderer")
                    ?.obj("content")
                    ?.obj("sectionListRenderer")
                ?: return emptyList()

        val contents = sectionList["contents"]?.jsonArray ?: return emptyList()
        return contents.mapNotNull { entry ->
            entry.jsonObject.obj("musicShelfRenderer")?.let(::decodeShelf)
        }
    }

    private fun decodeShelf(obj: JsonObject): MusicShelfRenderer? =
        runCatching { json.decodeFromJsonElement(MusicShelfRenderer.serializer(), obj) }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

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
            columns.getOrNull(1)
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
