package com.example.data.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.data.local.SongDao
import com.example.data.model.Song
import com.example.data.network.ITunesTrack
import com.example.data.network.LrcLibResponse
import com.example.data.network.NetworkClient
import com.example.utils.LrcParser
import com.example.data.lyrics.sources.OpenAILyricsSource
import com.example.data.lyrics.sources.GeniusLyricsSource
import com.example.data.lyrics.sources.LyricsOvhLyricsSource
import com.example.data.lyrics.sources.NetEaseLyricsSource
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricsCandidateDebug(
  val trackName: String,
  val artistName: String,
  val albumName: String?,
  val durationSec: Int,
  val hasSynced: Boolean,
  val hasPlain: Boolean
)

data class LyricsDebugStep(
  val stepName: String,
  val url: String,
  val httpStatus: Int?,
  val summary: String,
  val responseSnippet: String? = null,
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
  val durationSec: Int = 0,
  val steps: List<LyricsDebugStep> = emptyList(),
  val candidates: List<LyricsCandidateDebug> = emptyList(),
  val finalResult: String = "IDLE", // SYNCED, PLAIN, NOT_FOUND, ERROR (server busy, retry later), SKIPPED_SHORT_AUDIO
  val finalSource: String? = null,
  val identifiedArtist: String? = null,
  val identifiedAlbum: String? = null,
  val errorDetail: String? = null
)

private sealed class StepResult {
  data class Success<T>(val data: T) : StepResult()
  data object NotFound : StepResult()
  data class Error(val code: Int?, val message: String, val isTransient: Boolean) : StepResult()
  data object Skipped : StepResult()
}

class LyricsPipeline(
  private val context: Context,
  private val songDao: SongDao
) {
  companion object {
    private const val TAG = "LyricsPipeline"
    private val queueSemaphore = Semaphore(2) // Max 2 concurrent requests
  }

  private val netEaseSource = NetEaseLyricsSource()
  private val lyricsOvhSource = LyricsOvhLyricsSource()
  private val geniusSource = GeniusLyricsSource(context)
  val openAiSource = OpenAILyricsSource(context)

  private val _debugInfo = MutableStateFlow(LyricsDebugInfo())
  val debugInfo: StateFlow<LyricsDebugInfo> = _debugInfo.asStateFlow()

  // Track debug info per song to prevent background queue clobbering UI inspections
  private val debugInfoMap = ConcurrentHashMap<String, LyricsDebugInfo>()
  var activeDebugSongId: String? = null

  fun getDebugInfo(songId: String): LyricsDebugInfo {
    return debugInfoMap[songId] ?: if (_debugInfo.value.songId == songId) _debugInfo.value else LyricsDebugInfo(songId = songId)
  }

  fun setInspectSongId(songId: String) {
    activeDebugSongId = songId
    debugInfoMap[songId]?.let {
      _debugInfo.value = it
    }
  }

  suspend fun fetchLyricsForSong(song: Song, force: Boolean = false): Boolean {
    // ALWAYS reload the song by exact songId directly from the database!
    return fetchLyricsForSongId(song.id, force)
  }

  suspend fun fetchLyricsForSongId(songId: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
    // 1. ALWAYS load the song by exact songId directly from the database
    val song = songDao.getSongById(songId)
    if (song == null) {
      val err = "Song with songId '$songId' not found in database"
      Log.e(TAG, err)
      return@withContext false
    }

    if (!force && song.hasLyrics && !song.lyricsText.isNullOrBlank()) {
      Log.d(TAG, "Song '${song.title}' (id: $songId) already has cached lyrics in DB. Using local cache.")
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

    // Check 1-hour retry rule if marked ERROR
    if (!force && song.lyricsStatus == "ERROR") {
      val now = System.currentTimeMillis()
      if (now - song.lyricsFetchedAt < 60L * 60 * 1000) {
        Log.d(TAG, "Song '${song.title}' recently marked ERROR (server busy). Skipping until 1-hour cooldown or manual retry.")
        return@withContext false
      }
    }

    queueSemaphore.withPermit {
      executeLyricsFetchForSong(song)
    }
  }

  private suspend fun executeLyricsFetchForSong(song: Song): Boolean {
    val durationSec = (song.durationMs / 1000).toInt()
    val steps = mutableListOf<LyricsDebugStep>()
    val sentUrls = mutableSetOf<String>()
    var candidatesList = emptyList<LyricsCandidateDebug>()
    var hasTransientServerError = false

    // Log songId plus the exact title/artist/album/duration used at the start of every fetch
    Log.d(
      TAG,
      "[LyricsFetch Start] songId=${song.id}, title='${song.title}', artist='${song.artist}', album='${song.album}', durationMs=${song.durationMs} (${durationSec}s)"
    )

    // Rule: Duration under 30 seconds check
    if (song.durationMs in 1..29999) {
      val skipMsg = "File duration is ${durationSec}s (< 30s): probably not a song (ringtone/voice note). Skipped auto lyrics."
      Log.w(TAG, "[LyricsFetch Skipped] songId=${song.id}: $skipMsg")
      steps.add(LyricsDebugStep("Pre-check (Duration < 30s)", "", null, skipMsg))
      songDao.updateLyricsStatus(song.id, "SKIPPED_SHORT_AUDIO", System.currentTimeMillis(), skipMsg)

      val debug = LyricsDebugInfo(
        songId = song.id,
        rawTitle = song.title,
        rawArtist = song.artist,
        cleanedTitle = song.title,
        cleanedArtist = song.artist,
        album = song.album,
        durationMs = song.durationMs,
        durationSec = durationSec,
        steps = steps,
        finalResult = "SKIPPED_SHORT_AUDIO",
        errorDetail = skipMsg
      )
      publishDebug(debug)
      return false
    }

    // Clean title and filename
    val cleanedTitle = cleanFilenameOrTitle(song.title)
    var effectiveArtist: String? = if (!isGenericOrFolderMetadata(song.artist)) cleanFilenameOrTitle(song.artist) else null
    var effectiveAlbum: String? = if (!isGenericOrFolderMetadata(song.album)) cleanFilenameOrTitle(song.album) else null

    // Check if cleaned title originates from requested song's own title or filename
    val originatesFromSong = verifyTitleOrigin(cleanedTitle, song)
    if (!originatesFromSong) {
      val abortMsg = "ABORT: Cleaned title '$cleanedTitle' does not originate from requested song's title '${song.title}' or filename '${song.filePath}'"
      Log.e(TAG, abortMsg)
      steps.add(LyricsDebugStep("Integrity Check", "", null, abortMsg))
      songDao.updateLyricsStatus(song.id, "ERROR", System.currentTimeMillis(), abortMsg)

      val debug = LyricsDebugInfo(
        songId = song.id,
        rawTitle = song.title,
        rawArtist = song.artist,
        cleanedTitle = cleanedTitle,
        cleanedArtist = effectiveArtist ?: "Unknown Artist",
        album = effectiveAlbum ?: "-",
        durationMs = song.durationMs,
        durationSec = durationSec,
        steps = steps,
        finalResult = "ERROR",
        errorDetail = abortMsg
      )
      publishDebug(debug)
      return false
    }

    var currentDebug = LyricsDebugInfo(
      songId = song.id,
      rawTitle = song.title,
      rawArtist = song.artist,
      cleanedTitle = cleanedTitle,
      cleanedArtist = effectiveArtist ?: "Unknown Artist",
      album = effectiveAlbum ?: "-",
      durationMs = song.durationMs,
      durationSec = durationSec,
      steps = steps,
      finalResult = "FETCHING"
    )
    publishDebug(currentDebug)
    songDao.setLyricsFetching(song.id)

    // Helper to execute network step with 3-attempt exponential backoff & snippet capture
    suspend fun executeStep(
      stepName: String,
      url: String,
      call: suspend () -> retrofit2.Response<*>
    ): StepResult {
      if (sentUrls.contains(url)) {
        Log.d(TAG, "[$stepName] Skipping duplicate identical request: $url")
        return StepResult.Skipped
      }
      sentUrls.add(url)

      val backoffDelays = listOf(1000L, 3000L, 7000L)
      var lastCode: Int? = null
      var lastBodySnippet: String = ""

      for (attempt in 0..3) {
        try {
          val res = call()
          lastCode = res.code()
          if (res.isSuccessful) {
            val body = res.body()
            steps.add(LyricsDebugStep(stepName, url, lastCode, "Success (HTTP $lastCode)", responseSnippet = "OK"))
            currentDebug = currentDebug.copy(steps = steps.toList())
            publishDebug(currentDebug)
            Log.d(TAG, "[$stepName] SUCCESS ($lastCode): $url")
            return StepResult.Success(body)
          } else {
            val snippet = try {
              res.errorBody()?.string()?.take(200) ?: res.message()
            } catch (_: Exception) {
              res.message()
            }
            lastBodySnippet = snippet.ifBlank { "HTTP $lastCode" }

            if (lastCode == 404) {
              steps.add(LyricsDebugStep(stepName, url, lastCode, "HTTP 404: $lastBodySnippet", responseSnippet = lastBodySnippet))
              currentDebug = currentDebug.copy(steps = steps.toList())
              publishDebug(currentDebug)
              Log.d(TAG, "[$stepName] 404 Not Found: $url")
              return StepResult.NotFound
            }

            if (lastCode in listOf(429, 502, 503, 504)) {
              hasTransientServerError = true
              if (attempt < 3) {
                val delayMs = backoffDelays[attempt]
                Log.w(TAG, "[$stepName] HTTP $lastCode ($lastBodySnippet). Retry ${attempt + 1}/3 after ${delayMs}ms...")
                delay(delayMs)
                continue
              }
            }

            steps.add(LyricsDebugStep(stepName, url, lastCode, "HTTP $lastCode: $lastBodySnippet", responseSnippet = lastBodySnippet))
            currentDebug = currentDebug.copy(steps = steps.toList())
            publishDebug(currentDebug)
            Log.w(TAG, "[$stepName] FAILED ($lastCode): $url")
            return StepResult.Error(lastCode, lastBodySnippet, isTransient = (lastCode in listOf(429, 502, 503, 504)))
          }
        } catch (e: Exception) {
          hasTransientServerError = true
          val msg = e.message ?: "Network error"
          lastBodySnippet = "Exception: $msg"
          if (attempt < 3) {
            val delayMs = backoffDelays[attempt]
            Log.w(TAG, "[$stepName] Exception: $msg. Retry ${attempt + 1}/3 after ${delayMs}ms...")
            delay(delayMs)
            continue
          }
        }
      }

      val failSummary = if (lastCode != null) "HTTP $lastCode: $lastBodySnippet" else lastBodySnippet
      steps.add(LyricsDebugStep(stepName, url, lastCode, failSummary, responseSnippet = lastBodySnippet))
      currentDebug = currentDebug.copy(steps = steps.toList())
      publishDebug(currentDebug)
      return StepResult.Error(lastCode, lastBodySnippet, isTransient = true)
    }

    // Step 0: Identify Song using iTunes Search API if Artist is unknown
    var identifiedArtistName: String? = null
    var identifiedAlbumName: String? = null
    if (effectiveArtist == null) {
      val itunesUrl = "https://itunes.apple.com/search?term=${enc(cleanedTitle)}&entity=song&limit=5"
      val idResult = executeStep("Step 0 (iTunes Song Identification)", itunesUrl) {
        NetworkClient.iTunesService.searchSong(cleanedTitle, "song", 5)
      }
      if (idResult is StepResult.Success<*>) {
        val itunesData = idResult.data as? com.example.data.network.ITunesSearchResult
        val bestTrack = pickBestITunesTrack(itunesData?.results, cleanedTitle, durationSec)
        if (bestTrack != null && !bestTrack.artistName.isNullOrBlank()) {
          identifiedArtistName = bestTrack.artistName
          identifiedAlbumName = bestTrack.collectionName
          effectiveArtist = identifiedArtistName
          if (!isGenericOrFolderMetadata(identifiedAlbumName)) {
            effectiveAlbum = identifiedAlbumName
          }

          // Update song record in Room DB with identified metadata & high-res artwork
          val highResCover = bestTrack.artworkUrl100?.replace("100x100bb", "1200x1200bb")
          songDao.updateMetadata(
            songId = song.id,
            title = bestTrack.trackName ?: cleanedTitle,
            artist = identifiedArtistName!!,
            album = effectiveAlbum ?: "Single"
          )
          if (highResCover != null && song.albumArtUri.isNullOrBlank()) {
            songDao.updateAlbumArt(song.id, highResCover)
          }

          val diffSec = abs(((bestTrack.trackTimeMillis ?: 0L) / 1000) - durationSec)
          val idSummary = "Identified: '${bestTrack.trackName}' by '$identifiedArtistName', Album: '$effectiveAlbum' (Duration diff: ${diffSec}s)"
          steps.add(LyricsDebugStep("Identification Result", itunesUrl, 200, idSummary))
          currentDebug = currentDebug.copy(
            cleanedArtist = identifiedArtistName!!,
            album = effectiveAlbum ?: "-",
            identifiedArtist = identifiedArtistName,
            identifiedAlbum = effectiveAlbum,
            steps = steps.toList()
          )
          publishDebug(currentDebug)
          Log.d(TAG, "[iTunes Identification] $idSummary")
        } else {
          steps.add(LyricsDebugStep("Identification Result", itunesUrl, 200, "No confident match in top 5 results for '$cleanedTitle' within +-10s"))
          currentDebug = currentDebug.copy(steps = steps.toList())
          publishDebug(currentDebug)
        }
      }
    }

    var foundSynced: String? = null
    var foundPlain: String? = null
    var foundSource: String? = null

    // Step a: GET https://lrclib.net/api/get with track, artist, album, duration
    if (foundSynced == null && effectiveArtist != null && effectiveAlbum != null && durationSec >= 30) {
      val url = "https://lrclib.net/api/get?track_name=${enc(cleanedTitle)}&artist_name=${enc(effectiveArtist!!)}&album_name=${enc(effectiveAlbum!!)}&duration=$durationSec"
      val res = executeStep("Step a (Exact match)", url) {
        NetworkClient.lrcLibService.getLyrics(cleanedTitle, effectiveArtist!!, effectiveAlbum, durationSec)
      }
      if (res is StepResult.Success<*>) {
        val lrc = res.data as? LrcLibResponse
        if (!lrc?.syncedLyrics.isNullOrBlank()) {
          foundSynced = lrc!!.syncedLyrics
          foundSource = "LRCLIB (Step a: Exact)"
        } else if (!lrc?.plainLyrics.isNullOrBlank() && foundPlain == null) {
          foundPlain = lrc!!.plainLyrics
          foundSource = "LRCLIB (Step a: Plain)"
        }
      }
    }

    // Step b: GET https://lrclib.net/api/get without album_name
    if (foundSynced == null && effectiveArtist != null && durationSec >= 30) {
      val url = "https://lrclib.net/api/get?track_name=${enc(cleanedTitle)}&artist_name=${enc(effectiveArtist!!)}&duration=$durationSec"
      val res = executeStep("Step b (No album)", url) {
        NetworkClient.lrcLibService.getLyrics(cleanedTitle, effectiveArtist!!, null, durationSec)
      }
      if (res is StepResult.Success<*>) {
        val lrc = res.data as? LrcLibResponse
        if (!lrc?.syncedLyrics.isNullOrBlank()) {
          foundSynced = lrc!!.syncedLyrics
          foundSource = "LRCLIB (Step b: No album)"
        } else if (!lrc?.plainLyrics.isNullOrBlank() && foundPlain == null) {
          foundPlain = lrc!!.plainLyrics
          foundSource = "LRCLIB (Step b: Plain)"
        }
      }
    }

    // Step c: GET https://lrclib.net/api/search?track_name=..&artist_name=..
    if (foundSynced == null && effectiveArtist != null) {
      val url = "https://lrclib.net/api/search?track_name=${enc(cleanedTitle)}&artist_name=${enc(effectiveArtist!!)}"
      val res = executeStep("Step c (Search track & artist)", url) {
        NetworkClient.lrcLibService.searchLyricsByTrackAndArtist(cleanedTitle, effectiveArtist!!)
      }
      if (res is StepResult.Success<*>) {
        @Suppress("UNCHECKED_CAST")
        val list = res.data as? List<LrcLibResponse>
        recordCandidates(list)?.let { candidatesList = it }
        val best = pickBestSearchResult(list, durationSec, cleanedTitle, effectiveArtist)
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
    }

    // Step d: GET https://lrclib.net/api/search?q=<artist + title>
    if (foundSynced == null && effectiveArtist != null) {
      val query = "$effectiveArtist $cleanedTitle".trim()
      val url = "https://lrclib.net/api/search?q=${enc(query)}"
      val res = executeStep("Step d (Search q=artist+title)", url) {
        NetworkClient.lrcLibService.searchLyrics(query)
      }
      if (res is StepResult.Success<*>) {
        @Suppress("UNCHECKED_CAST")
        val list = res.data as? List<LrcLibResponse>
        recordCandidates(list)?.let { candidatesList = it }
        val best = pickBestSearchResult(list, durationSec, cleanedTitle, effectiveArtist)
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
    }

    // Step e: GET https://lrclib.net/api/search?q=<title only>
    if (foundSynced == null) {
      val url = "https://lrclib.net/api/search?q=${enc(cleanedTitle)}"
      val res = executeStep("Step e (Search q=title)", url) {
        NetworkClient.lrcLibService.searchLyrics(cleanedTitle)
      }
      if (res is StepResult.Success<*>) {
        @Suppress("UNCHECKED_CAST")
        val list = res.data as? List<LrcLibResponse>
        recordCandidates(list)?.let { candidatesList = it }
        val best = pickBestSearchResult(list, durationSec, cleanedTitle, effectiveArtist)
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
    }

    // Step f: NetEase Cloud Music (synced LRC endpoints)
    if (foundSynced == null) {
      try {
        val netEaseResult = netEaseSource.fetchLyrics(
          cleanedTitle = cleanedTitle,
          effectiveArtist = effectiveArtist,
          effectiveAlbum = effectiveAlbum,
          durationSec = durationSec,
          filePath = song.filePath
        ) { step ->
          steps.add(step)
          currentDebug = currentDebug.copy(steps = steps.toList())
          publishDebug(currentDebug)
        }
        if (netEaseResult != null) {
          if (!netEaseResult.syncedLyrics.isNullOrBlank()) {
            foundSynced = netEaseResult.syncedLyrics
            foundSource = netEaseResult.sourceName ?: "NetEase Cloud Music"
          } else if (!netEaseResult.plainLyrics.isNullOrBlank() && foundPlain == null) {
            foundPlain = netEaseResult.plainLyrics
            foundSource = netEaseResult.sourceName ?: "NetEase Cloud Music (Plain)"
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "NetEase source failed", e)
      }
    }

    // Step g: lyrics.ovh (plain lyrics fallback)
    if (foundSynced == null && foundPlain == null && effectiveArtist != null) {
      try {
        val ovhResult = lyricsOvhSource.fetchLyrics(
          cleanedTitle = cleanedTitle,
          effectiveArtist = effectiveArtist,
          effectiveAlbum = effectiveAlbum,
          durationSec = durationSec,
          filePath = song.filePath
        ) { step ->
          steps.add(step)
          currentDebug = currentDebug.copy(steps = steps.toList())
          publishDebug(currentDebug)
        }
        if (ovhResult?.plainLyrics != null) {
          foundPlain = ovhResult.plainLyrics
          foundSource = ovhResult.sourceName ?: "lyrics.ovh"
        }
      } catch (e: Exception) {
        Log.e(TAG, "lyrics.ovh source failed", e)
      }
    }

    // Step h: Genius API (if user entered Genius key)
    if (foundSynced == null && foundPlain == null) {
      try {
        val geniusResult = geniusSource.fetchLyrics(
          cleanedTitle = cleanedTitle,
          effectiveArtist = effectiveArtist,
          effectiveAlbum = effectiveAlbum,
          durationSec = durationSec,
          filePath = song.filePath
        ) { step ->
          steps.add(step)
          currentDebug = currentDebug.copy(steps = steps.toList())
          publishDebug(currentDebug)
        }
        if (geniusResult?.plainLyrics != null) {
          foundPlain = geniusResult.plainLyrics
          foundSource = geniusResult.sourceName ?: "Genius"
        }
      } catch (e: Exception) {
        Log.e(TAG, "Genius source failed", e)
      }
    }

    // Step i: Sidecar .lrc file next to the audio file
    if (foundSynced == null && song.filePath != null) {
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
      } catch (e: Exception) {
        Log.e(TAG, "Sidecar .lrc check error", e)
      }
    }

    // Step j: Embedded USLT/SYLT metadata from audio file
    if (foundSynced == null && foundPlain == null && song.filePath != null) {
      try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(song.filePath)
        val embeddedLyrics = try {
          mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
            ?: mmr.extractMetadata(101)
            ?: ""
        } catch (_: Exception) {
          ""
        }
        mmr.release()
        if (!embeddedLyrics.isNullOrBlank()) {
          val hasTimestamps = LrcParser.hasLrcTimestamps(embeddedLyrics)
          if (hasTimestamps) {
            foundSynced = embeddedLyrics
            foundSource = "Embedded Audio Metadata (Synced)"
          } else {
            foundPlain = embeddedLyrics
            foundSource = "Embedded Audio Metadata (Plain)"
          }
          steps.add(LyricsDebugStep("Embedded Metadata", song.filePath, 200, "Found embedded lyrics (${embeddedLyrics.length} chars)"))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Embedded lyrics extraction error", e)
      }
    }

    // Step k: OpenAI AI Fallback (ONLY if user entered key AND enabled Auto AI lyrics)
    if (foundSynced == null && song.filePath != null) {
      val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
      val autoAiLyrics = prefs.getBoolean("auto_ai_lyrics", false)
      if (autoAiLyrics) {
        try {
          val openAiResult = openAiSource.fetchLyrics(
            cleanedTitle = cleanedTitle,
            effectiveArtist = effectiveArtist,
            effectiveAlbum = effectiveAlbum,
            durationSec = durationSec,
            filePath = song.filePath
          ) { step ->
            steps.add(step)
            currentDebug = currentDebug.copy(steps = steps.toList())
            publishDebug(currentDebug)
          }
          if (openAiResult?.syncedLyrics != null) {
            foundSynced = openAiResult.syncedLyrics
            foundSource = "AI-generated (OpenAI)"
          }
        } catch (e: Exception) {
          Log.e(TAG, "OpenAI source failed", e)
        }
      }
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
      currentDebug = currentDebug.copy(
        steps = steps.toList(),
        candidates = candidatesList,
        finalResult = finalStatus,
        finalSource = foundSource
      )
      publishDebug(currentDebug)
      Log.d(TAG, "Lyrics saved permanently to DB for '${song.title}' ($finalStatus via $foundSource)")
      return true
    } else {
      if (hasTransientServerError) {
        val errText = "ERROR (server busy, retry later)"
        songDao.updateLyricsStatus(
          songId = song.id,
          status = "ERROR",
          fetchedAt = System.currentTimeMillis(),
          error = errText
        )
        currentDebug = currentDebug.copy(
          steps = steps.toList(),
          candidates = candidatesList,
          finalResult = errText,
          errorDetail = "LRCLIB server returned HTTP 429/502/503 or timed out during request"
        )
        publishDebug(currentDebug)
        Log.w(TAG, "Lyrics fetch for '${song.title}' marked as temporary ERROR. Auto-retry scheduled in 1 hour.")
        return false
      } else {
        songDao.updateLyricsNotFound(
          songId = song.id,
          fetchedAt = System.currentTimeMillis(),
          error = "Not found after all steps"
        )
        currentDebug = currentDebug.copy(
          steps = steps.toList(),
          candidates = candidatesList,
          finalResult = "NOT_FOUND"
        )
        publishDebug(currentDebug)
        Log.d(TAG, "Lyrics NOT_FOUND for '${song.title}' after trying all steps.")
        return false
      }
    }
  }

  private fun publishDebug(debug: LyricsDebugInfo) {
    debugInfoMap[debug.songId] = debug
    if (activeDebugSongId == null || activeDebugSongId == debug.songId) {
      _debugInfo.value = debug
    }
  }

  private fun recordCandidates(list: List<LrcLibResponse>?): List<LyricsCandidateDebug>? {
    if (list.isNullOrEmpty()) return null
    return list.take(6).map {
      LyricsCandidateDebug(
        trackName = it.trackName ?: "-",
        artistName = it.artistName ?: "-",
        albumName = it.albumName,
        durationSec = it.duration?.toInt() ?: 0,
        hasSynced = !it.syncedLyrics.isNullOrBlank(),
        hasPlain = !it.plainLyrics.isNullOrBlank()
      )
    }
  }

  private fun pickBestITunesTrack(
    tracks: List<ITunesTrack>?,
    targetTitle: String,
    targetDurationSec: Int
  ): ITunesTrack? {
    if (tracks.isNullOrEmpty()) return null
    val normTargetTitle = normalizeString(targetTitle)

    // Filter by title similarity
    val matched = tracks.filter { track ->
      val title = track.trackName ?: return@filter false
      val norm = normalizeString(title)
      norm.contains(normTargetTitle) || normTargetTitle.contains(norm)
    }

    val pool = if (matched.isNotEmpty()) matched else tracks

    return pool.minByOrNull { track ->
      var penalty = 0
      val trackSec = ((track.trackTimeMillis ?: 0L) / 1000).toInt()
      if (targetDurationSec > 0 && trackSec > 0) {
        val diff = abs(trackSec - targetDurationSec)
        penalty += diff * 3
        if (diff > 10) penalty += 500 // Penalty outside +-10s window
      }
      val normTitle = normalizeString(track.trackName ?: "")
      if (normTitle != normTargetTitle) penalty += 15
      penalty
    }?.takeIf { track ->
      val trackSec = ((track.trackTimeMillis ?: 0L) / 1000).toInt()
      if (targetDurationSec > 0 && trackSec > 0) {
        abs(trackSec - targetDurationSec) <= 15
      } else true
    }
  }

  private fun pickBestSearchResult(
    candidates: List<LrcLibResponse>?,
    targetDurationSec: Int,
    targetTitle: String,
    targetArtist: String?
  ): LrcLibResponse? {
    if (candidates.isNullOrEmpty()) return null

    val normTargetTitle = normalizeString(targetTitle)
    val normTargetArtist = targetArtist?.let { normalizeString(it) } ?: ""

    // Separate candidates with synced lyrics vs plain lyrics
    val withSynced = candidates.filter { !it.syncedLyrics.isNullOrBlank() }
    val pool = if (withSynced.isNotEmpty()) withSynced else candidates

    return pool.minByOrNull { item ->
      var penalty = 0
      val itemDuration = item.duration?.toInt() ?: 0
      if (targetDurationSec > 0 && itemDuration > 0) {
        val diff = abs(itemDuration - targetDurationSec)
        penalty += diff * 3
        if (diff > 5) penalty += 150 // Penalty outside +-5s
      }

      val itemTitle = normalizeString(item.trackName ?: "")
      val itemArtist = normalizeString(item.artistName ?: "")

      if (!itemTitle.contains(normTargetTitle) && !normTargetTitle.contains(itemTitle)) {
        penalty += 80
      }
      if (normTargetArtist.isNotEmpty() && !itemArtist.contains(normTargetArtist) && !normTargetArtist.contains(itemArtist)) {
        penalty += 40
      }
      if (item.syncedLyrics.isNullOrBlank()) {
        penalty += 200 // heavily penalize plain lyrics if synced exists
      }

      penalty
    }?.takeIf { item ->
      val itemDuration = item.duration?.toInt() ?: 0
      if (targetDurationSec > 0 && itemDuration > 0) {
        abs(itemDuration - targetDurationSec) <= 8
      } else true
    }
  }

  fun verifyTitleOrigin(cleanedTitle: String, song: Song): Boolean {
    val normCleaned = normalizeString(cleanedTitle)
    if (normCleaned.isBlank()) return true
    val normTitle = normalizeString(song.title)
    val normFileName = song.filePath?.let { File(it).nameWithoutExtension }?.let { normalizeString(it) } ?: ""

    return normTitle.contains(normCleaned) || normCleaned.contains(normTitle) ||
      (normFileName.isNotEmpty() && (normFileName.contains(normCleaned) || normCleaned.contains(normFileName)))
  }

  suspend fun runSetFireToTheRainTest(): Pair<Boolean, LyricsDebugInfo> = withContext(Dispatchers.IO) {
    val testSongId = "test_set_fire_to_the_rain"
    val testSong = Song(
      id = testSongId,
      title = "Set_Fire_to_the_Rain(128k)",
      artist = "Unknown Artist",
      album = "ShareMe",
      durationMs = 242000L,
      uriString = "content://media/external/audio/media/7777",
      filePath = "/storage/emulated/0/ShareMe/Set_Fire_to_the_Rain(128k).mp3"
    )
    songDao.insertSong(testSong)
    setInspectSongId(testSongId)

    val success = fetchLyricsForSongId(testSongId, force = true)
    val updated = songDao.getSongById(testSongId)
    val info = getDebugInfo(testSongId)

    val cleanedMatches = info.cleanedTitle.equals("Set Fire to the Rain", ignoreCase = true)
    val artistIdentified = (info.cleanedArtist.contains("Adele", ignoreCase = true) || updated?.artist?.contains("Adele", ignoreCase = true) == true)
    val durationMatches = (info.durationSec == 242 || (updated?.durationMs ?: 0L) / 1000 == 242L)
    val lyricsSynced = updated?.hasLyrics == true && updated.isSyncedLyrics

    val testPassed = cleanedMatches && artistIdentified && durationMatches && lyricsSynced
    Log.d(TAG, "[Test Set Fire to the Rain] Result: $testPassed (cleaned=$cleanedMatches, artist=$artistIdentified, duration=$durationMatches, synced=$lyricsSynced)")

    Pair(testPassed, info)
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

  fun cleanFilenameOrTitle(raw: String): String {
    var s = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFC)
    // Remove file extensions
    s = s.replace(Regex("\\.(mp3|m4a|flac|wav|aac|ogg|opus|wma)$", RegexOption.IGNORE_CASE), "")

    // Split into segments using separators: runs of 2+ underscores ("___"), " | ", " - ", " – ", " — ", " // "
    val segments = s.split(Regex("___|\\s+[-–—|/]+\\s+"))
    val cleanedSegments = mutableListOf<String>()

    val junkTerms = setOf(
      "lyrical video", "lyric video", "official video", "official audio", "official music video",
      "full video", "full song", "audio", "video", "hd", "4k", "lyrics", "karaoke", "cover", "live",
      "128k", "192k", "320k", "kbps"
    )

    for (seg in segments) {
      var cs = seg.replace('_', ' ')
      // Remove bitrate patterns
      cs = cs.replace(Regex("(?i)[\\(\\[]?\\s*\\b\\d{2,4}\\s*(k|kbps|kb/s)\\b\\s*[\\)\\]]?"), " ")
      // Remove tags in parentheses/brackets
      cs = cs.replace(Regex("(?i)\\[.*?(official|video|audio|lyrics?|remaster|visualizer|live|clip|hq|hd|explicit).*?\\]"), " ")
      cs = cs.replace(Regex("(?i)\\(.*?(official|video|audio|lyrics?|remaster|visualizer|live|clip|hq|hd|explicit).*?\\)"), " ")
      cs = cs.replace(Regex("(?i)-?\\s*Remaster(ed)?(\\s*\\d{4})?"), " ")
      cs = cs.replace(Regex("(?i)-?\\s*Live(\\s*at.*)?"), " ")
      cs = cs.replace(Regex("(?i)\\b(feat|ft)\\.?\\s+.*"), "")
      cs = cs.replace(Regex("\\(\\s*\\)"), " ")
      cs = cs.replace(Regex("\\[\\s*\\]"), " ")
      cs = cs.replace(Regex("(?i)\\[?(www\\.[a-z0-9.\\-_]+|[a-z0-9_\\-]+\\.(com|net|org|io|ru|co|info|cc))\\]?"), " ")
      cs = cs.replace(Regex("^\\s*\\d{1,3}\\s*[-._\\s]\\s*"), "")
      cs = cs.replace(Regex("\\s+"), " ").trim()

      if (cs.isNotBlank() && !junkTerms.contains(cs.lowercase()) && !cs.matches(Regex("^\\d{1,3}$"))) {
        cleanedSegments.add(cs)
      }
    }

    if (cleanedSegments.isNotEmpty()) {
      // Return the first meaningful segment as title (or join if multi-part title)
      return cleanedSegments.first()
    }

    // Fallback standard cleanup
    s = s.replace('_', ' ')
    s = s.replace(Regex("(?i)[\\(\\[]?\\s*\\b\\d{2,4}\\s*(k|kbps|kb/s)\\b\\s*[\\)\\]]?"), " ")
    s = s.replace(Regex("\\s+"), " ").trim()
    return s.ifBlank { raw.trim() }
  }

  fun isGenericOrFolderMetadata(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    val v = value.trim().lowercase()
    val genericNames = setOf(
      "unknown artist", "unknown album", "unknown", "<unknown>",
      "untitled", "various artists", "single",
      "shareme", "download", "downloads", "music", "audio", "bluetooth",
      "whatsapp", "whatsapp audio", "telegram", "telegram audio",
      "media", "internal storage", "sdcard", "ringtones", "notifications",
      "voice", "recordings", "sound", "sounds", "record", "recorder",
      "call", "calls", "hangouts", "hangouts call"
    )
    if (v in genericNames) return true
    if (v.startsWith("<unknown") || v.startsWith("unknown")) return true
    return false
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

  suspend fun generateLyricsWithOpenAI(song: Song): Boolean = withContext(Dispatchers.IO) {
    if (song.filePath == null) return@withContext false
    val file = File(song.filePath)
    if (!file.exists()) return@withContext false

    val durationSec = (song.durationMs / 1000).toInt()
    val steps = mutableListOf<LyricsDebugStep>()
    val result = openAiSource.generateLyricsFromAudio(file, durationSec, { steps.add(it) })
    if (result?.syncedLyrics != null) {
      songDao.updateLyricsSuccess(
        songId = song.id,
        lyrics = result.syncedLyrics,
        isSynced = true,
        source = "AI-generated (OpenAI)",
        fetchedAt = System.currentTimeMillis()
      )
      publishDebug(LyricsDebugInfo(
        songId = song.id,
        rawTitle = song.title,
        steps = steps,
        finalResult = "SYNCED",
        finalSource = "AI-generated (OpenAI)"
      ))
      return@withContext true
    }
    false
  }

  suspend fun autoSyncLyricsWithOpenAI(song: Song, plainLyrics: String): String? = withContext(Dispatchers.IO) {
    if (song.filePath == null) {
      throw IllegalStateException("Audio file path not found for '${song.title}'")
    }
    val file = File(song.filePath)
    if (!file.exists()) {
      throw IllegalStateException("Audio file does not exist on disk (${file.absolutePath})")
    }
    val durationSec = (song.durationMs / 1000).toInt()
    val steps = mutableListOf<LyricsDebugStep>()
    val synced = openAiSource.autoSyncLyricsWithAudio(file, plainLyrics, durationSec, { steps.add(it) })
    if (synced != null) {
      songDao.updateLyricsSuccess(
        songId = song.id,
        lyrics = synced,
        isSynced = true,
        source = "AI-synced (OpenAI)",
        fetchedAt = System.currentTimeMillis()
      )
      publishDebug(LyricsDebugInfo(
        songId = song.id,
        rawTitle = song.title,
        steps = steps,
        finalResult = "SYNCED",
        finalSource = "AI-synced (OpenAI)"
      ))
    }
    synced
  }
}

