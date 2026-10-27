package com.example.data.lyrics.sources

import android.content.Context
import com.example.data.lyrics.LyricsDebugStep
import com.example.utils.CrashLogger
import com.example.utils.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class OpenAILyricsSource(private val context: Context) : LyricsSource {
  override val name: String = "OpenAI AI"

  private val okClient = OkHttpClient.Builder()
    .connectTimeout(120, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .build()

  companion object {
    private const val TAG = "OpenAILyricsSource"

    fun redactApiKey(key: String?): String {
      if (key.isNullOrBlank()) return "sk-****"
      val trimmed = key.trim()
      return if (trimmed.length > 8) {
        "sk-****" + trimmed.takeLast(4)
      } else {
        "sk-****"
      }
    }
  }

  init {
    // Delete deprecated Gemini API key if present in SharedPreferences
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    if (prefs.contains("gemini_api_key")) {
      prefs.edit().remove("gemini_api_key").apply()
    }
  }

  private fun getApiKey(): String? {
    val key = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
      .getString("openai_api_key", "")?.trim()
    return if (key.isNullOrBlank()) null else key
  }

  private fun getChatModel(): String {
    return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
      .getString("openai_chat_model", "gpt-4o-mini")?.trim()?.ifBlank { "gpt-4o-mini" } ?: "gpt-4o-mini"
  }

  private fun getTranscriptionModel(): String {
    return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
      .getString("openai_transcription_model", "whisper-1")?.trim()?.ifBlank { "whisper-1" } ?: "whisper-1"
  }

  override suspend fun fetchLyrics(
    cleanedTitle: String,
    effectiveArtist: String?,
    effectiveAlbum: String?,
    durationSec: Int,
    filePath: String?,
    onStepLogged: (LyricsDebugStep) -> Unit
  ): LyricsSourceResult? = withContext(Dispatchers.IO) {
    val apiKey = getApiKey() ?: return@withContext null
    if (filePath == null) return@withContext null
    val audioFile = File(filePath)
    if (!audioFile.exists() || audioFile.length() == 0L) return@withContext null

    generateLyricsFromAudio(audioFile, durationSec, onStepLogged, apiKey)
  }

  suspend fun testApiKey(apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) {
      return@withContext Pair(false, "No API key entered.")
    }

    try {
      val req = Request.Builder()
        .url("https://api.openai.com/v1/models")
        .header("Authorization", "Bearer ${apiKey.trim()}")
        .get()
        .build()

      val res = okClient.newCall(req).execute()
      val code = res.code
      val bodyStr = res.body?.string() ?: ""

      if (res.isSuccessful) {
        Pair(true, "✓ Connected to OpenAI API successfully")
      } else {
        val errReason = parseErrorResponse(code, bodyStr)
        Pair(false, "✗ Error (HTTP $code): $errReason")
      }
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Test key exception", e)
      Pair(false, "✗ Connection failed: ${e.message}")
    }
  }

  suspend fun generateLyricsFromAudio(
    audioFile: File,
    durationSec: Int,
    onStepLogged: (LyricsDebugStep) -> Unit,
    apiKeyOverride: String? = null
  ): LyricsSourceResult? = withContext(Dispatchers.IO) {
    val apiKey = apiKeyOverride ?: getApiKey()
    if (apiKey == null) {
      onStepLogged(LyricsDebugStep("OpenAI AI", "", null, "No OpenAI API Key entered in Settings"))
      return@withContext null
    }

    // Check file size (24 MB limit for OpenAI 25 MB max)
    if (audioFile.length() > 24 * 1024 * 1024L) {
      val sizeMb = audioFile.length() / (1024 * 1024)
      val errText = "Audio file ($sizeMb MB) exceeds 24 MB OpenAI API limit"
      onStepLogged(LyricsDebugStep("OpenAI Audio", audioFile.name, 413, errText))
      throw IllegalStateException(errText)
    }

    try {
      val transcriptionModel = getTranscriptionModel()
      val mimeType = getMimeType(audioFile)

      val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("model", transcriptionModel)
        .addFormDataPart("response_format", "verbose_json")
        .addFormDataPart("timestamp_granularities[]", "segment")
        .addFormDataPart(
          "file",
          audioFile.name,
          audioFile.asRequestBody(mimeType.toMediaType())
        )
        .build()

      val req = Request.Builder()
        .url("https://api.openai.com/v1/audio/transcriptions")
        .header("Authorization", "Bearer $apiKey")
        .post(requestBody)
        .build()

      val bodyStr = executeWithRetry(req, onStepLogged, "OpenAI Transcribe", apiKey)
        ?: return@withContext null

      val segments = parseWhisperSegments(bodyStr)
      if (segments.isEmpty()) {
        onStepLogged(LyricsDebugStep("OpenAI Validation", "", null, "No audio segments transcribed"))
        return@withContext null
      }

      val lrcLines = convertSegmentsToLrc(segments)
      if (lrcLines.isEmpty() || !validateTimestamps(lrcLines, durationSec)) {
        onStepLogged(LyricsDebugStep("OpenAI Validation", "", null, "Transcribed lyrics failed timestamp validation"))
        return@withContext null
      }

      val formattedLrc = LrcParser.formatToLrc(lrcLines)
      onStepLogged(
        LyricsDebugStep(
          "OpenAI Success",
          "https://api.openai.com/v1/audio/transcriptions",
          200,
          "Successfully generated ${lrcLines.size} synced lines via OpenAI ($transcriptionModel)",
          formattedLrc.take(200)
        )
      )

      LyricsSourceResult(
        syncedLyrics = formattedLrc,
        sourceName = "AI-generated (OpenAI)",
        isAiGenerated = true
      )
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error generating lyrics with OpenAI", e)
      onStepLogged(LyricsDebugStep("OpenAI Exception", "", null, "Error: ${e.message}", e.localizedMessage))
      throw e
    }
  }

  suspend fun autoSyncLyricsWithAudio(
    audioFile: File,
    plainLyrics: String,
    durationSec: Int,
    onStepLogged: (LyricsDebugStep) -> Unit
  ): String = withContext(Dispatchers.IO) {
    val apiKey = getApiKey() ?: throw IllegalStateException("Please enter a valid OpenAI API Key in Settings first.")

    if (audioFile.length() > 24 * 1024 * 1024L) {
      throw IllegalStateException("Audio file exceeds 24 MB OpenAI API limit.")
    }

    // Step 1: Transcribe audio to segments
    val transcriptionModel = getTranscriptionModel()
    val mimeType = getMimeType(audioFile)

    val requestBody = MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("model", transcriptionModel)
      .addFormDataPart("response_format", "verbose_json")
      .addFormDataPart("timestamp_granularities[]", "segment")
      .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mimeType.toMediaType()))
      .build()

    val req = Request.Builder()
      .url("https://api.openai.com/v1/audio/transcriptions")
      .header("Authorization", "Bearer $apiKey")
      .post(requestBody)
      .build()

    val bodyStr = executeWithRetry(req, onStepLogged, "OpenAI AutoSync Audio", apiKey)
      ?: throw IllegalStateException("Failed to transcribe audio for alignment.")

    val segments = parseWhisperSegments(bodyStr)
    if (segments.isEmpty()) {
      throw IllegalStateException("Audio transcription produced no segments for alignment.")
    }

    val segmentsJsonArray = JSONArray()
    for (seg in segments) {
      segmentsJsonArray.put(JSONObject().apply {
        put("start", seg.start)
        put("text", seg.text)
      })
    }

    // Step 2: Use Chat model to map exact user lines to segment timestamps
    val chatModel = getChatModel()
    val promptSystem = """
      You are a lyric synchronization engine.
      You are given audio segments with start times (in seconds) and exact user pasted lyric lines.
      Map EACH user line to its best matching start timestamp from the audio segments.
      Return JSON object with key "mappings": [{"line": "<user line>", "start": <start_seconds>}].
      DO NOT change, translate, or invent any line text. Maintain exact line text and order.
    """.trimIndent()

    val userContent = "Audio segments:\n$segmentsJsonArray\n\nUser lines:\n$plainLyrics"

    val chatJson = JSONObject().apply {
      put("model", chatModel)
      put("response_format", JSONObject().put("type", "json_object"))
      put("temperature", 0)
      put("messages", JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", promptSystem))
        put(JSONObject().put("role", "user").put("content", userContent))
      })
    }

    val chatReq = Request.Builder()
      .url("https://api.openai.com/v1/chat/completions")
      .header("Authorization", "Bearer $apiKey")
      .post(chatJson.toString().toRequestBody("application/json".toMediaType()))
      .build()

    val chatResponseStr = executeWithRetry(chatReq, onStepLogged, "OpenAI AutoSync Align", apiKey)
      ?: throw IllegalStateException("AI line alignment request failed.")

    val chatJsonObj = JSONObject(chatResponseStr)
    val contentStr = chatJsonObj.optJSONArray("choices")?.optJSONObject(0)
      ?.optJSONObject("message")?.optString("content")
      ?: throw IllegalStateException("Empty completion response from OpenAI.")

    val mappedLrcLines = parseAlignedMappings(contentStr, plainLyrics, durationSec)
      ?: throw IllegalStateException("AI line alignment failed validation. Please use manual Tap-to-sync.")

    LrcParser.formatToLrc(mappedLrcLines)
  }

  suspend fun cleanMetadataWithAi(
    filename: String,
    title: String?,
    artist: String?
  ): JSONObject? = withContext(Dispatchers.IO) {
    val apiKey = getApiKey() ?: return@withContext null
    val chatModel = getChatModel()

    val systemPrompt = """
      You are a music metadata extraction tool.
      Extract clean song title, artist, titleEnglish, artistEnglish, and language from the given raw filename or title/artist.
      Return ONLY a JSON object with keys: "title", "artist", "titleEnglish", "artistEnglish", "language".
      Never write lyrics.
    """.trimIndent()

    val userContent = "Raw filename: $filename, Current title: $title, Current artist: $artist"

    val body = JSONObject().apply {
      put("model", chatModel)
      put("response_format", JSONObject().put("type", "json_object"))
      put("temperature", 0)
      put("messages", JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        put(JSONObject().put("role", "user").put("content", userContent))
      })
    }

    val req = Request.Builder()
      .url("https://api.openai.com/v1/chat/completions")
      .header("Authorization", "Bearer $apiKey")
      .post(body.toString().toRequestBody("application/json".toMediaType()))
      .build()

    try {
      val res = okClient.newCall(req).execute()
      if (!res.isSuccessful) return@withContext null
      val resBody = res.body?.string() ?: return@withContext null
      val contentStr = JSONObject(resBody).optJSONArray("choices")?.optJSONObject(0)
        ?.optJSONObject("message")?.optString("content") ?: return@withContext null

      JSONObject(contentStr)
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Metadata cleanup AI error", e)
      null
    }
  }

  private suspend fun executeWithRetry(
    request: Request,
    onStepLogged: (LyricsDebugStep) -> Unit,
    stepName: String,
    apiKey: String
  ): String? {
    var attempt = 0
    var delayMs = 1000L

    while (attempt < 3) {
      attempt++
      try {
        val res = okClient.newCall(request).execute()
        val code = res.code
        val bodyStr = res.body?.string() ?: ""

        if (res.isSuccessful) {
          onStepLogged(
            LyricsDebugStep(
              stepName = stepName,
              url = request.url.toString(),
              httpStatus = code,
              summary = "HTTP $code: Success",
              responseSnippet = bodyStr.take(150)
            )
          )
          return bodyStr
        }

        val errReason = parseErrorResponse(code, bodyStr)
        onStepLogged(
          LyricsDebugStep(
            stepName = stepName,
            url = request.url.toString(),
            httpStatus = code,
            summary = "HTTP $code: $errReason",
            responseSnippet = bodyStr.take(150)
          )
        )

        if (code == 429 || code >= 500) {
          if (attempt < 3) {
            delay(delayMs)
            delayMs *= 2
            continue
          }
        }
        return null
      } catch (e: Exception) {
        onStepLogged(
          LyricsDebugStep(
            stepName = stepName,
            url = request.url.toString(),
            httpStatus = null,
            summary = "Network exception: ${e.message}"
          )
        )
        if (attempt < 3) {
          delay(delayMs)
          delayMs *= 2
        } else {
          return null
        }
      }
    }
    return null
  }

  private fun parseErrorResponse(code: Int, bodyStr: String): String {
    return when (code) {
      401 -> "401 Invalid API key (Authentication failed)"
      429 -> "429 Rate limit or billing quota exceeded"
      413 -> "413 Payload / file too large"
      else -> {
        try {
          JSONObject(bodyStr).optJSONObject("error")?.optString("message") ?: bodyStr.take(120)
        } catch (e: Exception) {
          CrashLogger.e("OpenAILyricsSource", "Error parsing error response JSON", e)
          bodyStr.take(120)
        }
      }
    }
  }

  private data class WhisperSegment(val start: Double, val text: String)

  private fun parseWhisperSegments(jsonStr: String): List<WhisperSegment> {
    val list = mutableListOf<WhisperSegment>()
    try {
      val json = JSONObject(jsonStr)
      val segments = json.optJSONArray("segments") ?: return emptyList()
      for (i in 0 until segments.length()) {
        val seg = segments.getJSONObject(i)
        val start = seg.optDouble("start", 0.0)
        val text = seg.optString("text", "").trim()
        if (text.isNotBlank()) {
          list.add(WhisperSegment(start, text))
        }
      }
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error parsing Whisper segments", e)
    }
    return list
  }

  private fun convertSegmentsToLrc(segments: List<WhisperSegment>): List<com.example.data.model.LrcLine> {
    val result = mutableListOf<com.example.data.model.LrcLine>()
    var lastText = ""

    for (seg in segments) {
      if (seg.text.lowercase() == lastText.lowercase()) continue // Drop hallucination repetitions
      lastText = seg.text

      val ms = (seg.start * 1000).toLong()
      result.add(com.example.data.model.LrcLine(ms, seg.text))
    }
    return result
  }

  private fun parseAlignedMappings(
    contentStr: String,
    plainLyrics: String,
    durationSec: Int
  ): List<com.example.data.model.LrcLine>? {
    try {
      val json = JSONObject(contentStr)
      val mappings = json.optJSONArray("mappings") ?: return null
      val result = mutableListOf<com.example.data.model.LrcLine>()

      val maxDurationMs = if (durationSec > 0) (durationSec + 15) * 1000L else Long.MAX_VALUE
      var prevMs = -1L

      for (i in 0 until mappings.length()) {
        val item = mappings.getJSONObject(i)
        val line = item.optString("line", "").trim()
        val startSec = item.optDouble("start", -1.0)
        if (line.isBlank() || startSec < 0.0) continue

        val ms = (startSec * 1000).toLong()
        if (ms < prevMs || ms > maxDurationMs) {
          return null // Failed strictly increasing condition
        }
        prevMs = ms
        result.add(com.example.data.model.LrcLine(ms, line))
      }

      return if (result.isNotEmpty()) result else null
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Failed parsing aligned mappings", e)
      return null
    }
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

  private fun getMimeType(file: File): String {
    return when (file.extension.lowercase()) {
      "mp3" -> "audio/mpeg"
      "m4a", "aac" -> "audio/mp4"
      "wav" -> "audio/wav"
      "flac" -> "audio/flac"
      "ogg", "opus" -> "audio/ogg"
      else -> "audio/mpeg"
    }
  }
}
