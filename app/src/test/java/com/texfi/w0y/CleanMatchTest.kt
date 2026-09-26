package com.texfi.w0y

import com.texfi.w0y.data.CleanMatch
import com.texfi.w0y.data.Profanity
import com.texfi.w0y.data.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила режима «без мата». Проверяются без сети: ошибается тут не
 * YouTube, а наше сравнение названий.
 */
class CleanMatchTest {
    private val original =
        SongItem(id = "1", title = "Rap God (feat. X)", artist = "Eminem", explicit = true)

    @Test
    fun picksCleanVersionOfSameSong() {
        val found =
            CleanMatch.pick(
                original,
                listOf(
                    SongItem(id = "2", title = "Rap God", artist = "Eminem", explicit = true),
                    SongItem(id = "3", title = "Rap God [Clean]", artist = "Eminem", explicit = false),
                ),
            )
        assertEquals("3", found?.id)
    }

    @Test
    fun ignoresOtherSongsAndCovers() {
        val found =
            CleanMatch.pick(
                original,
                listOf(
                    SongItem(id = "4", title = "Lose Yourself", artist = "Eminem"),
                    SongItem(id = "5", title = "Rap God", artist = "Karaoke Band"),
                ),
            )
        assertNull("Подобрал чужую песню или кавер", found)
    }

    @Test
    fun neverReturnsExplicitOrSameTrack() {
        assertNull(CleanMatch.pick(original, listOf(original)))
        assertNull(
            CleanMatch.pick(
                original,
                listOf(SongItem(id = "6", title = "Rap God", artist = "Eminem", explicit = true)),
            ),
        )
    }

    @Test
    fun normalizeDropsBracketsAndPunctuation() {
        assertEquals("rap god", CleanMatch.normalize("Rap God (feat. X) [Clean]!"))
    }

    @Test
    fun profanityCatchesFormsAndSkipsCleanLines() {
        assertTrue(Profanity.inText("ну ты и сукаа"))
        assertTrue(Profanity.inText("What the fuck"))
        assertTrue(Profanity.inText("охуенно вышло"))
        assertFalse(Profanity.inText("солнце светит ярко"))
        assertFalse(Profanity.inText("I love you"))
    }
}
