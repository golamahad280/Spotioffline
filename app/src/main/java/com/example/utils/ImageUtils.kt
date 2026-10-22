package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.SpotifyBlue
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyOrange
import com.example.ui.theme.SpotifyPink
import com.example.ui.theme.SpotifyPurple
import com.example.ui.theme.SpotifyTeal
import com.example.ui.theme.SpotifyYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

object ImageUtils {
  private val VIBRANT_FALLBACK_COLORS = listOf(
    SpotifyGreen,
    SpotifyBlue,
    SpotifyPurple,
    SpotifyPink,
    SpotifyOrange,
    SpotifyYellow,
    SpotifyTeal
  )

  fun getFallbackColorForTrack(seed: String): Color {
    val index = abs(seed.hashCode()) % VIBRANT_FALLBACK_COLORS.size
    return VIBRANT_FALLBACK_COLORS[index]
  }

  suspend fun saveArtworkBytes(context: Context, bytes: ByteArray, id: String): String? =
    withContext(Dispatchers.IO) {
      try {
        val artDir = File(context.filesDir, "album_art").apply { mkdirs() }
        val safeName = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(artDir, "art_$safeName.jpg")
        FileOutputStream(file).use { out ->
          out.write(bytes)
          out.flush()
        }
        Uri.fromFile(file).toString()
      } catch (e: Exception) {
        null
      }
    }

  fun extractDominantColor(bitmap: Bitmap?): Color {
    if (bitmap == null) return SpotifyGreen
    try {
      // Sample a scaled down 24x24 version to quickly find highest saturation color
      val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, false)
      var maxSat = -1f
      var bestColorInt = 0

      for (x in 0 until 24) {
        for (y in 0 until 24) {
          val pixel = scaled.getPixel(x, y)
          val r = (pixel shr 16) and 0xFF
          val g = (pixel shr 8) and 0xFF
          val b = pixel and 0xFF
          val hsv = FloatArray(3)
          android.graphics.Color.RGBToHSV(r, g, b, hsv)
          val sat = hsv[1]
          val brightness = hsv[2]
          // Filter out near-black, near-white, or washed-out pixels
          if (brightness in 0.25f..0.85f && sat > maxSat) {
            maxSat = sat
            bestColorInt = pixel
          }
        }
      }

      if (maxSat > 0.15f) {
        return Color(bestColorInt)
      }
    } catch (_: Exception) {}
    return SpotifyGreen
  }
}
