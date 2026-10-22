package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Playlist
import com.example.data.model.PlaylistSongCrossRef
import com.example.data.model.Song

@Database(
  entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class],
  version = 2,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun songDao(): SongDao
  abstract fun playlistDao(): PlaylistDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsSource TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsFetchedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsStatus TEXT NOT NULL DEFAULT 'IDLE'")
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsError TEXT DEFAULT NULL")
      }
    }

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "spoti_offline_database"
        )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
