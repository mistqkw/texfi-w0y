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
    version = 5,
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

        /**
         * «Твоя версия» трека: своя скорость, тон и эхо.
         *
         * Три столбца, а не таблица: метка версии нужна в каждом списке, а
         * списки и так возвращают строку трека целиком. NULL значит «играть
         * как все» — так у старых треков после обновления ничего не меняется.
         */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN speed REAL")
                    db.execSQL("ALTER TABLE songs ADD COLUMN pitch REAL")
                    db.execSQL("ALTER TABLE songs ADD COLUMN reverb TEXT")
                }
            }

        /**
         * Плейлисты аккаунта как свои: обложка, признак «можно править» и
         * снимок для двустороннего слияния. Старые плейлисты остаются как
         * были — правимые, без снимка (первая сверка его заведёт).
         */
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE playlists ADD COLUMN coverUrl TEXT")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN remoteEditable INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN syncBase TEXT")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** Метка «E»: её показывает список, поэтому хранится вместе с треком. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN explicit INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}
