package com.texfi.w0y.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dialHiddenStore: DataStore<Preferences> by preferencesDataStore("dial_hidden")

/** Запись о плитке, убранной из быстрого набора. */
data class HiddenDial(
    val kind: String,
    val id: String,
    val title: String,
    /** Когда убрана, мс от эпохи. */
    val hiddenAtMs: Long,
)

/**
 * Убранное из быстрого набора.
 *
 * Плитка просто пропадает; списка «скрытого» нигде нет. Трек возвращается
 * сам, когда его снова включают ([LibraryRepository.remember]), а плейлист,
 * альбом или артист — когда их снова закрепляют. Из библиотеки, истории,
 * лайков и плейлистов ничего не удаляется. Хранится локально и переживает
 * перезапуск.
 */
@Singleton
class DialHiddenRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val hidden: Flow<List<HiddenDial>> =
        context.dialHiddenStore.data.map { prefs -> decode(prefs[KEY].orEmpty()) }

    suspend fun hide(kind: String, id: String, title: String) {
        context.dialHiddenStore.edit { prefs ->
            val kept = decode(prefs[KEY].orEmpty()).filterNot { it.kind == kind && it.id == id }
            prefs[KEY] = encode(kept + HiddenDial(kind, id, title, System.currentTimeMillis()))
        }
    }

    suspend fun restore(kind: String, id: String) {
        // Обычный случай — запись не скрыта: не трогаем хранилище зря.
        if (hidden.first().none { it.kind == kind && it.id == id }) return
        context.dialHiddenStore.edit { prefs ->
            prefs[KEY] = encode(decode(prefs[KEY].orEmpty()).filterNot { it.kind == kind && it.id == id })
        }
    }

    private fun encode(items: List<HiddenDial>): String =
        items.joinToString("\n") { "${it.kind}\t${it.id}\t${it.hiddenAtMs}\t${it.title.replace('\t', ' ').replace('\n', ' ')}" }

    private fun decode(raw: String): List<HiddenDial> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 4)
            if (parts.size < 4) return@mapNotNull null
            // Старые записи хранили здесь срок возврата — число всё равно годится.
            val at = parts[2].toLongOrNull() ?: return@mapNotNull null
            HiddenDial(parts[0], parts[1], parts[3], at)
        }.toList()

    private companion object {
        val KEY = stringPreferencesKey("hidden")
    }
}
