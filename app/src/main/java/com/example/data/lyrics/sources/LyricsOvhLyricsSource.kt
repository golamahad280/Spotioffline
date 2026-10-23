package com.example.data.lyrics.sources

import com.example.data.lyrics.LyricsDebugStep
import com.example.utils.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsOvhLyricsSource : LyricsSource {
  override val name: String = "lyrics.ovh"

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
    if (effectiveArtist.isNullOrBlank()) return@withContext null

    val url = "https://api.lyrics.ovh/v1/${enc(effectiveArtist)}/${enc(cleanedTitle)}"
    try {
      val req = Request.Builder()
        .url(url)
        .header("User-Agent", "SpotiOffline/1.0")
        .build()

      val res = okClient.newCall(req).execute()
      val code = res.code
      val body = res.body?.string() ?: ""

      if (!res.isSuccessful || body.isBlank()) {
        onStepLogged(
          LyricsDebugStep(
            stepName = "lyrics.ovh",
            url = url,
            httpStatus = code,
            summary = "HTTP $code: ${body.take(100)}",
            responseSnippet = body.take(200)
          )
        )
        return@withContext null
      }

      val json = JSONObject(body)
      val lyrics = json.optString("lyrics")
      if (lyrics.isNotBlank()) {
        onStepLogged(
          LyricsDebugStep(
            stepName = "lyrics.ovh",
            url = url,
            httpStatus = code,
            summary = "Found plain lyrics (${lyrics.length} chars)",
            responseSnippet = lyrics.take(200)
          )
        )
        LyricsSourceResult(
          plainLyrics = lyrics.trim(),
          sourceName = "lyrics.ovh (Plain)"
        )
      } else {
        null
      }
    } catch (e: Exception) {
      CrashLogger.w("LyricsOvhLyricsSource", "Error querying lyrics.ovh", e)
      onStepLogged(
        LyricsDebugStep(
          stepName = "lyrics.ovh Exception",
          url = url,
          httpStatus = null,
          summary = "Error: ${e.message}",
          responseSnippet = e.localizedMessage
        )
      )
      null
    }
  }

  private fun enc(s: String): String =
    try { URLEncoder.encode(s, "UTF-8") } catch (_: Exception) { s }
}
