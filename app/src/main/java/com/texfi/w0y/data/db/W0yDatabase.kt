package com.texfi.w0y.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEntity::class,
        PinEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class W0yDatabase : RoomDatabase() {
    abstract fun dao(): W0yDao

    companion object {
        /**
         * Закрепления на главной и ссылки трека на артиста с альбомом.
         *
         * Настоящая миграция, а не пересоздание базы: в ней лежат плейлисты
         * и лайки, собранные руками, — терять их при обновлении нельзя.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN artistId TEXT")
                    db.execSQL("ALTER TABLE songs ADD COLUMN albumId TEXT")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS pins (
                            key TEXT NOT NULL PRIMARY KEY,
                            kind TEXT NOT NULL,
                            targetId TEXT NOT NULL,
                            title TEXT NOT NULL,
                            subtitle TEXT,
                            thumbnailUrl TEXT,
                            pinnedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
