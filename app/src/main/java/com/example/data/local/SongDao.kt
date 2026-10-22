package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
  @Query("SELECT * FROM songs ORDER BY addedAt DESC")
  fun getAllSongs(): Flow<List<Song>>

  @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY addedAt DESC")
  fun getLikedSongs(): Flow<List<Song>>

  @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
  fun getRecentlyPlayed(limit: Int = 20): Flow<List<Song>>

  @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
  fun getMostPlayed(limit: Int = 20): Flow<List<Song>>

  @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
  fun getAllArtists(): Flow<List<String>>

  @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
  fun getSongsByArtist(artist: String): Flow<List<Song>>

  @Query("SELECT * FROM songs WHERE id = :songId")
  suspend fun getSongById(songId: String): Song?

  @Query("SELECT id FROM songs")
  suspend fun getAllSongIds(): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSongs(songs: List<Song>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSong(song: Song)

  @Update
  suspend fun updateSong(song: Song)

  @Query("UPDATE songs SET isLiked = :isLiked WHERE id = :songId")
  suspend fun updateLiked(songId: String, isLiked: Boolean)

  @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :songId")
  suspend fun incrementPlayCount(songId: String, timestamp: Long)

  @Query("UPDATE songs SET syncOffsetMs = :offset WHERE id = :songId")
  suspend fun updateSyncOffset(songId: String, offset: Long)

  @Query("SELECT * FROM songs WHERE id = :songId")
  fun getSongByIdFlow(songId: String): Flow<Song?>

  @Query("UPDATE songs SET lyricsText = :lyrics, hasLyrics = 1, isSyncedLyrics = :isSynced, lyricsSource = :source, lyricsFetchedAt = :fetchedAt, lyricsStatus = 'READY', lyricsError = NULL WHERE id = :songId")
  suspend fun updateLyricsSuccess(songId: String, lyrics: String, isSynced: Boolean, source: String, fetchedAt: Long)

  @Query("UPDATE songs SET lyricsStatus = 'NOT_FOUND', lyricsFetchedAt = :fetchedAt, lyricsError = :error WHERE id = :songId")
  suspend fun updateLyricsNotFound(songId: String, fetchedAt: Long, error: String)

  @Query("UPDATE songs SET lyricsStatus = :status, lyricsFetchedAt = :fetchedAt, lyricsError = :error WHERE id = :songId")
  suspend fun updateLyricsStatus(songId: String, status: String, fetchedAt: Long, error: String)

  @Query("UPDATE songs SET lyricsStatus = 'FETCHING' WHERE id = :songId")
  suspend fun setLyricsFetching(songId: String)

  @Query("SELECT * FROM songs WHERE hasLyrics = 0 AND lyricsStatus != 'SKIPPED_SHORT_AUDIO' AND ((lyricsStatus = 'ERROR' AND (:now - lyricsFetchedAt) > 3600000) OR (lyricsStatus = 'NOT_FOUND' AND (:now - lyricsFetchedAt) > 604800000) OR (lyricsStatus NOT IN ('ERROR', 'NOT_FOUND', 'SKIPPED_SHORT_AUDIO')))")
  suspend fun getSongsNeedingLyrics(now: Long = System.currentTimeMillis()): List<Song>

  @Query("UPDATE songs SET lyricsText = :lyrics, hasLyrics = 1, isSyncedLyrics = :isSynced, lyricsStatus = 'READY' WHERE id = :songId")
  suspend fun updateLyrics(songId: String, lyrics: String, isSynced: Boolean)

  @Query("UPDATE songs SET albumArtUri = :artUri WHERE id = :songId")
  suspend fun updateAlbumArt(songId: String, artUri: String)

  @Query("UPDATE songs SET title = :title, artist = :artist, album = :album WHERE id = :songId")
  suspend fun updateMetadata(songId: String, title: String, artist: String, album: String)

  @Query("DELETE FROM songs WHERE id IN (:missingIds)")
  suspend fun deleteSongsByIds(missingIds: List<String>)

  @Query("DELETE FROM songs WHERE id = :songId")
  suspend fun deleteSongById(songId: String)

  @Query("UPDATE songs SET lyricsText = NULL, hasLyrics = 0, isSyncedLyrics = 0")
  suspend fun clearAllLyrics()

  @Query("UPDATE songs SET albumArtUri = NULL")
  suspend fun clearAllAlbumArt()

  @Query("SELECT COUNT(*) FROM songs")
  suspend fun getSongCount(): Int
}
