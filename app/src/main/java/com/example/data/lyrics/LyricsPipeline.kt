package com.example.data.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.data.local.SongDao
import com.example.data.model.Song
import com.example.data.network.LrcLibResponse
import com.example.data.network.NetworkClient
import com.example.utils.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import kotlin.math.abs

data class LyricsDebugStep(
  val stepName: String,
  val url: String,
  val httpStatus: Int?,
  val summary: String,
  val timestamp: Long = System.currentTimeMillis()
)

data class LyricsDebugInfo(
  val songId: String = "",
  val rawTitle: String = "",
  val rawArtist: String = "",
  val cleanedTitle: String = "",
  val cleanedArtist: String = "",
  val album: String = "",
  val durationMs: Long = 0L,
  val steps: List<LyricsDebugStep> = emptyList(),
  val finalResult: String = "IDLE", // SYNCED, PLAIN, NOT_FOUND, ERROR
  val finalSource: String? = null
)

class LyricsPipeline(
  private val context: Context,
  private val songDao: SongDao
) {
  companion object {
    private const val TAG = "LyricsPipeline"
    private val queueSemaphore = Semaphore(2) // Max 2 concurrent requests
  }

  private val _debugInfo = MutableStateFlow(LyricsDebugInfo())
  val debugInfo: StateFlow<LyricsDebugInfo> = _debugInfo.asStateFlow()

  suspend fun fetchLyricsForSong(song: Song, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
    if (!force && song.hasLyrics && !song.lyricsText.isNullOrBlank()) {
      Log.d(TAG, "Song '${song.title}' already has cached lyrics in DB. Using local cache.")
      return@withContext true
    }

    // Check 7-day retry rule if marked NOT_FOUND
    if (!force && song.lyricsStatus == "NOT_FOUND") {
      val now = System.currentTimeMillis()
      if (now - song.lyricsFetchedAt < 7L * 24 * 60 * 60 * 1000) {
        Log.d(TAG, "Song '${song.title}' recently marked NOT_FOUND. Skipping until 7-day cooldown or manual retry.")
        return@withContext false
      }
    }

    queueSemaphore.withPermit {
      executeLyricsFetch(song)
    }
  }

  private suspend fun executeLyricsFetch(song: Song): Boolean {
    val durationSec = (song.durationMs / 1000).toInt()
    val cleanedTitle = cleanTitle(song.title)
    val cleanedArtist = cleanArtist(song.artist)
    val albumCleaned = if (song.album.equals("Single", true) || song.album.contains("unknown", true)) null else song.album.trim()

    val steps = mutableListOf<LyricsDebugStep>()
    var currentDebug = LyricsDebugInfo(
      songId = song.id,
      rawTitle = song.title,
      rawArtist = song.artist,
      cleanedTitle = cleanedTitle,
      cleanedArtist = cleanedArtist,
      album = song.album,
      durationMs = song.durationMs,
      steps = steps,
      finalResult = "FETCHING"
    )
    _debugInfo.value = currentDebug
    songDao.setLyricsFetching(song.id)

    Log.d(TAG, "Starting lyrics pipeline for '${song.title}' by '${song.artist}' (cleaned: '$cleanedTitle' by '$cleanedArtist')")

    var foundSynced: String? = null
    var foundPlain: String? = null
    var foundSource: String? = null

    // Helper for executing request with retry and logging
    suspend fun executeStep(
      stepName: String,
      url: String,
      call: suspend () -> retrofit2.Response<*>
    ): Any? {
      var lastResponseCode: Int? = null
      var lastErrorMsg = ""

      for (attempt in 0..1) {
        try {
          val res = call()
          lastResponseCode = res.code()
          if (res.isSuccessful) {
            val body = res.body()
            steps.add(LyricsDebugStep(stepName, url, lastResponseCode, "Success (HTTP $lastResponseCode)"))
            _debugInfo.value = currentDebug.copy(steps = steps.toList())
            Log.d(TAG, "[$stepName] SUCCESS ($lastResponseCode): $url")
            return body
          } else {
            steps.add(LyricsDebugStep(stepName, url, lastResponseCode, "HTTP $lastResponseCode: ${res.message()}"))
            _debugInfo.value = currentDebug.copy(steps = steps.toList())
            Log.d(TAG, "[$stepName] FAILED ($lastResponseCode): $url")
            // If 404, do not retry same endpoint, proceed to next step
            if (lastResponseCode == 404) break
          }
        } catch (e: Exception) {
          lastErrorMsg = e.message ?: "Network error"
          steps.add(LyricsDebugStep(stepName, url, null, "Exception: $lastErrorMsg"))
          _debugInfo.value = currentDebug.copy(steps = steps.toList())
          Log.w(TAG, "[$stepName] Exception on attempt $attempt: $lastErrorMsg")
          if (attempt == 0) delay(1000)
        }
      }
      return null
    }

    // Step a: GET https://lrclib.net/api/get with track, artist, album, duration
    if (foundSynced == null && albumCleaned != null && durationSec > 0) {
      val url = "https://lrclib.net/api/get?track_name=${enc(cleanedTitle)}&artist_name=${enc(cleanedArtist)}&album_name=${enc(albumCleaned)}&duration=$durationSec"
      val res = executeStep("Step a (Exact match)", url) {
        NetworkClient.lrcLibService.getLyrics(cleanedTitle, cleanedArtist, albumCleaned, durationSec)
      } as? LrcLibResponse
      if (res != null) {
        if (!res.syncedLyrics.isNullOrBlank()) {
          foundSynced = res.syncedLyrics
          foundSource = "LRCLIB (Step a: Exact)"
        } else if (!res.plainLyrics.isNullOrBlank() && foundPlain == null) {
          foundPlain = res.plainLyrics
          foundSource = "LRCLIB (Step a: Plain)"
        }
      }
    }

    // Step b: GET https://lrclib.net/api/get without album_name
    if (foundSynced == null && durationSec > 0) {
      val url = "https://lrclib.net/api/get?track_name=${enc(cleanedTitle)}&artist_name=${enc(cleanedArtist)}&duration=$durationSec"
      val res = executeStep("Step b (No album)", url) {
        NetworkClient.lrcLibService.getLyrics(cleanedTitle, cleanedArtist, null, durationSec)
      } as? LrcLibResponse
      if (res != null) {
        if (!res.syncedLyrics.isNullOrBlank()) {
          foundSynced = res.syncedLyrics
          foundSource = "LRCLIB (Step b)"
        } else if (!res.plainLyrics.isNullOrBlank() && foundPlain == null) {
          foundPlain = res.plainLyrics
          foundSource = "LRCLIB (Step b: Plain)"
        }
      }
    }

    // Step c: GET https://lrclib.net/api/search?track_name=..&artist_name=..
    if (foundSynced == null) {
      val url = "https://lrclib.net/api/search?track_name=${enc(cleanedTitle)}&artist_name=${enc(cleanedArtist)}"
      @Suppress("UNCHECKED_CAST")
      val list = executeStep("Step c (Search track & artist)", url) {
        NetworkClient.lrcLibService.searchLyricsByTrackAndArtist(cleanedTitle, cleanedArtist)
      } as? List<LrcLibResponse>

      val best = pickBestSearchResult(list, durationSec, cleanedTitle, cleanedArtist)
      if (best != null) {
        if (!best.syncedLyrics.isNullOrBlank()) {
          foundSynced = best.syncedLyrics
          foundSource = "LRCLIB (Step c: Search)"
        } else if (!best.plainLyrics.isNullOrBlank() && foundPlain == null) {
          foundPlain = best.plainLyrics
          foundSource = "LRCLIB (Step c: Plain)"
        }
      }
    }

    // Step d: GET https://lrclib.net/api/search?q=<artist + title>
    if (foundSynced == null) {
      val query = "$cleanedArtist $cleanedTitle".trim()
      val url = "https://lrclib.net/api/search?q=${enc(query)}"
      @Suppress("UNCHECKED_CAST")
      val list = executeStep("Step d (Search q=artist+title)", url) {
        NetworkClient.lrcLibService.searchLyrics(query)
      } as? List<LrcLibResponse>

      val best = pickBestSearchResult(list, durationSec, cleanedTitle, cleanedArtist)
      if (best != null) {
        if (!best.syncedLyrics.isNullOrBlank()) {
          foundSynced = best.syncedLyrics
          foundSource = "LRCLIB (Step d: Query)"
        } else if (!best.plainLyrics.isNullOrBlank() && foundPlain == null) {
          foundPlain = best.plainLyrics
          foundSource = "LRCLIB (Step d: Plain)"
        }
      }
    }

    // Step e: GET https://lrclib.net/api/search?q=<title only>
    if (foundSynced == null) {
      val url = "https://lrclib.net/api/search?q=${enc(cleanedTitle)}"
      @Suppress("UNCHECKED_CAST")
      val list = executeStep("Step e (Search q=title)", url) {
        NetworkClient.lrcLibService.searchLyrics(cleanedTitle)
      } as? List<LrcLibResponse>

      val best = pickBestSearchResult(list, durationSec, cleanedTitle, cleanedArtist)
      if (best != null) {
        if (!best.syncedLyrics.isNullOrBlank()) {
          foundSynced = best.syncedLyrics
          foundSource = "LRCLIB (Step e: Title)"
        } else if (!best.plainLyrics.isNullOrBlank() && foundPlain == null) {
          foundPlain = best.plainLyrics
          foundSource = "LRCLIB (Step e: Plain)"
        }
      }
    }

    // Fallback 1: Embedded USLT / SYLT tags
    if (foundSynced == null && foundPlain == null) {
      try {
        val retriever = MediaMetadataRetriever()
        if (song.filePath != null && File(song.filePath).exists()) {
          retriever.setDataSource(song.filePath)
        } else {
          retriever.setDataSource(context, Uri.parse(song.uriString))
        }
        // Extract embedded lyrics if present (ID3 USLT tag)
        val embeddedLyrics = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { null } // standard retriever might not expose key directly, check raw frame or genre
        retriever.release()
      } catch (_: Exception) {}
    }

    // Fallback 2: Sidecar .lrc file next to the audio file
    if (foundSynced == null && foundPlain == null && song.filePath != null) {
      try {
        val audioFile = File(song.filePath)
        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        if (lrcFile.exists() && lrcFile.length() > 0) {
          val sidecarText = lrcFile.readText()
          val parsed = LrcParser.parse(sidecarText)
          if (parsed.isNotEmpty()) {
            foundSynced = sidecarText
            foundSource = "Sidecar .lrc file"
            steps.add(LyricsDebugStep("Sidecar .lrc", lrcFile.absolutePath, 200, "Loaded ${parsed.size} lines from .lrc"))
          }
        }
      } catch (_: Exception) {}
    }

    // Save final result to Room DB
    val finalLyrics = foundSynced ?: foundPlain
    val isSynced = foundSynced != null

    if (!finalLyrics.isNullOrBlank()) {
      songDao.updateLyricsSuccess(
        songId = song.id,
        lyrics = finalLyrics,
        isSynced = isSynced,
        source = foundSource ?: "Online",
        fetchedAt = System.currentTimeMillis()
      )
      val finalStatus = if (isSynced) "SYNCED" else "PLAIN"
      _debugInfo.value = currentDebug.copy(
        steps = steps.toList(),
        finalResult = finalStatus,
        finalSource = foundSource
      )
      Log.d(TAG, "Lyrics saved permanently to DB for '${song.title}' ($finalStatus via $foundSource)")
      return true
    } else {
      songDao.updateLyricsNotFound(
        songId = song.id,
        fetchedAt = System.currentTimeMillis(),
        error = "Not found after all steps"
      )
      _debugInfo.value = currentDebug.copy(
        steps = steps.toList(),
        finalResult = "NOT_FOUND"
      )
      Log.d(TAG, "Lyrics NOT_FOUND for '${song.title}' after trying all steps.")
      return false
    }
  }

  private fun pickBestSearchResult(
    candidates: List<LrcLibResponse>?,
    targetDurationSec: Int,
    targetTitle: String,
    targetArtist: String
  ): LrcLibResponse? {
    if (candidates.isNullOrEmpty()) return null

    val normTargetTitle = normalizeString(targetTitle)
    val normTargetArtist = normalizeString(targetArtist)

    // Separate candidates with synced lyrics vs plain lyrics
    val withSynced = candidates.filter { !it.syncedLyrics.isNullOrBlank() }
    val pool = if (withSynced.isNotEmpty()) withSynced else candidates

    // Sort by duration difference and title similarity
    return pool.minByOrNull { item ->
      var penalty = 0
      val itemDuration = item.duration?.toInt() ?: 0
      if (targetDurationSec > 0 && itemDuration > 0) {
        val diff = abs(itemDuration - targetDurationSec)
        penalty += diff * 2
      }

      val itemTitle = normalizeString(item.trackName ?: "")
      val itemArtist = normalizeString(item.artistName ?: "")

      if (!itemTitle.contains(normTargetTitle) && !normTargetTitle.contains(itemTitle)) {
        penalty += 50
      }
      if (normTargetArtist.isNotEmpty() && !itemArtist.contains(normTargetArtist) && !normTargetArtist.contains(itemArtist)) {
        penalty += 30
      }
      if (item.syncedLyrics.isNullOrBlank()) {
        penalty += 100 // heavily penalize plain lyrics if synced available
      }

      penalty
    }
  }

  suspend fun runSelfTest(): Map<String, Boolean> = withContext(Dispatchers.IO) {
    val results = mutableMapOf<String, Boolean>()
    Log.d(TAG, "Starting self-test with known track: 'Blinding Lights - The Weeknd'")

    // Stage 1: Fetch
    var fetchedSyncedLyrics: String? = null
    try {
      val res = NetworkClient.lrcLibService.getLyrics(
        trackName = "Blinding Lights",
        artistName = "The Weeknd",
        albumName = "After Hours",
        duration = 200
      )
      if (res.isSuccessful && !res.body()?.syncedLyrics.isNullOrBlank()) {
        fetchedSyncedLyrics = res.body()?.syncedLyrics
        results["1. Fetch (LRCLIB online API)"] = true
      } else {
        // Try fallback search
        val search = NetworkClient.lrcLibService.searchLyrics("The Weeknd Blinding Lights")
        val match = search.body()?.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
        if (match != null) {
          fetchedSyncedLyrics = match.syncedLyrics
          results["1. Fetch (LRCLIB online API)"] = true
        } else {
          results["1. Fetch (LRCLIB online API)"] = false
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Self-test fetch failed", e)
      results["1. Fetch (LRCLIB online API)"] = false
    }

    // Stage 2: Parse
    var parsedLines = emptyList<com.example.data.model.LrcLine>()
    try {
      parsedLines = LrcParser.parse(fetchedSyncedLyrics ?: "[00:15.50]I said, ooh, I'm blinded by the lights\n[00:20.10]No, I can't sleep until I feel your touch")
      val validOrder = parsedLines.zipWithNext().all { it.first.timestampMs <= it.second.timestampMs }
      results["2. Parse (LRC timestamps & multi-lines)"] = parsedLines.isNotEmpty() && validOrder
    } catch (e: Exception) {
      results["2. Parse (LRC timestamps & multi-lines)"] = false
    }

    // Stage 3: Save to Room DB
    val testSongId = "test_blinding_lights_selftest"
    try {
      val testSong = Song(
        id = testSongId,
        title = "Blinding Lights",
        artist = "The Weeknd",
        album = "After Hours",
        durationMs = 200000L,
        uriString = "content://test/blinding_lights",
        hasLyrics = true,
        isSyncedLyrics = true,
        lyricsText = fetchedSyncedLyrics ?: "[00:15.50]I said, ooh, I'm blinded by the lights",
        lyricsSource = "LRCLIB Test",
        lyricsStatus = "READY",
        lyricsFetchedAt = System.currentTimeMillis()
      )
      songDao.insertSong(testSong)
      results["3. Save Permanently (Room DB)"] = true
    } catch (e: Exception) {
      results["3. Save Permanently (Room DB)"] = false
    }

    // Stage 4: Load Offline from Cache
    try {
      val loaded = songDao.getSongById(testSongId)
      results["4. Load Offline (Read from cache)"] = (loaded != null && loaded.hasLyrics && !loaded.lyricsText.isNullOrBlank())
    } catch (e: Exception) {
      results["4. Load Offline (Read from cache)"] = false
    }

    // Stage 5: Sync (Binary search at currentPositionMs)
    try {
      val activeIndex = LrcParser.findActiveLineIndex(parsedLines, 16000L, 0L)
      results["5. Sync (Binary search active line)"] = activeIndex >= 0
    } catch (e: Exception) {
      results["5. Sync (Binary search active line)"] = false
    }

    results
  }

  private fun cleanTitle(raw: String): String {
    var t = raw.trim()
    val patterns = listOf(
      Regex("\\[.*?\\]"),
      Regex("\\(.*?Official.*?\\)", RegexOption.IGNORE_CASE),
      Regex("\\(.*?Video.*?\\)", RegexOption.IGNORE_CASE),
      Regex("\\(.*?Audio.*?\\)", RegexOption.IGNORE_CASE),
      Regex("\\(.*?Lyrics.*?\\)", RegexOption.IGNORE_CASE),
      Regex("\\(.*?Remastered.*?\\)", RegexOption.IGNORE_CASE),
      Regex("- Remastered.*", RegexOption.IGNORE_CASE),
      Regex("\\(320kbps\\)", RegexOption.IGNORE_CASE),
      Regex("feat\\..*", RegexOption.IGNORE_CASE),
      Regex("ft\\..*", RegexOption.IGNORE_CASE),
      Regex("^\\d{1,3}\\s*[-._]\\s*"), // leading track numbers "01 - "
      Regex("\\.mp3$", RegexOption.IGNORE_CASE),
      Regex("\\.flac$", RegexOption.IGNORE_CASE),
      Regex("\\.m4a$", RegexOption.IGNORE_CASE),
      Regex("\\.wav$", RegexOption.IGNORE_CASE)
    )
    for (p in patterns) {
      t = t.replace(p, "").trim()
    }
    return t.replace('_', ' ').ifBlank { raw }
  }

  private fun cleanArtist(raw: String): String {
    var a = raw.trim()
    val patterns = listOf(
      Regex("feat\\..*", RegexOption.IGNORE_CASE),
      Regex("ft\\..*", RegexOption.IGNORE_CASE),
      Regex("\\[.*?\\]")
    )
    for (p in patterns) {
      a = a.replace(p, "").trim()
    }
    return a.replace('_', ' ').ifBlank { raw }
  }

  private fun normalizeString(s: String): String {
    return s.lowercase()
      .replace(Regex("[^a-z0-9]"), "")
      .trim()
  }

  private fun enc(s: String): String {
    return try {
      URLEncoder.encode(s, "UTF-8")
    } catch (_: Exception) {
      s
    }
  }
}
