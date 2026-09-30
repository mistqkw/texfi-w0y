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

/** Запись о треке, альбоме или артисте, убранном из быстрого набора на время. */
data class HiddenDial(
    val kind: String,
    val id: String,
    val title: String,
    /** Когда запись вернётся в набор сама, мс от эпохи. */
    val untilMs: Long,
)

/**
 * Скрытое из быстрого набора — не навсегда.
 *
 * Плитка пропадает из набора на заданный срок и возвращается сама; из
 * библиотеки, истории, лайков и плейлистов ничего не удаляется. Хранится
 * отдельно, локально, и переживает перезапуск. Просроченные записи
 * отфильтровывает тот, кто читает: таймер для этого не нужен.
 */
@Singleton
class DialHiddenRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    val hidden: Flow<List<HiddenDial>> =
        context.dialHiddenStore.data.map { prefs -> decode(prefs[KEY].orEmpty()) }

    suspend fun hide(kind: String, id: String, title: String) {
        val days = settings.settings.first().dialHideDays
        val until = System.currentTimeMillis() + days * DAY_MS
        context.dialHiddenStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val kept = decode(prefs[KEY].orEmpty()).filter { it.untilMs > now && !(it.kind == kind && it.id == id) }
            prefs[KEY] = encode(kept + HiddenDial(kind, id, title, until))
        }
    }

    suspend fun restore(kind: String, id: String) {
        context.dialHiddenStore.edit { prefs ->
            prefs[KEY] = encode(decode(prefs[KEY].orEmpty()).filterNot { it.kind == kind && it.id == id })
        }
    }

    suspend fun restoreAll() {
        context.dialHiddenStore.edit { it.remove(KEY) }
    }

    private fun encode(items: List<HiddenDial>): String =
        items.joinToString("\n") { "${it.kind}\t${it.id}\t${it.untilMs}\t${it.title.replace('\t', ' ').replace('\n', ' ')}" }

    private fun decode(raw: String): List<HiddenDial> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 4)
            if (parts.size < 4) return@mapNotNull null
            val until = parts[2].toLongOrNull() ?: return@mapNotNull null
            HiddenDial(parts[0], parts[1], parts[3], until)
        }.toList()

    private companion object {
        val KEY = stringPreferencesKey("hidden")
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
