package com.texfi.w0y

import com.texfi.w0y.data.EditKind
import com.texfi.w0y.data.EditMatch
import com.texfi.w0y.data.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Правило выбора готовой переделки — без сети, на выдуманной выдаче. */
class EditMatchTest {
    private val original = SongItem(id = "orig0000001", title = "andy warhol", artist = "Kai Angel")

    private fun item(id: String, title: String, artist: String = "slowed vibes") =
        SongItem(id = id, title = title, artist = artist)

    @Test
    fun picksTheSlowedEditOfTheSameSong() {
        val found =
            EditMatch.pick(
                original,
                EditKind.SLOWED,
                listOf(
                    original,
                    item("other000001", "Kai Angel - другая песня (slowed)"),
                    item("edit0000001", "Kai Angel - andy warhol (slowed + reverb)"),
                ),
            )
        assertEquals("edit0000001", found?.id)
    }

    @Test
    fun prefersTheOneThatNamesTheArtist() {
        val found =
            EditMatch.pick(
                original,
                EditKind.SPED_UP,
                listOf(
                    item("anon0000001", "andy warhol sped up"),
                    item("named000001", "andy warhol (sped up)", artist = "Kai Angel"),
                ),
            )
        assertEquals("named000001", found?.id)
    }

    @Test
    fun theOriginalIsNotAnEdit() {
        val found = EditMatch.pick(original, EditKind.SLOWED, listOf(original, item("x0000000001", "andy warhol")))
        assertNull(found)
    }

    @Test
    fun slowedIsNotSpedUp() {
        val found = EditMatch.pick(original, EditKind.SPED_UP, listOf(item("s0000000001", "andy warhol slowed")))
        assertNull(found)
    }
}
