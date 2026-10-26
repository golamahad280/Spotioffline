package com.example.playback

import com.example.data.model.Song
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SmartShuffleTest {

  private fun createSong(
    id: String,
    title: String,
    artist: String,
    durationMs: Long = 180000L,
    isLiked: Boolean = false,
    playCount: Int = 0,
    lastPlayedAt: Long = 0L
  ): Song {
    return Song(
      id = id,
      title = title,
      artist = artist,
      album = "Test Album",
      durationMs = durationMs,
      uriString = "file://test/$id",
      filePath = "/test/$id",
      isLiked = isLiked,
      playCount = playCount,
      lastPlayedAt = lastPlayedAt
    )
  }

  @Test
  fun testCurrentSongIsFirst() {
    val s1 = createSong("1", "Song 1", "Artist A")
    val s2 = createSong("2", "Song 2", "Artist B")
    val s3 = createSong("3", "Song 3", "Artist C")
    val list = listOf(s1, s2, s3)

    val shuffled = SmartShuffle.order(list, s2)
    assertEquals(s2.id, shuffled.first().id)
    assertEquals(3, shuffled.size)
    assertTrue(shuffled.map { it.id }.containsAll(list.map { it.id }))
  }

  @Test
  fun testNoDuplicatesInShuffledList() {
    val songs = (1..10).map { i ->
      createSong("$i", "Song $i", "Artist ${i % 3}")
    }
    val shuffled = SmartShuffle.order(songs, songs.first())
    assertEquals(songs.size, shuffled.size)
    assertEquals(songs.size, shuffled.distinctBy { it.id }.size)
  }

  @Test
  fun testSameArtistSpacing() {
    val s1 = createSong("1", "Song 1", "Artist A")
    val s2 = createSong("2", "Song 2", "Artist A")
    val s3 = createSong("3", "Song 3", "Artist B")
    val list = listOf(s1, s2, s3)

    val shuffled = SmartShuffle.order(list, s1, random = Random(42))
    assertEquals(s1.id, shuffled[0].id)
    assertEquals(s3.id, shuffled[1].id) // Artist B placed between Artist A songs
    assertEquals(s2.id, shuffled[2].id)
  }

  @Test
  fun testShortDurationSongsGoLast() {
    val s1 = createSong("1", "Long Song", "Artist A", durationMs = 200000L)
    val s2 = createSong("2", "Short Song", "Artist B", durationMs = 15000L) // < 30s
    val s3 = createSong("3", "Medium Song", "Artist C", durationMs = 150000L)
    val list = listOf(s1, s2, s3)

    val shuffled = SmartShuffle.order(list, s1)
    assertEquals(s1.id, shuffled.first().id)
    assertEquals(s2.id, shuffled.last().id) // Short song should be last
  }

  @Test
  fun testRecentlyPlayedSongsDeprioritized() {
    val now = System.currentTimeMillis()
    val s1 = createSong("1", "Current Song", "Artist A")
    val s2 = createSong("2", "Recently Played", "Artist B", lastPlayedAt = now - 1000L)
    val s3 = createSong("3", "Not Recently Played", "Artist C", lastPlayedAt = 0L)
    val list = listOf(s1, s2, s3)

    val shuffled = SmartShuffle.order(list, s1)
    assertEquals(s1.id, shuffled[0].id)
    assertEquals(s3.id, shuffled[1].id)
    assertEquals(s2.id, shuffled[2].id)
  }
}
