package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
  @PrimaryKey val id: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val uriString: String,
  val filePath: String? = null,
  val albumArtUri: String? = null,
  val hasLyrics: Boolean = false,
  val isSyncedLyrics: Boolean = false,
  val lyricsText: String? = null,
  val isLiked: Boolean = false,
  val playCount: Int = 0,
  val lastPlayedAt: Long = 0L,
  val addedAt: Long = System.currentTimeMillis(),
  val year: Int = 0,
  val trackNumber: Int = 0,
  val fileSize: Long = 0L,
  val syncOffsetMs: Long = 0L,
  val lyricsSource: String? = null,
  val lyricsFetchedAt: Long = 0L,
  val lyricsStatus: String = "IDLE",
  val lyricsError: String? = null,
  val isHidden: Boolean = false,
  val titleLocked: Boolean = false,
  val artistLocked: Boolean = false,
  val coverLocked: Boolean = false,
  val coverStatus: String = "IDLE",
  val coverFetchedAt: Long = 0L,
  val coverSource: String? = null,
  val playbackError: Int = 0,
  val lastErrorAt: Long = 0L,
  val lyricsLocked: Boolean = false,
  val lyricsOriginal: String? = null,
  val lyricsScript: String? = null,
  val lyricsEnText: String? = null,
  val lyricsEnMode: String? = null
)

@Entity(tableName = "playlists")
data class Playlist(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAt: Long = System.currentTimeMillis(),
  val description: String? = null,
  val coverArtUri: String? = null
)

@Entity(
  tableName = "playlist_songs",
  primaryKeys = ["playlistId", "songId"]
)
data class PlaylistSongCrossRef(
  val playlistId: Long,
  val songId: String,
  val orderIndex: Int = 0
)

data class LrcLine(
  val timestampMs: Long,
  val text: String
)

data class ScanProgress(
  val totalFiles: Int = 0,
  val scannedFiles: Int = 0,
  val currentFileName: String = "",
  val isScanning: Boolean = false
)

enum class RepeatMode {
  OFF,
  ALL,
  ONE
}

enum class SortOption {
  RECENTLY_ADDED,
  TITLE_ASC,
  TITLE_DESC,
  ARTIST_ASC,
  DURATION_DESC,
  MOST_PLAYED
}
