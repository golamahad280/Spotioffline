package com.example.data.lyrics.sources

import com.example.data.lyrics.LyricsCandidateDebug
import com.example.data.lyrics.LyricsDebugStep
import com.example.utils.CrashLogger
import com.example.utils.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class NetEaseLyricsSource : LyricsSource {
  override val name: String = "NetEase Cloud Music"

  private val okClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

  override suspend fun fetchLyrics(
    cleanedTitle: String,
    effectiveArtist: String?,
    effectiveAlbum: String?,
    durationSec: Int,
    filePath: String?,
    onStepLogged: (LyricsDebugStep) -> Unit
  ): LyricsSourceResult? = withContext(Dispatchers.IO) {
    val query = if (!effectiveArtist.isNullOrBlank()) "$effectiveArtist $cleanedTitle" else cleanedTitle
    val searchUrl = "https://music.163.com/api/search/get/web?s=${enc(query)}&type=1&limit=5"

    try {
      val searchReq = Request.Builder()
        .url(searchUrl)
        .header("Referer", "https://music.163.com")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .build()

      val searchRes = okClient.newCall(searchReq).execute()
      val searchCode = searchRes.code
      val searchBody = searchRes.body?.string() ?: ""

      if (!searchRes.isSuccessful || searchBody.isBlank()) {
        onStepLogged(
          LyricsDebugStep(
            stepName = "NetEase Search",
            url = searchUrl,
            httpStatus = searchCode,
            summary = "HTTP $searchCode: ${searchBody.take(120)}",
            responseSnippet = searchBody.take(200)
          )
        )
        return@withContext null
      }

      val json = JSONObject(searchBody)
      val resultObj = json.optJSONObject("result")
      val songsArray = resultObj?.optJSONArray("songs")

      if (songsArray == null || songsArray.length() == 0) {
        onStepLogged(
          LyricsDebugStep(
            stepName = "NetEase Search",
            url = searchUrl,
            httpStatus = searchCode,
            summary = "No songs found for '$query'",
            responseSnippet = searchBody.take(200)
          )
        )
        return@withContext null
      }

      val candidates = mutableListOf<LyricsCandidateDebug>()
      var bestSongId: Long? = null
      var bestScore = Int.MAX_VALUE

      for (i in 0 until songsArray.length()) {
        val songObj = songsArray.optJSONObject(i) ?: continue
        val id = songObj.optLong("id")
        val name = songObj.optString("name")
        val durationMs = songObj.optLong("duration", 0L)
        val songDurationSec = (durationMs / 1000).toInt()

        val artistsArray = songObj.optJSONArray("artists")
        val artistNames = mutableListOf<String>()
        if (artistsArray != null) {
          for (a in 0 until artistsArray.length()) {
            val art = artistsArray.optJSONObject(a)?.optString("name")
            if (!art.isNullOrBlank()) artistNames.add(art)
          }
        }
        val artistStr = artistNames.joinToString(", ")

        candidates.add(
          LyricsCandidateDebug(
            trackName = name,
            artistName = artistStr,
            albumName = songObj.optJSONObject("album")?.optString("name"),
            durationSec = songDurationSec,
            hasSynced = true,
            hasPlain = true
          )
        )

        // Title and duration match scoring
        val normCandidate = normalize(name)
        val normTarget = normalize(cleanedTitle)
        var score = 0

        if (durationSec > 0 && songDurationSec > 0) {
          val diff = abs(songDurationSec - durationSec)
          score += diff * 3
          if (diff > 8) score += 200 // Penalty outside +-8s
        }

        if (!normCandidate.contains(normTarget) && !normTarget.contains(normCandidate)) {
          score += 100
        }

        if (score < bestScore && (durationSec <= 0 || abs(songDurationSec - durationSec) <= 8)) {
          bestScore = score
          bestSongId = id
        }
      }

      onStepLogged(
        LyricsDebugStep(
          stepName = "NetEase Search",
          url = searchUrl,
          httpStatus = searchCode,
          summary = "Found ${songsArray.length()} candidates, bestId=$bestSongId",
          responseSnippet = "Matched ID: $bestSongId"
        )
      )

      if (bestSongId == null) {
        return@withContext null
      }

      // Fetch lyric for bestSongId
      val lyricUrl = "https://music.163.com/api/song/lyric?os=pc&id=$bestSongId&lv=-1&kv=-1&tv=-1"
      val lyricReq = Request.Builder()
        .url(lyricUrl)
        .header("Referer", "https://music.163.com")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .build()

      val lyricRes = okClient.newCall(lyricReq).execute()
      val lyricCode = lyricRes.code
      val lyricBody = lyricRes.body?.string() ?: ""

      if (!lyricRes.isSuccessful || lyricBody.isBlank()) {
        onStepLogged(
          LyricsDebugStep(
            stepName = "NetEase Lyric",
            url = lyricUrl,
            httpStatus = lyricCode,
            summary = "HTTP $lyricCode: ${lyricBody.take(120)}",
            responseSnippet = lyricBody.take(200)
          )
        )
        return@withContext null
      }

      val lyricJson = JSONObject(lyricBody)
      val lrcRaw = lyricJson.optJSONObject("lrc")?.optString("lyric")
      val tLrcRaw = lyricJson.optJSONObject("tlyric")?.optString("lyric")

      val hasSynced = LrcParser.hasLrcTimestamps(lrcRaw)
      val hasTransSynced = LrcParser.hasLrcTimestamps(tLrcRaw)

      onStepLogged(
        LyricsDebugStep(
          stepName = "NetEase Lyric",
          url = lyricUrl,
          httpStatus = lyricCode,
          summary = if (hasSynced) "Found synced LRC (${lrcRaw?.length} chars)" else "Plain or no lyrics",
          responseSnippet = (lrcRaw ?: tLrcRaw ?: "No lyrics").take(200)
        )
      )

      if (hasSynced && !lrcRaw.isNullOrBlank()) {
        LyricsSourceResult(
          syncedLyrics = lrcRaw,
          sourceName = "NetEase Cloud Music",
          candidateList = candidates
        )
      } else if (hasTransSynced && !tLrcRaw.isNullOrBlank()) {
        LyricsSourceResult(
          syncedLyrics = tLrcRaw,
          sourceName = "NetEase Cloud Music (Translated)",
          candidateList = candidates
        )
      } else if (!lrcRaw.isNullOrBlank()) {
        LyricsSourceResult(
          plainLyrics = lrcRaw,
          sourceName = "NetEase Cloud Music (Plain)",
          candidateList = candidates
        )
      } else {
        null
      }
    } catch (e: Exception) {
      CrashLogger.w("NetEaseLyricsSource", "Error fetching lyrics from NetEase for '$cleanedTitle'", e)
      onStepLogged(
        LyricsDebugStep(
          stepName = "NetEase Lyric Exception",
          url = searchUrl,
          httpStatus = null,
          summary = "Error: ${e.message}",
          responseSnippet = e.localizedMessage
        )
      )
      null
    }
  }

  private fun normalize(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]"), "").trim()

  private fun enc(s: String): String =
    try { URLEncoder.encode(s, "UTF-8") } catch (e: Exception) { com.example.utils.CrashLogger.e("NetEaseLyricsSource", "URLEncoder error", e); s }
}
