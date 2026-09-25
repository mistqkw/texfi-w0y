package com.texfi.w0y.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class W0yDatabase : RoomDatabase() {
    abstract fun dao(): W0yDao
}
