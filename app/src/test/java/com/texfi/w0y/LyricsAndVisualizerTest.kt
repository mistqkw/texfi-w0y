package com.texfi.w0y

import com.texfi.w0y.data.LrcParser
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.data.VisualizerStyle
import com.texfi.w0y.ui.components.forUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разбор текста с пословными метками, поиск звучащей строки, стили визуализатора. */
class LyricsAndVisualizerTest {
    private val lrc =
        """
        [ar:Someone]
        [00:12.50]<00:12.50>first <00:13.10>line <00:13.90>here
        [00:05.00]intro
        [00:20.00]
        [01:02.3]last line
        """.trimIndent()

    @Test
    fun parsesLinesSortedWithoutEmpty() {
        val lines = LrcParser.parse(lrc)
        assertEquals(listOf(5_000L, 12_500L, 62_300L), lines.map { it.timeMs })
        assertEquals("first line here", lines[1].text)
        assertTrue(lines[0].words.isEmpty())
    }

    @Test
    fun parsesWordTimings() {
        val words = LrcParser.parse(lrc)[1].words
        assertEquals(listOf("first", "line", "here"), words.map { it.text })
        assertEquals(listOf(12_500L, 13_100L, 13_900L), words.map { it.timeMs })
    }

    @Test
    fun activeLineFollowsPosition() {
        val lines = LrcParser.parse(lrc)
        assertEquals(-1, LrcParser.activeIndex(lines, 1_000))
        assertEquals(0, LrcParser.activeIndex(lines, 5_000))
        assertEquals(1, LrcParser.activeIndex(lines, 30_000))
        assertEquals(2, LrcParser.activeIndex(lines, 999_000))
        assertEquals(-1, LrcParser.activeIndex(emptyList(), 10))
    }

    @Test
    fun visualizerStyleFitsTheLook() {
        VisualizerStyle.entries.forEach { style ->
            assertEquals(false, style.forUi(UiStyle.PIXEL).smooth)
            assertEquals(true, style.forUi(UiStyle.SMOOTH).smooth)
        }
        assertEquals(VisualizerStyle.RING, VisualizerStyle.RING.forUi(UiStyle.PIXEL))
        assertEquals(VisualizerStyle.WAVE, VisualizerStyle.WAVE.forUi(UiStyle.SMOOTH))
    }
}
