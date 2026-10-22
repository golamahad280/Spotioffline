package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Playlist
import com.example.data.model.PlaylistSongCrossRef
import com.example.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
  @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
  fun getAllPlaylists(): Flow<List<Playlist>>

  @Query("SELECT * FROM playlists WHERE id = :playlistId")
  suspend fun getPlaylistById(playlistId: Long): Playlist?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylist(playlist: Playlist): Long

  @Query("DELETE FROM playlists WHERE id = :playlistId")
  suspend fun deletePlaylist(playlistId: Long)

  @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
  suspend fun clearPlaylistSongs(playlistId: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)

  @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
  suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

  @Query("""
    SELECT songs.* FROM songs
    INNER JOIN playlist_songs ON songs.id = playlist_songs.songId
    WHERE playlist_songs.playlistId = :playlistId
    ORDER BY playlist_songs.orderIndex ASC
  """)
  fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>>

  @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
  suspend fun getSongCountInPlaylist(playlistId: Long): Int
}
