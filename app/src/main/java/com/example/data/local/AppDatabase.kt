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
  version = 5,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun songDao(): SongDao
  abstract fun playlistDao(): PlaylistDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsSource TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsFetchedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsStatus TEXT NOT NULL DEFAULT 'IDLE'")
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsError TEXT DEFAULT NULL")
      }
    }

    val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN titleLocked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN artistLocked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN coverLocked INTEGER NOT NULL DEFAULT 0")
      }
    }

    val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN coverStatus TEXT NOT NULL DEFAULT 'IDLE'")
        db.execSQL("ALTER TABLE songs ADD COLUMN coverFetchedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN coverSource TEXT DEFAULT NULL")
      }
    }

    val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN playbackError INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN lastErrorAt INTEGER NOT NULL DEFAULT 0")
      }
    }

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "spoti_offline_database"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
