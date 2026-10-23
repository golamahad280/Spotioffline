package com.example.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.SongDao
import com.example.data.model.Song
import com.example.utils.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class CoverPipeline(
  private val context: Context,
  private val songDao: SongDao
) {
  companion object {
    private const val TAG = "CoverPipeline"
    private val queueSemaphore = Semaphore(2) // Max 2 concurrent image/network jobs
    private val musicBrainzMutex = Mutex() // 1 req/sec rate limit for MusicBrainz
    private var lastMusicBrainzTime = 0L
  }

  private val okClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  suspend fun autoFetchCoverForSong(song: Song, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
    if (song.coverLocked) {
      CrashLogger.d(TAG, "Song '${song.title}' cover is locked by user. Skipping cover fetch.")
      return@withContext false
    }

    if (!force && !song.albumArtUri.isNullOrBlank()) {
      return@withContext true
    }

    // 7-day retry rule for NOT_FOUND
    val now = System.currentTimeMillis()
    if (!force && song.coverStatus == "NOT_FOUND") {
      if (now - song.coverFetchedAt < 7L * 24 * 60 * 60 * 1000) {
        CrashLogger.d(TAG, "Song '${song.title}' recently marked NOT_FOUND. Skipping until 7-day cooldown.")
        return@withContext false
      }
    }

    queueSemaphore.withPermit {
      executeCoverCascade(song, customQuery = null)
    }
  }

  suspend fun searchCoverWithCustomQuery(song: Song, customQuery: String): Boolean = withContext(Dispatchers.IO) {
    queueSemaphore.withPermit {
      executeCoverCascade(song, customQuery = customQuery.trim())
    }
  }

  private suspend fun executeCoverCascade(song: Song, customQuery: String?): Boolean {
    val durationSec = (song.durationMs / 1000).toInt()
    val cleanedTitle = cleanFilenameOrTitle(song.title)
    val effectiveArtist = if (!isGenericMetadata(song.artist)) cleanFilenameOrTitle(song.artist) else null

    CrashLogger.i(TAG, "[CoverFetch Start] songId=${song.id}, title='$cleanedTitle', artist='$effectiveArtist', query='$customQuery'")
    songDao.updateCoverStatus(song.id, "FETCHING", System.currentTimeMillis(), "Searching CDN")

    // 1. Source: iTunes Multi-country Storefronts
    val itunesResult = searchITunesCascade(cleanedTitle, effectiveArtist, durationSec, customQuery)
    if (itunesResult != null) {
      val saved = saveCoverArtworkAndThumb(song.id, itunesResult.imageUrl)
      if (saved != null) {
        songDao.updateCoverSuccess(song.id, saved, "iTunes (${itunesResult.country ?: "Global"})", System.currentTimeMillis())
        applyIdentifiedMetadata(song, itunesResult.artistName, itunesResult.collectionName)
        CrashLogger.i(TAG, "Saved iTunes cover for '${song.title}'")
        return true
      }
    }

    // 2. Source: Deezer
    val deezerResult = searchDeezer(cleanedTitle, effectiveArtist, durationSec, customQuery)
    if (deezerResult != null) {
      val saved = saveCoverArtworkAndThumb(song.id, deezerResult.imageUrl)
      if (saved != null) {
        songDao.updateCoverSuccess(song.id, saved, "Deezer", System.currentTimeMillis())
        applyIdentifiedMetadata(song, deezerResult.artistName, deezerResult.albumName)
        CrashLogger.i(TAG, "Saved Deezer cover for '${song.title}'")
        return true
      }
    }

    // 3. Source: MusicBrainz + Cover Art Archive (rate limited 1 req/sec)
    val mbResult = searchMusicBrainzAndCAA(cleanedTitle, effectiveArtist)
    if (mbResult != null) {
      val saved = saveCoverArtworkAndThumb(song.id, mbResult)
      if (saved != null) {
        songDao.updateCoverSuccess(song.id, saved, "Cover Art Archive", System.currentTimeMillis())
        CrashLogger.i(TAG, "Saved CAA cover for '${song.title}'")
        return true
      }
    }

    // Not found across all sources
    songDao.updateCoverStatus(
      song.id,
      "NOT_FOUND",
      System.currentTimeMillis(),
      "Not found across iTunes, Deezer, MusicBrainz"
    )
    CrashLogger.w(TAG, "Cover NOT_FOUND for '${song.title}' after trying all sources.")
    return false
  }

  private data class ITunesCoverMatch(
    val imageUrl: String,
    val country: String?,
    val artistName: String?,
    val collectionName: String?
  )

  private suspend fun searchITunesCascade(
    title: String,
    artist: String?,
    durationSec: Int,
    customQuery: String?
  ): ITunesCoverMatch? {
    val storefronts = listOf("IN", "US", "GB", "BD", null)
    val terms = if (!customQuery.isNullOrBlank()) {
      listOf(customQuery)
    } else {
      val list = mutableListOf<String>()
      if (!artist.isNullOrBlank()) {
        list.add("$artist $title")
      }
      list.add(title)
      list
    }

    for (country in storefronts) {
      for (term in terms) {
        val url = StringBuilder("https://itunes.apple.com/search?term=").append(enc(term))
          .append("&entity=song&limit=10")
        if (country != null) {
          url.append("&country=").append(country)
        }

        try {
          val req = Request.Builder().url(url.toString())
            .header("User-Agent", "SpotiOffline/1.0")
            .build()
          val res = okClient.newCall(req).execute()
          val body = res.body?.string() ?: ""
          if (!res.isSuccessful || body.isBlank()) continue

          val json = JSONObject(body)
          val results = json.optJSONArray("results") ?: continue

          var bestUrl: String? = null
          var bestScore = Int.MAX_VALUE
          var matchedArtist: String? = null
          var matchedAlbum: String? = null

          val normTarget = normalize(title)

          for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val trackName = item.optString("trackName")
            val rawArt = item.optString("artworkUrl100")
            val trackTimeMs = item.optLong("trackTimeMillis", 0L)
            val trackSec = (trackTimeMs / 1000).toInt()

            if (rawArt.isBlank()) continue

            val normName = normalize(trackName)
            var score = 0

            if (durationSec > 0 && trackSec > 0) {
              val diff = abs(trackSec - durationSec)
              score += diff * 4
              if (diff > 10) score += 300 // Penalty outside +-10s
            }

            if (!normName.contains(normTarget) && !normTarget.contains(normName)) {
              score += 120
            }

            if (score < bestScore && (durationSec <= 0 || abs(trackSec - durationSec) <= 10)) {
              bestScore = score
              bestUrl = rawArt
              matchedArtist = item.optString("artistName")
              matchedAlbum = item.optString("collectionName")
            }
          }

          if (bestUrl != null) {
            // Quality fallback cascade: 3000x3000bb -> 1200x1200bb -> 600x600bb
            val candidatesUrls = listOf(
              bestUrl.replace("100x100bb", "3000x3000bb"),
              bestUrl.replace("100x100bb", "1200x1200bb"),
              bestUrl.replace("100x100bb", "600x600bb")
            )

            for (highResUrl in candidatesUrls) {
              if (checkUrlReachable(highResUrl)) {
                return ITunesCoverMatch(
                  imageUrl = highResUrl,
                  country = country,
                  artistName = matchedArtist,
                  collectionName = matchedAlbum
                )
              }
            }
          }
        } catch (e: Exception) {
          CrashLogger.w(TAG, "iTunes query error for $term ($country)", e)
        }
      }
    }
    return null
  }

  private data class DeezerCoverMatch(
    val imageUrl: String,
    val artistName: String?,
    val albumName: String?
  )

  private suspend fun searchDeezer(
    title: String,
    artist: String?,
    durationSec: Int,
    customQuery: String?
  ): DeezerCoverMatch? {
    val query = customQuery ?: if (!artist.isNullOrBlank()) "$artist $title" else title
    val url = "https://api.deezer.com/search?q=${enc(query)}&limit=10"

    try {
      val req = Request.Builder().url(url)
        .header("User-Agent", "SpotiOffline/1.0")
        .build()
      val res = okClient.newCall(req).execute()
      val body = res.body?.string() ?: ""
      if (!res.isSuccessful || body.isBlank()) return null

      val json = JSONObject(body)
      val data = json.optJSONArray("data") ?: return null

      val normTarget = normalize(title)
      var bestScore = Int.MAX_VALUE
      var bestMatch: DeezerCoverMatch? = null

      for (i in 0 until data.length()) {
        val item = data.optJSONObject(i) ?: continue
        val itemTitle = item.optString("title")
        val itemDuration = item.optInt("duration", 0)
        val albumObj = item.optJSONObject("album") ?: continue
        val coverUrl = albumObj.optString("cover_xl").ifBlank {
          albumObj.optString("cover_big").ifBlank { albumObj.optString("cover_medium") }
        }
        if (coverUrl.isBlank()) continue

        val normName = normalize(itemTitle)
        var score = 0
        if (durationSec > 0 && itemDuration > 0) {
          val diff = abs(itemDuration - durationSec)
          score += diff * 4
          if (diff > 10) score += 300
        }
        if (!normName.contains(normTarget) && !normTarget.contains(normName)) {
          score += 120
        }

        if (score < bestScore && (durationSec <= 0 || abs(itemDuration - durationSec) <= 10)) {
          bestScore = score
          bestMatch = DeezerCoverMatch(
            imageUrl = coverUrl,
            artistName = item.optJSONObject("artist")?.optString("name"),
            albumName = albumObj.optString("title")
          )
        }
      }
      return bestMatch
    } catch (e: Exception) {
      CrashLogger.w(TAG, "Deezer query error for $query", e)
      return null
    }
  }

  private suspend fun searchMusicBrainzAndCAA(title: String, artist: String?): String? {
    musicBrainzMutex.withLock {
      val now = System.currentTimeMillis()
      val elapsed = now - lastMusicBrainzTime
      if (elapsed < 1000L) {
        delay(1000L - elapsed)
      }
      lastMusicBrainzTime = System.currentTimeMillis()
    }

    try {
      val query = if (!artist.isNullOrBlank()) {
        "recording:${enc(title)}+AND+artist:${enc(artist)}"
      } else {
        "recording:${enc(title)}"
      }
      val url = "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=5"

      val req = Request.Builder().url(url)
        .header("User-Agent", "SpotiOffline/1.0 (contact@example.com)")
        .build()

      val res = okClient.newCall(req).execute()
      val body = res.body?.string() ?: ""
      if (!res.isSuccessful || body.isBlank()) return null

      val json = JSONObject(body)
      val recordings = json.optJSONArray("recordings") ?: return null

      for (i in 0 until recordings.length()) {
        val rec = recordings.optJSONObject(i) ?: continue
        val releases = rec.optJSONArray("releases") ?: continue
        for (r in 0 until releases.length()) {
          val rel = releases.optJSONObject(r) ?: continue
          val mbid = rel.optString("id")
          if (mbid.isNotBlank()) {
            val caaUrl = "https://coverartarchive.org/release/$mbid/front"
            if (checkUrlReachable(caaUrl)) {
              return caaUrl
            }
          }
        }
      }
    } catch (e: Exception) {
      CrashLogger.w(TAG, "MusicBrainz error for $title", e)
    }
    return null
  }

  private fun checkUrlReachable(imageUrl: String): Boolean {
    return try {
      val req = Request.Builder().url(imageUrl).head().build()
      val res = okClient.newCall(req).execute()
      res.isSuccessful
    } catch (_: Exception) {
      false
    }
  }

  private suspend fun saveCoverArtworkAndThumb(songId: String, imageUrl: String): String? = withContext(Dispatchers.IO) {
    try {
      val req = Request.Builder().url(imageUrl).build()
      val res = okClient.newCall(req).execute()
      if (!res.isSuccessful) return@withContext null

      val bytes = res.body?.bytes() ?: return@withContext null
      if (bytes.isEmpty()) return@withContext null

      val artDir = File(context.filesDir, "album_art").apply { mkdirs() }
      val safeId = songId.hashCode().toString().replace("-", "n")

      // 1. Save full-res JPEG
      val fullFile = File(artDir, "art_$safeId.jpg")
      FileOutputStream(fullFile).use { out ->
        out.write(bytes)
        out.flush()
      }

      // 2. Generate and save 512px thumbnail for list views
      try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
          sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        if (decoded != null) {
          val thumb = Bitmap.createScaledBitmap(decoded, 512, 512, true)
          val thumbFile = File(artDir, "thumb_$safeId.jpg")
          FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
          }
          if (thumb != decoded) thumb.recycle()
          decoded.recycle()
        }
      } catch (e: Exception) {
        CrashLogger.w(TAG, "Error generating thumbnail for $songId", e)
      }

      Uri.fromFile(fullFile).toString()
    } catch (oom: OutOfMemoryError) {
      CrashLogger.e(TAG, "OOM in saveCoverArtworkAndThumb", oom)
      null
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error saving cover from $imageUrl", e)
      null
    }
  }

  private suspend fun applyIdentifiedMetadata(song: Song, artist: String?, album: String?) {
    val newArtist = if (!song.artistLocked && isGenericMetadata(song.artist) && !artist.isNullOrBlank()) {
      artist
    } else song.artist

    val newAlbum = if (!song.titleLocked && (song.album.isBlank() || isGenericMetadata(song.album)) && !album.isNullOrBlank()) {
      album
    } else song.album

    if (newArtist != song.artist || newAlbum != song.album) {
      songDao.updateMetadata(song.id, song.title, newArtist, newAlbum)
      CrashLogger.i(TAG, "Updated metadata for ${song.id}: artist='$newArtist', album='$newAlbum'")
    }
  }

  private fun cleanFilenameOrTitle(raw: String): String {
    var s = raw.trim().replace(Regex("\\.(mp3|m4a|flac|wav|aac|ogg|opus)$", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("(?i)[\\(\\[]?\\s*\\b\\d{2,4}\\s*(k|kbps|kb/s)\\b\\s*[\\)\\]]?"), " ")
    s = s.replace(Regex("(?i)\\[.*?(official|video|audio|lyrics?|remaster|live).*?\\]"), " ")
    s = s.replace(Regex("(?i)\\(.*?(official|video|audio|lyrics?|remaster|live).*?\\)"), " ")
    s = s.replace('_', ' ')
    s = s.replace(Regex("\\s+"), " ").trim()
    return s.ifBlank { raw.trim() }
  }

  private fun isGenericMetadata(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    val v = value.trim().lowercase()
    return v in setOf("unknown artist", "unknown album", "unknown", "<unknown>", "untitled", "shareme", "download", "music")
  }

  private fun normalize(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]"), "").trim()

  private fun enc(s: String): String =
    try { URLEncoder.encode(s, "UTF-8") } catch (_: Exception) { s }
}
