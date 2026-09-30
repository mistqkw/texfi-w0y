package com.texfi.w0y

import com.texfi.w0y.data.YtJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разбор ответов аккаунта на искусственных ответах: настоящий живёт только у владельца. */
class AccountJsonTest {
    private fun parse(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun avatarIsTakenFromAccountMenuAndUpscaled() {
        val menu =
            parse(
                """{"a":{"activeAccountHeaderRenderer":{"accountName":{"runs":[{"text":"Ник"}]},
                "accountPhoto":{"thumbnails":[{"url":"https://yt3.ggpht.com/x=s48-c-k-c0x00ffffff-no-rj","width":48,"height":48}]}}}}""",
            )
        assertEquals("https://yt3.ggpht.com/x=s192-c-k-c0x00ffffff-no-rj", YtJson.accountAvatar(menu))
    }

    @Test
    fun avatarFallsBackToLastThumbnailWithoutWidth() {
        val menu = parse("""{"accountPhoto":{"thumbnails":[{"url":"https://a/1"},{"url":"https://a/2"}]}}""")
        assertEquals("https://a/2", YtJson.accountAvatar(menu))
    }

    @Test
    fun noAvatarGivesNull() {
        assertNull(YtJson.accountAvatar(parse("""{"x":1}""")))
    }

    @Test
    fun ownPlaylistIsEditableAndKeepsItsCustomCover() {
        val page =
            parse(
                """{"h":{"musicEditablePlaylistDetailHeaderRenderer":{"header":{"musicDetailHeaderRenderer":
                {"thumbnail":{"croppedSquareThumbnailRenderer":{"thumbnail":{"thumbnails":[
                {"url":"https://c/small","width":60,"height":60},{"url":"https://c/custom","width":544,"height":544}]}}}}}}}}""",
            )
        val header = YtJson.playlistHeader(page)
        assertTrue(header.editable)
        assertTrue(header.found)
        assertEquals("https://c/custom", header.cover)
    }

    @Test
    fun savedPlaylistWithoutSetVideoIdsIsReadOnly() {
        val page =
            parse(
                """{"h":{"musicResponsiveHeaderRenderer":{"thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                {"url":"https://c/a","width":226,"height":226}]}}}}}}""",
            )
        val header = YtJson.playlistHeader(page)
        assertFalse(header.editable)
        assertEquals("https://c/a", header.cover)
    }

    @Test
    fun setVideoIdMakesPlaylistEditableEvenWithoutEditableHeader() {
        val page = parse("""{"row":{"playlistItemData":{"videoId":"v1","playlistSetVideoId":"s1"}}}""")
        assertTrue(YtJson.playlistHeader(page).editable)
        assertEquals(mapOf("v1" to "s1"), YtJson.setVideoIds(page))
    }

    @Test
    fun emptyAnswerHasNoHeader() {
        val header = YtJson.playlistHeader(parse("""{"error":{"code":404}}"""))
        assertFalse(header.found)
        assertFalse(header.editable)
        assertNull(header.cover)
    }
}
