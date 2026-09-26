package com.texfi.w0y

import com.texfi.w0y.data.YtJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Разбор подписи трека на кусках, которые YouTube реально присылает.
 *
 * Без сети: живые тесты ловят смену формата, а эти — разбор уже известных
 * форм. Ленту главной, где подпись состоит из одного счётчика
 * прослушиваний, живым запросом поймать не удаётся — она приходит не
 * каждый раз, и проверять её можно только так.
 */
class BylineParseTest {
    private fun song(byline: List<String>): com.texfi.w0y.data.SongItem {
        val runs = byline.joinToString(",") { """{"text":${Json.encodeToString(it)}}""" }
        val payload =
            """
            {"contents":[{"musicResponsiveListItemRenderer":{
              "playlistItemData":{"videoId":"abc12345678"},
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Название"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[$runs]}}}
              ]
            }}]}
            """.trimIndent()
        val parsed = YtJson.songs(Json.parseToJsonElement(payload) as JsonObject)
        assertEquals("Трек не разобрался", 1, parsed.size)
        return parsed.first()
    }

    @Test
    fun splitsArtistAlbumAndDuration() {
        val item = song(listOf("Kai Angel", " • ", "andy warhol", " • ", "3:19"))
        assertEquals("Kai Angel", item.artist)
        assertEquals("andy warhol", item.album)
        assertEquals("3:19", item.durationText)
        assertNull(item.plays)
    }

    @Test
    fun keepsMultipleArtistsTogether() {
        // Соисполнители приходят отдельными ранами вместе с разделителями,
        // и союз «и» когда-то уезжал в поле альбома.
        val item = song(listOf("Kai Angel", ", ", "9mice", " и ", "OG Buda", " • ", "2:41"))
        assertEquals("Kai Angel, 9mice и OG Buda", item.artist)
        assertNull(item.album)
        assertEquals("2:41", item.durationText)
    }

    @Test
    fun playsAreNotAnArtistName() {
        // Ровно тот случай с главного экрана: вся подпись — счётчик.
        val item = song(listOf("5.2M plays"))
        assertEquals("", item.artist)
        assertEquals("5.2M plays", item.plays)
        assertNull(item.album)
    }

    @Test
    fun readsPlaysNextToArtist() {
        val item = song(listOf("Минин", " • ", "1.4M прослушиваний"))
        assertEquals("Минин", item.artist)
        assertEquals("1.4M прослушиваний", item.plays)
    }
}
