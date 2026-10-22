package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.example.data.local.SongDao
import com.example.data.model.ScanProgress
import com.example.data.model.Song
import com.example.data.network.NetworkClient
import com.example.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class AudioScanner(
  private val context: Context,
  private val songDao: SongDao
) {

  private val _scanProgress = MutableStateFlow(ScanProgress())
  val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

  suspend fun scanDeviceAudio(): Int = withContext(Dispatchers.IO) {
    _scanProgress.value = ScanProgress(isScanning = true, currentFileName = "Searching for audio files...")

    val existingSongIds = songDao.getAllSongIds().toSet()
    val foundSongs = mutableListOf<Song>()

    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.DATA,
      MediaStore.Audio.Media.YEAR,
      MediaStore.Audio.Media.TRACK,
      MediaStore.Audio.Media.SIZE,
      MediaStore.Audio.Media.DATE_ADDED
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    try {
      val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        sortOrder
      )

      cursor?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val yearCol = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
        val trackCol = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val dateAddedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

        val total = c.count
        var scanned = 0

        while (c.moveToNext()) {
          val mediaId = c.getLong(idCol)
          val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString()
          val filePath = if (dataCol != -1) c.getString(dataCol) else null
          val rawTitle = c.getString(titleCol) ?: "Unknown Track"
          val rawArtist = c.getString(artistCol) ?: "Unknown Artist"
          val rawAlbum = c.getString(albumCol) ?: "Unknown Album"
          val duration = c.getLong(durationCol)
          val year = if (yearCol != -1) c.getInt(yearCol) else 0
          val track = if (trackCol != -1) c.getInt(trackCol) else 0
          val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
          val dateAdded = if (dateAddedCol != -1) c.getLong(dateAddedCol) * 1000L else System.currentTimeMillis()

          scanned++
          _scanProgress.value = ScanProgress(
            totalFiles = total,
            scannedFiles = scanned,
            currentFileName = rawTitle,
            isScanning = true
          )

          // Clean title and artist if needed
          val (cleanedTitle, cleanedArtist) = cleanMetadata(rawTitle, rawArtist, filePath)

          // If already in DB, keep existing lyrics and cover
          if (existingSongIds.contains(contentUri)) {
            continue
          }

          // Read embedded artwork if available
          var embeddedArtUri: String? = null
          try {
            val retriever = MediaMetadataRetriever()
            if (filePath != null && File(filePath).exists()) {
              retriever.setDataSource(filePath)
            } else {
              retriever.setDataSource(context, Uri.parse(contentUri))
            }
            val embeddedPic = retriever.embeddedPicture
            if (embeddedPic != null && embeddedPic.isNotEmpty()) {
              embeddedArtUri = ImageUtils.saveArtworkBytes(context, embeddedPic, "media_$mediaId")
            }
            retriever.release()
          } catch (_: Exception) {}

          val song = Song(
            id = contentUri,
            title = cleanedTitle,
            artist = cleanedArtist,
            album = if (rawAlbum.contains("<unknown>", true)) "Single" else rawAlbum,
            durationMs = duration,
            uriString = contentUri,
            filePath = filePath,
            albumArtUri = embeddedArtUri,
            year = year,
            trackNumber = track,
            fileSize = size,
            addedAt = dateAdded
          )
          foundSongs.add(song)
        }
      }
    } catch (e: Exception) {
      // Permission or query exception
    }

    if (foundSongs.isNotEmpty()) {
      songDao.insertSongs(foundSongs)
    }

    _scanProgress.value = ScanProgress(
      totalFiles = foundSongs.size,
      scannedFiles = foundSongs.size,
      currentFileName = "Scan completed! Added ${foundSongs.size} tracks.",
      isScanning = false
    )

    foundSongs.size
  }

  suspend fun importAudioUri(uri: Uri): Song? = withContext(Dispatchers.IO) {
    try {
      val retriever = MediaMetadataRetriever()
      retriever.setDataSource(context, uri)

      val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
      val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
      val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Imported"
      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      val durationMs = durationStr?.toLongOrNull() ?: 180000L

      val fileName = uri.lastPathSegment ?: "Imported Track"
      val (cleanedTitle, cleanedArtist) = cleanMetadata(
        rawTitle ?: fileName,
        rawArtist ?: "Unknown Artist",
        fileName
      )

      var artUri: String? = null
      val pic = retriever.embeddedPicture
      if (pic != null) {
        artUri = ImageUtils.saveArtworkBytes(context, pic, "imported_${System.currentTimeMillis()}")
      }
      retriever.release()

      val song = Song(
        id = uri.toString(),
        title = cleanedTitle,
        artist = cleanedArtist,
        album = rawAlbum,
        durationMs = durationMs,
        uriString = uri.toString(),
        albumArtUri = artUri,
        addedAt = System.currentTimeMillis()
      )

      songDao.insertSong(song)
      song
    } catch (e: Exception) {
      null
    }
  }

  suspend fun autoFetchMetadataForSong(song: Song): Song = withContext(Dispatchers.IO) {
    var updatedSong = song

    // 1. Fetch Synced Lyrics via robust LyricsPipeline
    try {
      val lyricsPipeline = com.example.data.lyrics.LyricsPipeline(context, songDao)
      lyricsPipeline.fetchLyricsForSong(song)
      songDao.getSongById(song.id)?.let { updatedSong = it }
    } catch (_: Exception) {}

    // 2. Fetch High-Res Album Cover from iTunes if not present
    if (updatedSong.albumArtUri.isNullOrBlank()) {
      try {
        val query = "${updatedSong.artist} ${updatedSong.title}"
        val res = NetworkClient.iTunesService.searchSong(query)
        if (res.isSuccessful && res.body()?.results?.isNotEmpty() == true) {
          val track = res.body()!!.results.first()
          val rawArt = track.artworkUrl100
          if (!rawArt.isNullOrBlank()) {
            // Replace 100x100bb with high-res 1200x1200bb or 3000x3000bb
            val highResUrl = rawArt.replace("100x100bb", "1200x1200bb")
            val downloadedUri = downloadAndCacheImage(highResUrl, "itunes_${song.id.hashCode()}")
            if (downloadedUri != null) {
              songDao.updateAlbumArt(song.id, downloadedUri)
              updatedSong = updatedSong.copy(albumArtUri = downloadedUri)
            }
          }
        }
      } catch (_: Exception) {}
    }

    updatedSong
  }

  private suspend fun downloadAndCacheImage(imageUrl: String, idKey: String): String? = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder().url(imageUrl).build()
      val okClient = okhttp3.OkHttpClient()
      val response = okClient.newCall(request).execute()
      if (response.isSuccessful) {
        val bytes = response.body?.bytes()
        if (bytes != null && bytes.isNotEmpty()) {
          val artDir = File(context.filesDir, "album_art").apply { mkdirs() }
          val file = File(artDir, "cached_$idKey.jpg")
          FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
          }
          return@withContext Uri.fromFile(file).toString()
        }
      }
    } catch (_: Exception) {}
    null
  }

  private fun cleanMetadata(rawTitle: String, rawArtist: String, path: String?): Pair<String, String> {
    var title = rawTitle.trim()
    var artist = rawArtist.trim()

    // Clean common junk words from title
    val junkPatterns = listOf(
      Regex("\\[.*?\\]"),
      Regex("\\(.*?Official.*?\\)", RegexOption.IGNORE_CASE),
      Regex("\\(.*?Video.*?\\)", RegexOption.IGNORE_CASE),
      Regex("\\(.*?Audio.*?\\)", RegexOption.IGNORE_CASE),
      Regex("\\(.*?Lyrics.*?\\)", RegexOption.IGNORE_CASE),
      Regex("\\(320kbps\\)", RegexOption.IGNORE_CASE),
      Regex("\\.mp3$", RegexOption.IGNORE_CASE),
      Regex("\\.m4a$", RegexOption.IGNORE_CASE),
      Regex("\\.flac$", RegexOption.IGNORE_CASE),
      Regex("\\.wav$", RegexOption.IGNORE_CASE)
    )

    for (p in junkPatterns) {
      title = title.replace(p, "").trim()
    }

    // If title has "Artist - Track"
    if (title.contains(" - ") && (artist.contains("<unknown>", true) || artist.isBlank())) {
      val parts = title.split(" - ", limit = 2)
      artist = parts[0].trim()
      title = parts[1].trim()
    }

    if (artist.contains("<unknown>", true) || artist.isBlank()) {
      artist = "Unknown Artist"
    }

    return Pair(title.ifBlank { "Untitled Track" }, artist)
  }
}
