package com.example

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

  @Test
  fun testMigration1To2And2To3() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // 1. Create SQLite DB with version 1 schema
    val dbName = "migration_test_db"
    context.deleteDatabase(dbName)

    val openHelper = FrameworkSQLiteOpenHelperFactory().create(
      androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
          override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
              """
              CREATE TABLE IF NOT EXISTS songs (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                durationMs INTEGER NOT NULL,
                uriString TEXT NOT NULL,
                filePath TEXT,
                albumArtUri TEXT,
                hasLyrics INTEGER NOT NULL DEFAULT 0,
                isSyncedLyrics INTEGER NOT NULL DEFAULT 0,
                lyricsText TEXT,
                isLiked INTEGER NOT NULL DEFAULT 0,
                playCount INTEGER NOT NULL DEFAULT 0,
                lastPlayedAt INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL DEFAULT 0,
                year INTEGER NOT NULL DEFAULT 0,
                trackNumber INTEGER NOT NULL DEFAULT 0,
                fileSize INTEGER NOT NULL DEFAULT 0,
                syncOffsetMs INTEGER NOT NULL DEFAULT 0
              )
              """.trimIndent()
            )
            db.execSQL(
              """
              CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                description TEXT,
                coverArtUri TEXT
              )
              """.trimIndent()
            )
            db.execSQL(
              """
              CREATE TABLE IF NOT EXISTS playlist_songs (
                playlistId INTEGER NOT NULL,
                songId TEXT NOT NULL,
                orderIndex INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(playlistId, songId)
              )
              """.trimIndent()
            )
          }

          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        })
        .build()
    )

    val db = openHelper.writableDatabase

    // Insert a test row in version 1
    db.execSQL(
      "INSERT INTO songs (id, title, artist, album, durationMs, uriString) VALUES ('test_1', 'Song A', 'Artist A', 'Album A', 120000, 'content://test/1')"
    )

    // 2. Apply MIGRATION_1_2
    AppDatabase.MIGRATION_1_2.migrate(db)

    // Verify columns from migration 1 -> 2
    val cursor1 = db.query("SELECT lyricsStatus, lyricsFetchedAt FROM songs WHERE id = 'test_1'")
    assertTrue(cursor1.moveToFirst())
    assertEquals("IDLE", cursor1.getString(0))
    assertEquals(0L, cursor1.getLong(1))
    cursor1.close()

    // 3. Apply MIGRATION_2_3
    AppDatabase.MIGRATION_2_3.migrate(db)

    // Verify columns from migration 2 -> 3
    val cursor2 = db.query("SELECT isHidden, titleLocked, artistLocked, coverLocked FROM songs WHERE id = 'test_1'")
    assertTrue(cursor2.moveToFirst())
    assertEquals(0, cursor2.getInt(0))
    assertEquals(0, cursor2.getInt(1))
    assertEquals(0, cursor2.getInt(2))
    assertEquals(0, cursor2.getInt(3))
    cursor2.close()

    // 4. Apply MIGRATION_3_4
    AppDatabase.MIGRATION_3_4.migrate(db)

    // Verify columns from migration 3 -> 4
    val cursor3 = db.query("SELECT coverStatus, coverFetchedAt, coverSource FROM songs WHERE id = 'test_1'")
    assertTrue(cursor3.moveToFirst())
    assertEquals("IDLE", cursor3.getString(0))
    assertEquals(0L, cursor3.getLong(1))
    assertEquals(null, cursor3.getString(2))
    cursor3.close()

    db.close()
  }
}
