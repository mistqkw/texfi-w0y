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
                val song =
                    runCatching {
                        json.decodeFromJsonElement(MusicResponsiveListItemRenderer.serializer(), obj)
                    }.getOrNull()?.toSong() ?: return@mapNotNull null
                // Ссылки на артиста и альбом лежат в ранах подписи. Типовая
                // модель библиотеки их не разбирает, поэтому берём из сырого
                // объекта: без них не открыть карточку артиста из списка.
                song.copy(
                    artistId = obj.browseIds().firstOrNull { it.startsWith("UC") },
                    albumId = obj.browseIds().firstOrNull { it.startsWith("MPRE") },
                )
            }.distinctBy { it.id }

    /** Артисты: и строкой в выдаче поиска, и плиткой в «похожих». */
    fun artistCards(root: JsonElement): List<ArtistCard> {
        val fromRows =
            root.findAll("musicResponsiveListItemRenderer").mapNotNull { obj ->
                // У трека есть videoId, у артиста — только канал.
                if (obj.firstString("videoId") != null) return@mapNotNull null
                val browseId =
                    obj.browseIds().firstOrNull { it.startsWith("UC") } ?: return@mapNotNull null
                ArtistCard(
                    browseId = browseId,
                    name = obj["flexColumns"]?.firstString("text") ?: return@mapNotNull null,
                    subtitle = null,
                    thumbnailUrl = obj.bestThumbnail(),
                )
            }
        val fromTiles =
            root.findAll("musicTwoRowItemRenderer").mapNotNull { obj ->
                val browseId =
                    obj.browseIds().firstOrNull { it.startsWith("UC") } ?: return@mapNotNull null
                ArtistCard(
                    browseId = browseId,
                    name = obj["title"]?.firstString("text") ?: return@mapNotNull null,
                    subtitle = obj["subtitle"]?.firstString("text"),
                    thumbnailUrl = obj.bestThumbnail(),
                )
            }
        return (fromRows + fromTiles).distinctBy { it.browseId }
    }

    /**
     * Страница артиста.
     *
     * Секции не разбираются по их заголовкам: YouTube называет их на языке
     * выдачи и регулярно переименовывает, так что «Albums» ловилось бы, а
     * «Альбомы» — нет. Вместо этого берём со страницы всё по типу объекта:
     * треки, релизы и похожих артистов.
     */
    fun artistPage(root: JsonElement, browseId: String): ArtistPage {
        val header =
            root.findAll("musicImmersiveHeaderRenderer").firstOrNull()
                ?: root.findAll("musicVisualHeaderRenderer").firstOrNull()
                ?: root.findAll("musicResponsiveHeaderRenderer").firstOrNull()
        return ArtistPage(
            browseId = browseId,
            name = header?.get("title")?.firstString("text") ?: "",
            subtitle = header?.firstString("subscriberCountText") ?: header?.get("subtitle")?.firstString("text"),
            thumbnailUrl = header?.bestThumbnail() ?: root.bestThumbnail(),
            songs = songs(root),
            releases = playlistCards(root),
            similar = artistCards(root).filterNot { it.browseId == browseId },
        )
    }

    /** Страница альбома: шапка и треклист. */
    fun albumPage(root: JsonElement, browseId: String): AlbumPage {
        val header =
            root.findAll("musicDetailHeaderRenderer").firstOrNull()
                ?: root.findAll("musicResponsiveHeaderRenderer").firstOrNull()
        val cover = header?.bestThumbnail() ?: root.bestThumbnail()
        // Подпись альбома — это «исполнитель • год • треков»: раны приходят
        // вперемешку с разделителями, поэтому склеиваем их сами.
        val subtitle =
            header
                ?.get("subtitle")
                ?.textRuns()
                ?.filter { it.isNotBlank() && it.trim() != "•" }
                ?.joinToString(" · ")
        return AlbumPage(
            browseId = browseId,
            title = header?.get("title")?.firstString("text") ?: "",
            subtitle = subtitle?.takeIf { it.isNotBlank() },
            thumbnailUrl = cover,
            // На странице альбома у строк нет своих картинок: обложка одна
            // на всех. Подставляем её, иначе треклист выглядит пустым.
            songs =
                songs(root).map { song ->
                    if (song.thumbnailUrl == null) {
                        song.copy(thumbnailUrl = cover, albumId = browseId)
                    } else {
                        song.copy(albumId = song.albumId ?: browseId)
                    }
                },
        )
    }

    /**
     * Очередь из ответа `next`: радио и автоплейлисты приходят другим
     * рендерером, не тем, которым отдаются списки и выдача поиска.
     */
    fun queueSongs(root: JsonElement): List<SongItem> =
        root
            .findAll("playlistPanelVideoRenderer")
            .mapNotNull { obj ->
                val videoId = obj.firstString("videoId") ?: return@mapNotNull null
                val title = obj["title"]?.firstString("text") ?: return@mapNotNull null
                val byline =
                    obj["longBylineText"]
                        ?.textRuns()
                        ?.filter { it.isNotBlank() && it.trim() != "•" }
                        .orEmpty()
                SongItem(
                    id = videoId,
                    title = title,
                    artist = byline.firstOrNull().orEmpty(),
                    album = byline.getOrNull(1),
                    durationText = obj["lengthText"]?.firstString("text"),
                    thumbnailUrl = obj.bestThumbnail(),
                    artistId = obj.browseIds().firstOrNull { it.startsWith("UC") },
                    albumId = obj.browseIds().firstOrNull { it.startsWith("MPRE") },
                )
            }.distinctBy { it.id }

    /**
     * Ленты главной страницы YouTube Music, каждая со своим заголовком.
     *
     * Заголовок берём как есть — он приходит на языке выдачи, и переводить
     * его самим значило бы врать о том, что именно рекомендовано.
     */
    fun shelves(root: JsonElement): List<Shelf> =
        (root.findAll("musicCarouselShelfRenderer") + root.findAll("musicShelfRenderer"))
            .mapNotNull { shelf ->
                val title =
                    shelf["header"]?.firstString("text")
                        ?: shelf["title"]?.firstString("text")
                        ?: return@mapNotNull null
                val songs = songs(shelf)
                val cards = playlistCards(shelf)
                val artists = artistCards(shelf)
                if (songs.isEmpty() && cards.isEmpty() && artists.isEmpty()) return@mapNotNull null
                Shelf(title = title, songs = songs, cards = cards, artists = artists)
            }.distinctBy { it.title }

    /** Все browseId в поддереве — в порядке появления. */
    fun JsonElement.browseIds(): List<String> =
        findAll("browseEndpoint").mapNotNull { (it["browseId"] as? JsonPrimitive)?.content }

    /** Самая крупная картинка в поддереве. */
    fun JsonElement.bestThumbnail(): String? {
        var best: Pair<String, Int>? = null
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val url = (element["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val width = (element["width"] as? JsonPrimitive)?.content?.toIntOrNull()
                    if (url != null && width != null && width > (best?.second ?: 0)) {
                        best = url to width
                    }
                    element.forEach { (_, value) -> walk(value) }
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return best?.first
    }

    /** Все текстовые раны поддерева подряд. */
    fun JsonElement.textRuns(): List<String> {
        val texts = mutableListOf<String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject ->
                    element.forEach { (name, value) ->
                        if (name == "text" && value is JsonPrimitive && value.isString) {
                            texts += value.content
                        } else {
                            walk(value)
                        }
                    }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return texts
    }

    /**
     * Плейлисты и альбомы.
     *
     * В карусели на странице артиста это плитки, а в выдаче поиска —
     * обычные строки списка. Разбираем оба вида: иначе раздел «альбомы»
     * в поиске оказывается пустым, хотя ответ пришёл полный.
     */
    fun playlistCards(root: JsonElement): List<PlaylistCard> {
        val fromRows =
            root.findAll("musicResponsiveListItemRenderer").mapNotNull { obj ->
                if (obj.firstString("videoId") != null) return@mapNotNull null
                val browseId =
                    obj.browseIds().firstOrNull {
                        it.startsWith("MPRE") || it.startsWith("VL")
                    } ?: return@mapNotNull null
                val columns = obj["flexColumns"]?.findAll("musicResponsiveListItemFlexColumnRenderer").orEmpty()
                PlaylistCard(
                    browseId = browseId,
                    title = columns.firstOrNull()?.firstString("text") ?: return@mapNotNull null,
                    subtitle =
                        columns
                            .drop(1)
                            .mapNotNull { it.firstString("text") }
                            .firstOrNull { it.isNotBlank() && it.trim() != "•" },
                    thumbnailUrl = obj.bestThumbnail(),
                )
            }
        return (tileCards(root) + fromRows).distinctBy { it.browseId }
    }

    private fun tileCards(root: JsonElement): List<PlaylistCard> =
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
                    thumbnailUrl = obj["thumbnailRenderer"]?.bestThumbnail(),
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
) {
    /** Альбомы и плейлисты открываются одинаково, но подписываются по-разному. */
    val isAlbum: Boolean get() = browseId.startsWith("MPRE")
}

/** Карточка артиста. */
data class ArtistCard(
    val browseId: String,
    val name: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
)

/** Страница артиста целиком. */
data class ArtistPage(
    val browseId: String,
    val name: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
    val releases: List<PlaylistCard> = emptyList(),
    val similar: List<ArtistCard> = emptyList(),
)

/** Лента на главной: заголовок и то, что в ней лежит. */
data class Shelf(
    val title: String,
    val songs: List<SongItem> = emptyList(),
    val cards: List<PlaylistCard> = emptyList(),
    val artists: List<ArtistCard> = emptyList(),
)

/** Страница альбома целиком. */
data class AlbumPage(
    val browseId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
)
