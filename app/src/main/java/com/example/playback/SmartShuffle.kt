package com.example.playback

import com.example.data.model.Song
import kotlin.random.Random

object SmartShuffle {

  /**
   * Generates a smart-shuffled order for the given list of songs.
   *
   * @param songs The full list of songs to shuffle.
   * @param currentSong The song currently playing, which will stay at position 0 if present.
   * @param random Random instance for weighted shuffling (can be seeded for testing).
   */
  fun order(
    songs: List<Song>,
    currentSong: Song?,
    random: Random = Random.Default
  ): List<Song> {
    val validSongs = songs.filter { it.playbackError == 0 }
    if (validSongs.isEmpty()) return emptyList()

    val current = validSongs.firstOrNull { it.id == currentSong?.id }
    val candidates = if (current != null) {
      validSongs.filter { it.id != current.id }
    } else {
      validSongs.toList()
    }

    if (candidates.isEmpty()) {
      return if (current != null) listOf(current) else emptyList()
    }

    val now = System.currentTimeMillis()
    val twentyFourHoursMs = 24 * 60 * 60 * 1000L

    // Categorize candidates
    // Group 1: Normal songs (>= 30s duration, not played in last 24h)
    // Group 2: Recently played songs (>= 30s duration, played in last 24h)
    // Group 3: Short songs (< 30s duration)
    val shortSongs = mutableListOf<Song>()
    val recentSongs = mutableListOf<Song>()
    val normalSongs = mutableListOf<Song>()

    for (s in candidates) {
      val isShort = s.durationMs in 1 until 30000L
      val isRecent = s.lastPlayedAt > 0L && (now - s.lastPlayedAt <= twentyFourHoursMs)

      when {
        isShort -> shortSongs.add(s)
        isRecent -> recentSongs.add(s)
        else -> normalSongs.add(s)
      }
    }

    val result = mutableListOf<Song>()
    if (current != null) {
      result.add(current)
    }

    // Function to pick from a candidate group while respecting artist spacing rule
    fun pickAllWithArtistSpacing(pool: MutableList<Song>) {
      while (pool.isNotEmpty()) {
        val lastArtist = result.lastOrNull()?.artist?.trim()?.lowercase() ?: ""
        
        // Candidates that do NOT match last artist
        val nonMatching = pool.filter { s ->
          val a = s.artist.trim().lowercase()
          a.isEmpty() || a == "unknown artist" || a != lastArtist
        }

        val choiceList = if (nonMatching.isNotEmpty()) nonMatching else pool
        val selected = weightedRandomSelect(choiceList, random)
        
        result.add(selected)
        pool.remove(selected)
      }
    }

    pickAllWithArtistSpacing(normalSongs)
    pickAllWithArtistSpacing(recentSongs)
    pickAllWithArtistSpacing(shortSongs)

    return result
  }

  private fun weightedRandomSelect(candidates: List<Song>, random: Random): Song {
    if (candidates.size == 1) return candidates.first()

    val weights = candidates.map { s ->
      var w = 1.0
      if (s.isLiked) w += 3.0
      w += (s.playCount.coerceAtMost(20).toDouble() * 0.5)
      w
    }

    val totalWeight = weights.sum()
    var r = random.nextDouble() * totalWeight
    for (i in candidates.indices) {
      r -= weights[i]
      if (r <= 0) {
        return candidates[i]
      }
    }
    return candidates.last()
  }
}
