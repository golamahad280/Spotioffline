package com.example

import com.example.data.lyrics.LyricsPipeline
import com.example.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyricsPipelineTest {

  // Test helper with dummy parameters
  private val pipeline by lazy {
    // Can test utility methods directly without Android context
    LyricsPipeline(
      context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
      songDao = object : com.example.data.local.SongDao {
        override fun getAllSongs() = kotlinx.coroutines.flow.flowOf(emptyList<Song>())
        override fun getLikedSongs() = kotlinx.coroutines.flow.flowOf(emptyList<Song>())
        override fun getRecentlyPlayed(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<Song>())
        override fun getMostPlayed(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<Song>())
        override fun getAllArtists() = kotlinx.coroutines.flow.flowOf(emptyList<String>())
        override fun getSongsByArtist(artist: String) = kotlinx.coroutines.flow.flowOf(emptyList<Song>())
        override suspend fun getSongById(songId: String): Song? = null
        override suspend fun getAllSongIds(): List<String> = emptyList()
        override suspend fun insertSongs(songs: List<Song>) {}
        override suspend fun insertSong(song: Song) {}
        override suspend fun updateSong(song: Song) {}
        override suspend fun updateLiked(songId: String, isLiked: Boolean) {}
        override suspend fun incrementPlayCount(songId: String, timestamp: Long) {}
        override suspend fun updateSyncOffset(songId: String, offset: Long) {}
        override fun getSongByIdFlow(songId: String) = kotlinx.coroutines.flow.flowOf(null)
        override suspend fun updateLyricsSuccess(songId: String, lyrics: String, isSynced: Boolean, source: String, fetchedAt: Long, script: String?) {}
        override suspend fun saveManualLyrics(songId: String, lyrics: String, isSynced: Boolean, previousLyrics: String?, script: String?, fetchedAt: Long) {}
        override suspend fun restoreOriginalLyrics(songId: String, isSynced: Boolean, script: String?) {}
        override suspend fun deleteLyrics(songId: String) {}
        override suspend fun saveEnglishLyrics(songId: String, enText: String?, mode: String?) {}
        override suspend fun forceUpdateLyrics(songId: String, lyrics: String, isSynced: Boolean, source: String, fetchedAt: Long, previousLyrics: String?, script: String?) {}
        override suspend fun updateLyricsNotFound(songId: String, fetchedAt: Long, error: String) {}
        override suspend fun updateLyricsStatus(songId: String, status: String, fetchedAt: Long, error: String) {}
        override suspend fun setLyricsFetching(songId: String) {}
        override suspend fun getSongsNeedingLyrics(now: Long) = emptyList<Song>()
        override suspend fun updateLyrics(songId: String, lyrics: String, isSynced: Boolean) {}
        override suspend fun updateAlbumArt(songId: String, artUri: String) {}
        override suspend fun updateMetadata(songId: String, title: String, artist: String, album: String) {}
        override suspend fun deleteSongsByIds(missingIds: List<String>) {}
        override suspend fun deleteSongById(songId: String) {}
        override suspend fun clearAllLyrics() {}
        override suspend fun clearAllAlbumArt() {}
        override fun getHiddenSongs() = kotlinx.coroutines.flow.flowOf(emptyList<Song>())
        override suspend fun setHidden(songId: String, isHidden: Boolean) {}
        override suspend fun setHiddenBatch(songIds: List<String>, isHidden: Boolean) {}
        override suspend fun updateSongMetadataLocked(songId: String, title: String, artist: String, titleLocked: Boolean, artistLocked: Boolean) {}
        override suspend fun updateCustomCoverLocked(songId: String, artUri: String?, coverLocked: Boolean) {}
        override suspend fun updateCoverSuccess(songId: String, artUri: String, source: String, fetchedAt: Long) {}
        override suspend fun updateCoverStatus(songId: String, status: String, fetchedAt: Long, source: String?) {}
        override suspend fun getSongsNeedingCover(now: Long): List<Song> = emptyList()
        override suspend fun getSongCount(): Int = 0
        override suspend fun updatePlaybackError(songId: String, error: Int, errorAt: Long) {}
        override suspend fun clearPlaybackError(songId: String) {}
      }
    )
  }

  @Test
  fun `cleanFilenameOrTitle cleans Set Fire to the Rain 128k correctly`() {
    val raw = "Set_Fire_to_the_Rain(128k)"
    val cleaned = pipeline.cleanFilenameOrTitle(raw)
    assertEquals("Set Fire to the Rain", cleaned)
  }

  @Test
  fun `cleanFilenameOrTitle cleans bitrates tags and extensions`() {
    assertEquals("Set Fire to the Rain", pipeline.cleanFilenameOrTitle("01 - Set_Fire_to_the_Rain(128k).mp3"))
    assertEquals("Blinding Lights", pipeline.cleanFilenameOrTitle("Blinding_Lights [Official Video] (320kbps).flac"))
    assertEquals("Hello", pipeline.cleanFilenameOrTitle("Hello - Remastered 2021.m4a"))
  }

  @Test
  fun `cleanFilenameOrTitle cleans Bengali YouTube filename correctly`() {
    val raw = "আমার_ভিতর_বাহিরে___সোমলতা_আচার্য্য_চৌধুরী___Lyrical_Video___Somlata_and_The_Aces(128k)"
    val cleaned = pipeline.cleanFilenameOrTitle(raw)
    assertEquals("আমার ভিতর বাহিরে", cleaned)
  }

  @Test
  fun `isGenericOrFolderMetadata detects generic artists and folder names`() {
    assertTrue(pipeline.isGenericOrFolderMetadata("Unknown Artist"))
    assertTrue(pipeline.isGenericOrFolderMetadata("<unknown>"))
    assertTrue(pipeline.isGenericOrFolderMetadata("ShareMe"))
    assertTrue(pipeline.isGenericOrFolderMetadata("Download"))
    assertTrue(pipeline.isGenericOrFolderMetadata("Music"))
    assertTrue(pipeline.isGenericOrFolderMetadata("Hangouts Call"))
    assertTrue(pipeline.isGenericOrFolderMetadata(""))
    assertTrue(pipeline.isGenericOrFolderMetadata(null))

    assertFalse(pipeline.isGenericOrFolderMetadata("Adele"))
    assertFalse(pipeline.isGenericOrFolderMetadata("The Weeknd"))
    assertFalse(pipeline.isGenericOrFolderMetadata("Coldplay"))
  }

  @Test
  fun `verifyTitleOrigin correctly validates origin of title`() {
    val song = Song(
      id = "test_song_1",
      title = "Set_Fire_to_the_Rain(128k)",
      artist = "Unknown Artist",
      album = "ShareMe",
      durationMs = 242000L,
      uriString = "content://audio/1",
      filePath = "/storage/emulated/0/ShareMe/Set_Fire_to_the_Rain(128k).mp3"
    )

    // Should pass for "Set Fire to the Rain"
    assertTrue(pipeline.verifyTitleOrigin("Set Fire to the Rain", song))

    // Should fail for an unrelated song like "Hangouts Call"
    assertFalse(pipeline.verifyTitleOrigin("Hangouts Call", song))
  }
}
