package com.texfi.w0y.data

import android.content.Context
import androidx.room.Room
import com.texfi.w0y.data.db.W0yDao
import com.texfi.w0y.data.db.W0yDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): W0yDatabase =
        Room
            .databaseBuilder(context, W0yDatabase::class.java, "w0y.db")
            .addMigrations(
                W0yDatabase.MIGRATION_1_2,
                W0yDatabase.MIGRATION_2_3,
                W0yDatabase.MIGRATION_3_4,
                W0yDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    @Singleton
    fun dao(database: W0yDatabase): W0yDao = database.dao()
}
