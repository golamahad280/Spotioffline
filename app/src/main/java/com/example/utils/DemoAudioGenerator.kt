package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object DemoAudioGenerator {

  suspend fun generateDemoTracks(context: Context): List<Song> = withContext(Dispatchers.IO) {
    val demoDir = File(context.filesDir, "demo_audio").apply { mkdirs() }
    val artDir = File(context.filesDir, "demo_art").apply { mkdirs() }

    val tracks = listOf(
      DemoTrackDef(
        id = "demo_midnight_lights",
        title = "Midnight City Lights",
        artist = "Luna Echoes",
        album = "Neon Horizon",
        durationSec = 34,
        baseFreq = 220.0, // A3
        bgColor = 0xFF1B2A4A.toInt(),
        fgColor = 0xFF2E77D0.toInt(),
        lyrics = """
          [00:01.00]Neon reflections on the wet street
          [00:05.50]Driving into the quiet midnight beat
          [00:10.00]Skyline glowing under purple haze
          [00:14.50]Lost inside a nocturnal maze
          [00:19.00]Electric dreams whispering low
          [00:23.50]Every light puts on a show
          [00:28.00]Midnight city lights guiding the way
          [00:32.00]Fade out before the break of day
        """.trimIndent()
      ),
      DemoTrackDef(
        id = "demo_cosmic_drift",
        title = "Cosmic Drift",
        artist = "Solaris 88",
        album = "Astral Waves",
        durationSec = 30,
        baseFreq = 261.63, // C4
        bgColor = 0xFF2A143F.toInt(),
        fgColor = 0xFF8D67AB.toInt(),
        lyrics = """
          [00:01.00]Floating past Jupiter's ring
          [00:05.00]Hearing the stellar choir sing
          [00:09.50]Zero gravity in my veins
          [00:14.00]Escaping all earthly chains
          [00:18.50]Solar flares illuminate the dark
          [00:23.00]Leaving behind a burning spark
          [00:27.00]Drifting through the deep infinite sea
        """.trimIndent()
      ),
      DemoTrackDef(
        id = "demo_sunset_boulevard",
        title = "Sunset Boulevard",
        artist = "Golden Hour Collective",
        album = "Summer Reverie",
        durationSec = 32,
        baseFreq = 293.66, // D4
        bgColor = 0xFF3D1F0A.toInt(),
        fgColor = 0xFFE8562A.toInt(),
        lyrics = """
          [00:01.00]Warm ocean breeze through the trees
          [00:05.50]Golden hour bringing me to my knees
          [00:10.00]Sun is dipping in a sea of gold
          [00:14.50]Memories we will forever hold
          [00:19.00]Laughing into the amber glow
          [00:23.50]Letting the gentle currents flow
          [00:28.00]Until the evening stars appear
        """.trimIndent()
      ),
      DemoTrackDef(
        id = "demo_electric_pulse",
        title = "Electric Pulse",
        artist = "CyberSynth",
        album = "Digital Dreams",
        durationSec = 28,
        baseFreq = 329.63, // E4
        bgColor = 0xFF0D3320.toInt(),
        fgColor = 0xFF1DB954.toInt(),
        lyrics = """
          [00:01.00]Feel the frequency begin to rise
          [00:05.00]Digital sparks before your eyes
          [00:09.00]Eight-zero-eight pumping through the floor
          [00:13.50]Coming back to dance once more
          [00:17.50]Laser lines in a neon haze
          [00:21.50]Living in the synthwave craze
          [00:25.50]Pure energy, pure delight
        """.trimIndent()
      )
    )

    tracks.map { def ->
      val audioFile = File(demoDir, "${def.id}.wav")
      if (!audioFile.exists() || audioFile.length() < 1000) {
        generateWavFile(audioFile, def.durationSec, def.baseFreq)
      }

      val artFile = File(artDir, "${def.id}.jpg")
      if (!artFile.exists()) {
        generateCoverArt(artFile, def.title, def.artist, def.bgColor, def.fgColor)
      }

      Song(
        id = def.id,
        title = def.title,
        artist = def.artist,
        album = def.album,
        durationMs = def.durationSec * 1000L,
        uriString = Uri.fromFile(audioFile).toString(),
        filePath = audioFile.absolutePath,
        albumArtUri = Uri.fromFile(artFile).toString(),
        hasLyrics = true,
        isSyncedLyrics = true,
        lyricsText = def.lyrics,
        isLiked = (def.id == "demo_electric_pulse" || def.id == "demo_midnight_lights"),
        playCount = if (def.id == "demo_midnight_lights") 5 else 1,
        lastPlayedAt = if (def.id == "demo_midnight_lights") System.currentTimeMillis() - 60000 else 0L,
        addedAt = System.currentTimeMillis() - (tracks.indexOf(def) * 3600000L),
        year = 2024,
        trackNumber = tracks.indexOf(def) + 1,
        fileSize = audioFile.length(),
        syncOffsetMs = 0L
      )
    }
  }

  private fun generateCoverArt(
    file: File,
    title: String,
    artist: String,
    bgColor: Int,
    fgColor: Int
  ) {
    try {
      val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)

      // Background gradient-like draw
      val bgPaint = Paint().apply {
        color = bgColor
        style = Paint.Style.FILL
      }
      canvas.drawRect(0f, 0f, 512f, 512f, bgPaint)

      // Decorative vinyl/soundwave ring
      val ringPaint = Paint().apply {
        color = fgColor
        style = Paint.Style.STROKE
        strokeWidth = 14f
        isAntiAlias = true
      }
      canvas.drawCircle(256f, 210f, 130f, ringPaint)
      canvas.drawCircle(256f, 210f, 70f, ringPaint.apply { strokeWidth = 8f })
      canvas.drawCircle(256f, 210f, 20f, ringPaint.apply { style = Paint.Style.FILL })

      // Text
      val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 32f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
      }
      canvas.drawText(title, 256f, 410f, textPaint)

      val artistPaint = Paint().apply {
        color = 0xFFB3B3B3.toInt()
        textSize = 22f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
      }
      canvas.drawText(artist, 256f, 450f, artistPaint)

      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.flush()
      }
    } catch (e: Exception) {
      CrashLogger.e("DemoAudioGenerator", "Failed generating art file", e)
    }
  }

  private fun generateWavFile(file: File, durationSec: Int, baseFreq: Double) {
    val sampleRate = 22050
    val totalSamples = sampleRate * durationSec
    val numChannels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
    val blockAlign = numChannels * (bitsPerSample / 8)
    val dataSize = totalSamples * blockAlign

    val pcmData = ByteArray(dataSize)
    val buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)

    val chordNotes = listOf(baseFreq, baseFreq * 1.25, baseFreq * 1.5, baseFreq * 2.0)
    val beatInterval = (sampleRate * 0.5).toInt() // 120 bpm pulse

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / sampleRate
      val chordIndex = ((i / (sampleRate * 4)) % chordNotes.size)
      val freq = chordNotes[chordIndex]

      // Pleasant smooth synth wave with harmonics
      var sampleVal = (sin(2.0 * Math.PI * freq * t) * 0.45) +
          (sin(2.0 * Math.PI * (freq * 0.5) * t) * 0.25) +
          (sin(2.0 * Math.PI * (freq * 1.5) * t) * 0.15)

      // Add kick rhythm pulse
      val beatPos = i % beatInterval
      if (beatPos < (sampleRate * 0.08)) {
        val kickT = beatPos.toDouble() / sampleRate
        val kickDecay = (1.0 - (beatPos.toDouble() / (sampleRate * 0.08)))
        sampleVal += sin(2.0 * Math.PI * 65.0 * kickT) * 0.35 * kickDecay
      }

      // Smooth fade in and fade out
      val fadeIn = if (i < sampleRate) i.toDouble() / sampleRate else 1.0
      val fadeOut = if (i > totalSamples - sampleRate) (totalSamples - i).toDouble() / sampleRate else 1.0
      val envelope = fadeIn * fadeOut

      val finalSample = (sampleVal * envelope * 24000.0).toInt().coerceIn(-32767, 32767).toShort()
      buffer.putShort(finalSample)
    }

    FileOutputStream(file).use { out ->
      // Write 44-byte WAV header
      out.write("RIFF".toByteArray())
      out.write(intToByteArray(dataSize + 36))
      out.write("WAVE".toByteArray())
      out.write("fmt ".toByteArray())
      out.write(intToByteArray(16)) // subchunk1size (16 for PCM)
      out.write(shortToByteArray(1)) // audio format (1 = PCM)
      out.write(shortToByteArray(numChannels.toShort()))
      out.write(intToByteArray(sampleRate))
      out.write(intToByteArray(byteRate))
      out.write(shortToByteArray(blockAlign.toShort()))
      out.write(shortToByteArray(bitsPerSample.toShort()))
      out.write("data".toByteArray())
      out.write(intToByteArray(dataSize))
      out.write(pcmData)
      out.flush()
    }
  }

  private fun intToByteArray(value: Int): ByteArray {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
  }

  private fun shortToByteArray(value: Short): ByteArray {
    return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
  }

  private data class DemoTrackDef(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val baseFreq: Double,
    val bgColor: Int,
    val fgColor: Int,
    val lyrics: String
  )
}
