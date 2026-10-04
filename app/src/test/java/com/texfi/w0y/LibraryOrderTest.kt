package com.texfi.w0y

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.db.HistoryEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Быстрый набор по порогу прослушиваний и сохранение порядка плейлиста. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryOrderTest {
    private lateinit var db: W0yDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), W0yDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun song(id: String) = db.dao().upsertSong(SongEntity(id = id, title = id, artist = "a"))

    private suspend fun listen(id: String, at: Long, counted: Boolean = true) =
        db.dao().addHistory(HistoryEntity(songId = id, playedAt = at, listenedMs = 100_000, counted = counted, legacy = false))

    @Test
    fun dialNeedsThresholdAndSortsByPlaysThenRecency() = runBlocking {
        listOf("x", "y", "z", "w").forEach { song(it) }
        repeat(3) { listen("x", 100L + it) }
        repeat(3) { listen("y", 200L + it) } // столько же, но позже — выше x
        repeat(5) { listen("z", 10L + it) }
        repeat(2) { listen("w", 900L + it) } // мало — в набор не попадает
        repeat(4) { listen("w", 950L + it, counted = false) } // незасчитанные не считаются
        val ids = db.dao().mostPlayed(minPlays = 3).first().map { it.song.id }
        assertEquals(listOf("z", "y", "x"), ids)
        assertEquals(listOf("z", "y", "x", "w"), db.dao().mostPlayed(minPlays = 1).first().map { it.song.id })
    }

    @Test
    fun playlistOrderPersists() = runBlocking {
        val dao = db.dao()
        val id = dao.createPlaylist(PlaylistEntity(name = "p", createdAt = 0))
        listOf("a", "b", "c").forEachIndexed { i, s ->
            dao.addSongToPlaylist(id, SongEntity(id = s, title = s, artist = "a"), now = i.toLong())
        }
        dao.reorderPlaylist(id, listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), dao.playlistSongs(id).first().map { it.id })
    }
}
