package com.texfi.w0y

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.StatsRepository
import com.texfi.w0y.data.db.W0yDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Миграция 5 → 6 не теряет историю: старые записи остаются засчитанными,
 * помечаются как «по старым правилам», и Room принимает схему.
 *
 * База версии 5 собирается по сохранённой схеме 5.json — ровно та, что
 * лежит у людей на телефонах с v0.0.1 beta-1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private fun schema(version: Int): JSONObject {
        val name = "com.texfi.w0y.data.db.W0yDatabase/$version.json"
        val file = listOf(File("schemas/$name"), File("app/schemas/$name")).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    @Test
    fun historySurvivesAndIsMarkedLegacy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val path = context.getDatabasePath("migration-test.db")
        path.parentFile?.mkdirs()
        path.delete()

        val v5 = schema(5)
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = v5.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = v5.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 5
            db.insert(
                "songs",
                null,
                ContentValues().apply {
                    put("id", "song1")
                    put("title", "t")
                    put("artist", "a")
                    put("durationText", "3:00")
                    put("explicit", 0)
                    put("liked", 0)
                    put("downloadState", 0)
                },
            )
            repeat(3) { n ->
                db.insert(
                    "history",
                    null,
                    ContentValues().apply {
                        put("songId", "song1")
                        put("playedAt", System.currentTimeMillis() - n * 60_000L)
                    },
                )
            }
        }

        val room =
            Room
                .databaseBuilder(context, W0yDatabase::class.java, path.absolutePath)
                .addMigrations(*W0yDatabase.ALL)
                .allowMainThreadQueries()
                .build()
        try {
            val dao = room.dao()
            val rows = dao.listensSince(0)
            assertEquals(3, rows.size)
            assertTrue(rows.all { it.legacy && it.counted && it.listenedMs == 0L })
            // Старые минуты оцениваются по длине трека: 3 × 3 минуты.
            assertEquals(9 * 60_000L, rows.sumOf(StatsRepository::msOf))
            assertEquals(3, dao.playsSince(0))
            val played = dao.mostPlayed(minPlays = 3).first()
            assertEquals(listOf("song1"), played.map { it.song.id })
        } finally {
            room.close()
            path.delete()
        }
    }
}
