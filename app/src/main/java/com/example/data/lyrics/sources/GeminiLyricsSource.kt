package com.example.data.lyrics.sources

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.lyrics.LyricsDebugStep
import com.example.utils.CrashLogger
import com.example.utils.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GeminiLyricsSource(private val context: Context) : LyricsSource {
  override val name: String = "Gemini AI"

  private val okClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
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
    val apiKey = prefs.getString("gemini_api_key", "")?.trim()
    if (apiKey.isNullOrBlank()) return@withContext null

    if (filePath == null) return@withContext null
    val audioFile = File(filePath)
    if (!audioFile.exists() || audioFile.length() == 0L) return@withContext null

    generateLyricsFromAudio(audioFile, durationSec, onStepLogged, apiKey)
  }

  suspend fun generateLyricsFromAudio(
    audioFile: File,
    durationSec: Int,
    onStepLogged: (LyricsDebugStep) -> Unit,
    apiKeyOverride: String? = null
  ): LyricsSourceResult? = withContext(Dispatchers.IO) {
    val apiKey = apiKeyOverride ?: context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
      .getString("gemini_api_key", "")?.trim()
    if (apiKey.isNullOrBlank()) {
      onStepLogged(LyricsDebugStep("Gemini AI", "", null, "No Gemini API Key entered in Settings"))
      return@withContext null
    }

    try {
      val (audioBytes, mimeType) = readAudioBytesAndMime(audioFile) ?: return@withContext null
      val base64Data = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

      val prompt = """
        You are an expert audio lyrics transcriber.
        Transcribe the vocal lyrics in this audio into strict LRC format with exact timestamps for each line.
        Rules:
        1. Write the lyrics in their ORIGINAL script (e.g. Bengali script for Bengali songs, Devanagari for Hindi, Latin for English/Spanish).
        2. Format every single line strictly as: [mm:ss.xx] Line lyrics text
        3. Timestamps must be strictly increasing and not exceed audio duration of ${durationSec}s.
        4. Do NOT output markdown code fences, headers, or conversational text. Output ONLY the raw LRC text.
      """.trimIndent()

      val result = callGeminiGenerateContent(apiKey, prompt, base64Data, mimeType, onStepLogged, "Gemini Transcribe")
      if (result.isNullOrBlank()) return@withContext null

      val parsed = LrcParser.parse(result)
      if (parsed.isEmpty() || !validateTimestamps(parsed, durationSec)) {
        onStepLogged(LyricsDebugStep("Gemini Validation", "", null, "Generated lyrics failed timestamp validation", result.take(200)))
        return@withContext null
      }

      val formattedLrc = LrcParser.formatToLrc(parsed)
      onStepLogged(LyricsDebugStep("Gemini Success", "", 200, "Successfully generated ${parsed.size} synced lines via Gemini", formattedLrc.take(200)))

      LyricsSourceResult(
        syncedLyrics = formattedLrc,
        sourceName = "AI-generated",
        isAiGenerated = true
      )
    } catch (e: Exception) {
      CrashLogger.e("GeminiLyricsSource", "Error generating lyrics with Gemini", e)
      onStepLogged(LyricsDebugStep("Gemini Exception", "", null, "Error: ${e.message}", e.localizedMessage))
      null
    }
  }

  suspend fun autoSyncLyricsWithAudio(
    audioFile: File,
    plainLyrics: String,
    durationSec: Int,
    onStepLogged: (LyricsDebugStep) -> Unit
  ): String? = withContext(Dispatchers.IO) {
    val apiKey = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
      .getString("gemini_api_key", "")?.trim()
    if (apiKey.isNullOrBlank()) {
      throw IllegalStateException("Please enter a valid Gemini API Key in Settings first.")
    }

    val (audioBytes, mimeType) = readAudioBytesAndMime(audioFile)
      ?: throw IllegalStateException("Audio file could not be read (${audioFile.name}).")
    val base64Data = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

    val prompt = """
      You are a professional music synchronization engine.
      Here is the song's audio along with the EXACT text of the lyrics:
      ---
      $plainLyrics
      ---
      Task:
      Synchronize each line of the lyrics with the audio.
      Rules:
      1. Keep the EXACT same lines in the EXACT same order and language/script.
      2. Do NOT add, omit, or modify words.
      3. Prefix each line with its start time tag in strict LRC format: [mm:ss.xx].
      4. Timestamps must be strictly increasing and <= ${durationSec} seconds.
      5. Output ONLY the raw LRC formatted text without markdown formatting or commentary.
    """.trimIndent()

    val result = callGeminiGenerateContent(apiKey, prompt, base64Data, mimeType, onStepLogged, "Gemini AutoSync")
      ?: throw IllegalStateException("Gemini returned empty response.")

    val parsed = LrcParser.parse(result)
    if (parsed.isEmpty() || !validateTimestamps(parsed, durationSec)) {
      throw IllegalStateException("AI generated invalid LRC timestamps. Please try manual 'Tap to sync'.")
    }

    LrcParser.formatToLrc(parsed)
  }

  private fun readAudioBytesAndMime(audioFile: File): Pair<ByteArray, String>? {
    try {
      val ext = audioFile.extension.lowercase()
      val mime = when (ext) {
        "mp3" -> "audio/mp3"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"
        else -> "audio/mp3"
      }

      val maxBytes = 14 * 1024 * 1024 // 14MB inline limit for Gemini API
      val length = audioFile.length()
      val bytes = if (length > maxBytes) {
        // Read first 14MB
        val buf = ByteArray(maxBytes)
        audioFile.inputStream().use { it.read(buf) }
        buf
      } else {
        audioFile.readBytes()
      }
      return Pair(bytes, mime)
    } catch (e: Exception) {
      CrashLogger.e("GeminiLyricsSource", "Error reading audio file", e)
      return null
    }
  }

  private fun callGeminiGenerateContent(
    apiKey: String,
    prompt: String,
    base64Data: String,
    mimeType: String,
    onStepLogged: (LyricsDebugStep) -> Unit,
    stepName: String
  ): String? {
    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

    val jsonBody = JSONObject().apply {
      val contents = JSONArray().apply {
        put(JSONObject().apply {
          val parts = JSONArray().apply {
            put(JSONObject().apply {
              put("inlineData", JSONObject().apply {
                put("mimeType", mimeType)
                put("data", base64Data)
              })
            })
            put(JSONObject().apply {
              put("text", prompt)
            })
          }
          put("parts", parts)
        })
      }
      put("contents", contents)
      put("generationConfig", JSONObject().apply {
        put("temperature", 0.1)
      })
    }

    val req = Request.Builder()
      .url(endpoint)
      .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
      .build()

    val res = okClient.newCall(req).execute()
    val code = res.code
    val bodyStr = res.body?.string() ?: ""

    if (!res.isSuccessful) {
      val errorMsg = try {
        JSONObject(bodyStr).optJSONObject("error")?.optString("message") ?: bodyStr
      } catch (_: Exception) {
        bodyStr
      }
      onStepLogged(
        LyricsDebugStep(
          stepName = stepName,
          url = "https://generativelanguage.googleapis.com/...:generateContent",
          httpStatus = code,
          summary = "HTTP $code: $errorMsg",
          responseSnippet = errorMsg.take(200)
        )
      )
      return null
    }

    val responseJson = JSONObject(bodyStr)
    val candidate = responseJson.optJSONArray("candidates")?.optJSONObject(0)
    val text = candidate?.optJSONObject("content")
      ?.optJSONArray("parts")
      ?.optJSONObject(0)
      ?.optString("text")

    return text?.replace("```lrc", "")?.replace("```", "")?.trim()
  }

  private fun validateTimestamps(lines: List<com.example.data.model.LrcLine>, durationSec: Int): Boolean {
    if (lines.isEmpty()) return false
    val maxDurationMs = if (durationSec > 0) (durationSec + 15) * 1000L else Long.MAX_VALUE
    var prev = -1L
    for (line in lines) {
      if (line.timestampMs < prev || line.timestampMs > maxDurationMs) {
        return false
      }
      prev = line.timestampMs
    }
    return true
  }
}
