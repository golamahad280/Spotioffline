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
  @Query("SELECT * FROM songs WHERE isHidden = 0 ORDER BY addedAt DESC")
  fun getAllSongs(): Flow<List<Song>>

  @Query("SELECT * FROM songs WHERE isHidden = 0 AND isLiked = 1 ORDER BY addedAt DESC")
  fun getLikedSongs(): Flow<List<Song>>

  @Query("SELECT * FROM songs WHERE isHidden = 0 AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
  fun getRecentlyPlayed(limit: Int = 20): Flow<List<Song>>

  @Query("SELECT * FROM songs WHERE isHidden = 0 AND playCount > 0 ORDER BY playCount DESC LIMIT :limit")
  fun getMostPlayed(limit: Int = 20): Flow<List<Song>>

  @Query("SELECT DISTINCT artist FROM songs WHERE isHidden = 0 ORDER BY artist ASC")
  fun getAllArtists(): Flow<List<String>>

  @Query("SELECT * FROM songs WHERE isHidden = 0 AND artist = :artist ORDER BY title ASC")
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

  @Query("UPDATE songs SET lyricsText = :lyrics, hasLyrics = 1, isSyncedLyrics = :isSynced, lyricsSource = :source, lyricsFetchedAt = :fetchedAt, lyricsStatus = 'READY', lyricsError = NULL, lyricsOriginal = CASE WHEN lyricsOriginal IS NULL THEN :lyrics ELSE lyricsOriginal END, lyricsScript = :script WHERE id = :songId AND lyricsLocked = 0")
  suspend fun updateLyricsSuccess(songId: String, lyrics: String, isSynced: Boolean, source: String, fetchedAt: Long, script: String? = null)

  @Query("UPDATE songs SET lyricsText = :lyrics, hasLyrics = 1, isSyncedLyrics = :isSynced, lyricsSource = 'Manual', lyricsFetchedAt = :fetchedAt, lyricsStatus = 'READY', lyricsError = NULL, lyricsLocked = 1, lyricsOriginal = CASE WHEN lyricsOriginal IS NULL THEN :previousLyrics ELSE lyricsOriginal END, lyricsScript = :script WHERE id = :songId")
  suspend fun saveManualLyrics(
    songId: String,
    lyrics: String,
    isSynced: Boolean,
    previousLyrics: String?,
    script: String?,
    fetchedAt: Long = System.currentTimeMillis()
  )

  @Query("UPDATE songs SET lyricsText = lyricsOriginal, hasLyrics = CASE WHEN lyricsOriginal IS NOT NULL AND lyricsOriginal != '' THEN 1 ELSE 0 END, isSyncedLyrics = :isSynced, lyricsSource = 'Restored', lyricsStatus = 'READY', lyricsLocked = 0, lyricsScript = :script WHERE id = :songId")
  suspend fun restoreOriginalLyrics(
    songId: String,
    isSynced: Boolean,
    script: String?
  )

  @Query("UPDATE songs SET lyricsText = NULL, hasLyrics = 0, isSyncedLyrics = 0, lyricsStatus = 'IDLE', lyricsSource = 'Manual Deleted', lyricsLocked = 1, lyricsEnText = NULL, lyricsEnMode = NULL WHERE id = :songId")
  suspend fun deleteLyrics(songId: String)

  @Query("UPDATE songs SET lyricsEnText = :enText, lyricsEnMode = :mode WHERE id = :songId")
  suspend fun saveEnglishLyrics(songId: String, enText: String?, mode: String?)

  @Query("UPDATE songs SET lyricsText = :lyrics, hasLyrics = 1, isSyncedLyrics = :isSynced, lyricsSource = :source, lyricsFetchedAt = :fetchedAt, lyricsStatus = 'READY', lyricsError = NULL, lyricsOriginal = CASE WHEN lyricsOriginal IS NULL THEN :previousLyrics ELSE lyricsOriginal END, lyricsScript = :script WHERE id = :songId")
  suspend fun forceUpdateLyrics(
    songId: String,
    lyrics: String,
    isSynced: Boolean,
    source: String,
    fetchedAt: Long,
    previousLyrics: String?,
    script: String?
  )

  @Query("UPDATE songs SET lyricsStatus = 'NOT_FOUND', lyricsFetchedAt = :fetchedAt, lyricsError = :error WHERE id = :songId AND lyricsLocked = 0")
  suspend fun updateLyricsNotFound(songId: String, fetchedAt: Long, error: String)

  @Query("UPDATE songs SET lyricsStatus = :status, lyricsFetchedAt = :fetchedAt, lyricsError = :error WHERE id = :songId AND lyricsLocked = 0")
  suspend fun updateLyricsStatus(songId: String, status: String, fetchedAt: Long, error: String)

  @Query("UPDATE songs SET lyricsStatus = 'FETCHING' WHERE id = :songId AND lyricsLocked = 0")
  suspend fun setLyricsFetching(songId: String)

  @Query("SELECT * FROM songs WHERE lyricsLocked = 0 AND hasLyrics = 0 AND lyricsStatus != 'SKIPPED_SHORT_AUDIO' AND ((lyricsStatus = 'ERROR' AND (:now - lyricsFetchedAt) > 3600000) OR (lyricsStatus = 'NOT_FOUND' AND (:now - lyricsFetchedAt) > 604800000) OR (lyricsStatus NOT IN ('ERROR', 'NOT_FOUND', 'SKIPPED_SHORT_AUDIO')))")
  suspend fun getSongsNeedingLyrics(now: Long = System.currentTimeMillis()): List<Song>

  @Query("UPDATE songs SET lyricsText = :lyrics, hasLyrics = 1, isSyncedLyrics = :isSynced, lyricsStatus = 'READY' WHERE id = :songId AND lyricsLocked = 0")
  suspend fun updateLyrics(songId: String, lyrics: String, isSynced: Boolean)

  @Query("SELECT * FROM songs WHERE isHidden = 1 ORDER BY addedAt DESC")
  fun getHiddenSongs(): Flow<List<Song>>

  @Query("UPDATE songs SET isHidden = :isHidden WHERE id = :songId")
  suspend fun setHidden(songId: String, isHidden: Boolean)

  @Query("UPDATE songs SET isHidden = :isHidden WHERE id IN (:songIds)")
  suspend fun setHiddenBatch(songIds: List<String>, isHidden: Boolean)

  @Query("UPDATE songs SET title = :title, artist = :artist, titleLocked = :titleLocked, artistLocked = :artistLocked WHERE id = :songId")
  suspend fun updateSongMetadataLocked(
    songId: String,
    title: String,
    artist: String,
    titleLocked: Boolean = true,
    artistLocked: Boolean = true
  )

  @Query("UPDATE songs SET albumArtUri = :artUri, coverLocked = :coverLocked WHERE id = :songId")
  suspend fun updateCustomCoverLocked(songId: String, artUri: String?, coverLocked: Boolean = true)

  @Query("UPDATE songs SET albumArtUri = :artUri, coverSource = :source, coverStatus = 'READY', coverFetchedAt = :fetchedAt WHERE id = :songId")
  suspend fun updateCoverSuccess(songId: String, artUri: String, source: String, fetchedAt: Long)

  @Query("UPDATE songs SET coverStatus = :status, coverFetchedAt = :fetchedAt, coverSource = :source WHERE id = :songId")
  suspend fun updateCoverStatus(songId: String, status: String, fetchedAt: Long, source: String? = null)

  @Query("SELECT * FROM songs WHERE coverLocked = 0 AND (albumArtUri IS NULL OR albumArtUri = '') AND ((coverStatus = 'ERROR' AND (:now - coverFetchedAt) > 3600000) OR (coverStatus = 'NOT_FOUND' AND (:now - coverFetchedAt) > 604800000) OR (coverStatus NOT IN ('ERROR', 'NOT_FOUND')))")
  suspend fun getSongsNeedingCover(now: Long = System.currentTimeMillis()): List<Song>

  @Query("UPDATE songs SET albumArtUri = :artUri WHERE id = :songId")
  suspend fun updateAlbumArt(songId: String, artUri: String)

  @Query("UPDATE songs SET title = :title, artist = :artist, album = :album WHERE id = :songId")
  suspend fun updateMetadata(songId: String, title: String, artist: String, album: String)

  @Query("DELETE FROM songs WHERE id IN (:missingIds)")
  suspend fun deleteSongsByIds(missingIds: List<String>)

  @Query("DELETE FROM songs WHERE id = :songId")
  suspend fun deleteSongById(songId: String)

  @Query("UPDATE songs SET lyricsText = NULL, hasLyrics = 0, isSyncedLyrics = 0 WHERE lyricsLocked = 0")
  suspend fun clearAllLyrics()

  @Query("UPDATE songs SET albumArtUri = NULL WHERE coverLocked = 0")
  suspend fun clearAllAlbumArt()

  @Query("SELECT COUNT(*) FROM songs WHERE isHidden = 0")
  suspend fun getSongCount(): Int

  @Query("UPDATE songs SET playbackError = :error, lastErrorAt = :errorAt WHERE id = :songId")
  suspend fun updatePlaybackError(songId: String, error: Int, errorAt: Long)

  @Query("UPDATE songs SET playbackError = 0 WHERE id = :songId")
  suspend fun clearPlaybackError(songId: String)
}
