package com.texfi.w0y.playback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.texfi.w0y.data.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.queueStore: DataStore<Preferences> by preferencesDataStore("queue")

/** Очередь, позиция в ней и время внутри трека — как их оставил пользователь. */
data class SavedQueue(val songs: List<SongItem>, val index: Int, val positionMs: Long)

/**
 * Очередь переживает перезапуск приложения.
 *
 * Хранится только то, что нужно, чтобы показать тот же трек на паузе и
 * продолжить с того же места: поток не запрашивается, пока человек сам
 * не нажмёт «играть». Любая ошибка разбора — это просто «очереди нет»,
 * а не падение на старте.
 */
@Singleton
class QueueStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun save(songs: List<SongItem>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) {
            context.queueStore.edit { it.remove(KEY) }
            return
        }
        val array = JSONArray()
        songs.take(MAX_SONGS).forEach { song ->
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album ?: JSONObject.NULL)
                    .put("thumb", song.thumbnailUrl ?: JSONObject.NULL)
                    .put("artistId", song.artistId ?: JSONObject.NULL)
                    .put("albumId", song.albumId ?: JSONObject.NULL)
                    .put("duration", song.durationText ?: JSONObject.NULL),
            )
        }
        val payload =
            JSONObject()
                .put("index", index.coerceIn(0, songs.lastIndex))
                .put("position", positionMs.coerceAtLeast(0L))
                .put("songs", array)
        context.queueStore.edit { it[KEY] = payload.toString() }
    }

    suspend fun load(): SavedQueue? =
        runCatching {
            val raw = context.queueStore.data.first()[KEY] ?: return null
            val obj = JSONObject(raw)
            val array = obj.getJSONArray("songs")
            val songs =
                (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    SongItem(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        artist = item.getString("artist"),
                        album = item.optStringOrNull("album"),
                        thumbnailUrl = item.optStringOrNull("thumb"),
                        artistId = item.optStringOrNull("artistId"),
                        albumId = item.optStringOrNull("albumId"),
                        durationText = item.optStringOrNull("duration"),
                    )
                }
            if (songs.isEmpty()) null else SavedQueue(songs, obj.optInt("index", 0).coerceIn(0, songs.lastIndex), obj.optLong("position", 0L))
        }.getOrNull()

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private companion object {
        val KEY = stringPreferencesKey("queue")

        /** Длиннее очередь не нужна: дальше это уже не очередь, а библиотека. */
        const val MAX_SONGS = 300
    }
}
