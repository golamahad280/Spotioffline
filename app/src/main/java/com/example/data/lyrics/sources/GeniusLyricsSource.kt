package com.example.data.lyrics.sources

import android.content.Context
import com.example.data.lyrics.LyricsDebugStep
import com.example.utils.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GeniusLyricsSource(private val context: Context) : LyricsSource {
  override val name: String = "Genius"

  private val okClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .build()

  override suspend fun fetchLyrics(
    cleanedTitle: String,
    effectiveArtist: String?,
    effectiveAlbum: String?,
    durationSec: Int,
    filePath: String?,
    onStepLogged: (LyricsDebugStep) -> Unit
  ): LyricsSourceResult? = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val geniusKey = prefs.getString("genius_api_key", "")?.trim()
    if (geniusKey.isNullOrBlank()) {
      return@withContext null
    }

    val query = if (!effectiveArtist.isNullOrBlank()) "$effectiveArtist $cleanedTitle" else cleanedTitle
    val searchUrl = "https://api.genius.com/search?q=${enc(query)}"

    try {
      val req = Request.Builder()
        .url(searchUrl)
        .header("Authorization", "Bearer $geniusKey")
        .header("User-Agent", "SpotiOffline/1.0")
        .build()

      val res = okClient.newCall(req).execute()
      val code = res.code
      val body = res.body?.string() ?: ""

      if (!res.isSuccessful || body.isBlank()) {
        onStepLogged(
          LyricsDebugStep(
            stepName = "Genius Search",
            url = searchUrl,
            httpStatus = code,
            summary = "HTTP $code: ${body.take(100)}",
            responseSnippet = body.take(200)
          )
        )
        return@withContext null
      }

      val json = JSONObject(body)
      val hits = json.optJSONObject("response")?.optJSONArray("hits")
      if (hits != null && hits.length() > 0) {
        val firstHit = hits.optJSONObject(0)?.optJSONObject("result")
        val fullTitle = firstHit?.optString("full_title")
        val url = firstHit?.optString("url")

        onStepLogged(
          LyricsDebugStep(
            stepName = "Genius Match",
            url = searchUrl,
            httpStatus = code,
            summary = "Matched: $fullTitle ($url)",
            responseSnippet = url
          )
        )
      }
      null
    } catch (e: Exception) {
      CrashLogger.w("GeniusLyricsSource", "Error querying Genius API", e)
      null
    }
  }

  private fun enc(s: String): String =
    try { URLEncoder.encode(s, "UTF-8") } catch (e: Exception) { CrashLogger.e("GeniusLyricsSource", "URLEncoder error", e); s }
}
