package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.graphics.Color
import android.media.ExifInterface
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
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max

object ImageUtils {
  private const val TAG = "ImageUtils"

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
      } catch (oom: OutOfMemoryError) {
        CrashLogger.e(TAG, "OutOfMemoryError in saveArtworkBytes", oom)
        null
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Exception in saveArtworkBytes", e)
        null
      }
    }

  suspend fun saveCustomArtwork(context: Context, uri: Uri, songId: String): String? =
    withContext(Dispatchers.IO) {
      try {
        val cr = context.contentResolver

        // 1. Check dimensions first to avoid allocating huge bitmaps
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return@withContext null

        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return@withContext null

        val maxDim = max(origW, origH)
        var sampleSize = 1
        while (maxDim / sampleSize > 3000) {
          sampleSize *= 2
        }

        // 2. Decode bitmap with inSampleSize
        val decodeOptions = BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var decoded: Bitmap? = cr.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return@withContext null

        // 3. Handle EXIF orientation rotation
        try {
          cr.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val orientation = exif.getAttributeInt(
              ExifInterface.TAG_ORIENTATION,
              ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
              ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
              ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
              ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
              ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
              ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            if (!matrix.isIdentity) {
              val rotated = Bitmap.createBitmap(
                decoded!!, 0, 0, decoded!!.width, decoded!!.height, matrix, true
              )
              if (rotated != decoded) {
                decoded!!.recycle()
                decoded = rotated
              }
            }
          }
        } catch (e: Exception) {
          CrashLogger.w(TAG, "Exif orientation read failed, continuing with unrotated bitmap", e)
        }

        // 4. Save to filesDir/album_art/custom_<songId>.jpg (JPEG quality 95)
        val artDir = File(context.filesDir, "album_art").apply { mkdirs() }
        val safeName = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(artDir, "custom_$safeName.jpg")
        FileOutputStream(file).use { out ->
          decoded!!.compress(Bitmap.CompressFormat.JPEG, 95, out)
          out.flush()
        }
        decoded?.recycle()

        Uri.fromFile(file).toString()
      } catch (oom: OutOfMemoryError) {
        CrashLogger.e(TAG, "OutOfMemoryError in saveCustomArtwork", oom)
        null
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Exception in saveCustomArtwork", e)
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
    } catch (oom: OutOfMemoryError) {
      CrashLogger.e(TAG, "OutOfMemoryError in extractDominantColor", oom)
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Exception in extractDominantColor", e)
    }
    return SpotifyGreen
  }
}
