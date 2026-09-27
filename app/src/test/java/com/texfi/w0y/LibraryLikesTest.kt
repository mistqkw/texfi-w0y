package com.texfi.w0y

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.innertubex.InnerTube
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.texfi.w0y.data.AccountRepository
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.SoundProfile
import com.texfi.w0y.data.YtPlaylistSync
import com.texfi.w0y.data.db.W0yDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Лайк принадлежит пользователю и не должен исчезать сам.
 *
 * Тест появился по живому случаю: раздел «лайки» оказывался пустым при
 * девяти записях в истории. Причина — трек перезаписывался целиком при
 * каждом сохранении, и флаг лайка обнулялся повторным прослушиванием.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryLikesTest {
    private lateinit var db: W0yDatabase
    private lateinit var library: LibraryRepository

    private val song =
        SongItem(
            id = "abc12345678",
            title = "andy warhol",
            artist = "Kai Angel",
            album = "ЖИЗНЬ",
            durationText = "2:56",
            explicit = true,
        )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, W0yDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        // Зеркалирование собрано настоящее, а не заглушкой: без входа в
        // аккаунт оно обязано ничего не делать, и это тоже часть проверки —
        // ни один тест здесь в сеть не ходит.
        val innerTube = InnerTube(httpClient = HttpClient(OkHttp))
        library =
            LibraryRepository(
                dao = db.dao(),
                sync =
                    YtPlaylistSync(
                        innerTube = innerTube,
                        account = AccountRepository(context, innerTube),
                        settings = SettingsRepository(context),
                    ),
            )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun likeSurvivesListeningAgain() = runBlocking {
        library.toggleLike(song)
        // Из плеера трек приходит без альбома и длительности — ровно так
        // его и сохраняет история.
        library.remember(song.copy(album = null, durationText = null))

        val liked = library.liked.first()
        assertEquals(listOf(song.id), liked.map(SongItem::id))
        // И метаданные при этом не обеднели.
        assertEquals("ЖИЗНЬ", liked.first().album)
        assertEquals("2:56", liked.first().durationText)
        assertTrue("Метка E потерялась", liked.first().explicit)
    }

    @Test
    fun likeSurvivesAddingToPlaylist() = runBlocking {
        library.toggleLike(song)
        val playlistId = library.createPlaylist("Свой")
        library.addToPlaylist(playlistId, song)

        assertEquals(listOf(song.id), library.liked.first().map(SongItem::id))
    }

    @Test
    fun accountLikesLandInTheLikedSection() = runBlocking {
        // Синхронизация раньше только сохраняла треки, не отмечая лайк.
        library.importLikes(listOf(song))

        assertEquals(listOf(song.id), library.liked.first().map(SongItem::id))
    }

    @Test
    fun syncDoesNotDropLocalLikes() = runBlocking {
        val own = song.copy(id = "local999", title = "Свой лайк")
        library.toggleLike(own)
        library.importLikes(listOf(song))

        val ids = library.liked.first().map(SongItem::id).toSet()
        assertEquals(setOf(own.id, song.id), ids)
    }

    /**
     * «Своя версия» трека — такая же собственность пользователя, как лайк:
     * подобрал скорость один раз и она обязана дождаться следующего раза.
     * Проверка отдельная, потому что механика та же самая, на которой лайк
     * уже один раз терялся: повторное прослушивание перезаписывает трек.
     */
    @Test
    fun soundSurvivesListeningAgain() = runBlocking {
        val mine = SoundProfile(speed = 0.85f, pitch = 0.94f, reverb = Reverb.HALL)
        library.setSound(song, mine)
        library.remember(song.copy(album = null, durationText = null))

        assertEquals(mine, library.soundOnce(song.id))
    }

    @Test
    fun soundComesBackWithTheTrack() = runBlocking {
        val mine = SoundProfile(speed = 1.25f, pitch = 1f, reverb = Reverb.OFF)
        library.setSound(song, mine)
        library.toggleLike(song)

        assertEquals(mine, library.liked.first().first().sound)
    }

    @Test
    fun plainTrackHasNoVersion() = runBlocking {
        library.remember(song)

        assertNull("У обычного трека не должно появляться своей версии", library.soundOnce(song.id))
    }
}
