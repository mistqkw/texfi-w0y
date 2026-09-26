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

private val Context.searchStore: DataStore<Preferences> by preferencesDataStore("searches")

/**
 * Последние запросы.
 *
 * Нужны двум вещам сразу: показать недавнее под пустым полем поиска и
 * подсказать рекомендациям, чем человек интересовался, — по одному этому
 * сигналу «бета» уже угадывает заметно лучше, чем по чистой выдаче YouTube.
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val recent: Flow<List<String>> =
        context.searchStore.data.map { prefs ->
            prefs[Keys.RECENT].orEmpty().split(SEPARATOR).filter { it.isNotBlank() }
        }

    suspend fun remember(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_LENGTH) return
        val current = recent.first()
        // Повтор поднимается наверх, а не задваивается: список коротких
        // запросов иначе мгновенно забивается одним и тем же словом.
        val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(LIMIT)
        context.searchStore.edit { it[Keys.RECENT] = updated.joinToString(SEPARATOR) }
    }

    suspend fun clear() = context.searchStore.edit { it.remove(Keys.RECENT) }

    private object Keys {
        val RECENT = stringPreferencesKey("recent")
    }

    private companion object {
        const val SEPARATOR = "\n"
        const val LIMIT = 20

        /** Одна-две буквы — это не запрос, а промежуточное состояние набора. */
        const val MIN_LENGTH = 2
    }
}
