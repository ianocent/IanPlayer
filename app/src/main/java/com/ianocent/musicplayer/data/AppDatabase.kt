package com.ianocent.musicplayer.data

import android.content.Context
import com.ianocent.musicplayer.data.canvas.CanvasCacheDao
import com.ianocent.musicplayer.data.canvas.CanvasCacheEntity
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedStreamUrl::class, LyricCacheEntry::class, CanvasCacheEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun streamCacheDao(): StreamCacheDao
    abstract fun lyricCacheDao(): LyricCacheDao
    abstract fun canvasCacheDao(): CanvasCacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ianplayer_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}