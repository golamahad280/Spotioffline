package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.SongDao
import com.example.data.model.ScanProgress
import com.example.data.model.Song
import com.example.data.network.NetworkClient
import com.example.utils.CrashLogger
import com.example.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class AudioScanner(
  private val context: Context,
  private val songDao: SongDao
) {
  private val TAG = "AudioScanner"

  private val _scanProgress = MutableStateFlow(ScanProgress())
  val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

  // Concurrency limiter: limit parallel image/network jobs to 2
  private val networkSemaphore = Semaphore(2)

  private val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac")

  suspend fun scanDeviceAudio(): Int = withContext(Dispatchers.IO) {
    _scanProgress.value = ScanProgress(isScanning = true, currentFileName = "Preparing audio scan...")
    CrashLogger.i(TAG, "Starting audio scan")

    var totalAddedOrUpdated = 0

    try {
      // 1. Walk common audio folders and trigger MediaScannerConnection for unindexed audio files
      triggerMediaScannerOnCommonFolders()

      // 2. Incremental scan: cleanup dead songs from DB
      cleanupDeadSongs()

      // 3. Query MediaStore.Audio.Media without IS_MUSIC, duration >= 15s, excluding ringtone/alarm/notification
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

      val selection = "${MediaStore.Audio.Media.DURATION} >= 15000 AND " +
          "${MediaStore.Audio.Media.IS_RINGTONE} == 0 AND " +
          "${MediaStore.Audio.Media.IS_NOTIFICATION} == 0 AND " +
          "${MediaStore.Audio.Media.IS_ALARM} == 0"
      val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

      val cursor = try {
        context.contentResolver.query(
          MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          null,
          sortOrder
        )
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Failed querying MediaStore", e)
        null
      }

      val pendingBatch = mutableListOf<Song>()
      val BATCH_SIZE = 50

      cursor?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
        val yearCol = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
        val trackCol = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val dateAddedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

        val totalCount = c.count
        var scannedCount = 0

        while (c.moveToNext()) {
          scannedCount++
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

          _scanProgress.value = ScanProgress(
            totalFiles = totalCount,
            scannedFiles = scannedCount,
            currentFileName = rawTitle,
            isScanning = true
          )

          // Check if song already exists in Room database
          val existingSong = songDao.getSongById(contentUri)

          if (existingSong != null) {
            // Respect user locks & existing metadata
            val finalTitle = if (existingSong.titleLocked) existingSong.title else {
              val (cleanedT, _) = cleanMetadata(rawTitle, rawArtist, filePath)
              cleanedT
            }
            val finalArtist = if (existingSong.artistLocked) existingSong.artist else {
              val (_, cleanedA) = cleanMetadata(rawTitle, rawArtist, filePath)
              cleanedA
            }
            val finalAlbumArt = if (existingSong.coverLocked || !existingSong.albumArtUri.isNullOrBlank()) {
              existingSong.albumArtUri
            } else {
              extractEmbeddedArt(contentUri, filePath, mediaId)
            }

            val updatedSong = existingSong.copy(
              title = finalTitle,
              artist = finalArtist,
              album = if (rawAlbum.contains("<unknown>", true)) "Single" else rawAlbum,
              durationMs = duration,
              filePath = filePath ?: existingSong.filePath,
              albumArtUri = finalAlbumArt,
              year = year,
              trackNumber = track,
              fileSize = size
            )
            pendingBatch.add(updatedSong)
          } else {
            // New Song
            val (cleanedTitle, cleanedArtist) = cleanMetadata(rawTitle, rawArtist, filePath)
            val embeddedArt = extractEmbeddedArt(contentUri, filePath, mediaId)

            val newSong = Song(
              id = contentUri,
              title = cleanedTitle,
              artist = cleanedArtist,
              album = if (rawAlbum.contains("<unknown>", true)) "Single" else rawAlbum,
              durationMs = duration,
              uriString = contentUri,
              filePath = filePath,
              albumArtUri = embeddedArt,
              year = year,
              trackNumber = track,
              fileSize = size,
              addedAt = dateAdded,
              isHidden = false,
              titleLocked = false,
              artistLocked = false,
              coverLocked = false
            )
            pendingBatch.add(newSong)
          }

          // Batch insert 50 at a time for responsive UI
          if (pendingBatch.size >= BATCH_SIZE) {
            songDao.insertSongs(pendingBatch.toList())
            totalAddedOrUpdated += pendingBatch.size
            pendingBatch.clear()
          }
        }
      }

      // Insert any remaining items in pending batch
      if (pendingBatch.isNotEmpty()) {
        songDao.insertSongs(pendingBatch.toList())
        totalAddedOrUpdated += pendingBatch.size
        pendingBatch.clear()
      }

      _scanProgress.value = ScanProgress(
        totalFiles = totalAddedOrUpdated,
        scannedFiles = totalAddedOrUpdated,
        currentFileName = "Scan completed! Found $totalAddedOrUpdated tracks.",
        isScanning = false
      )
      CrashLogger.i(TAG, "Audio scan completed successfully. Processed: $totalAddedOrUpdated tracks")
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Scan failed with exception", e)
      _scanProgress.value = ScanProgress(
        totalFiles = 0,
        scannedFiles = 0,
        currentFileName = "Scan finished with errors.",
        isScanning = false
      )
    }

    totalAddedOrUpdated
  }

  private suspend fun extractEmbeddedArt(contentUri: String, filePath: String?, mediaId: Long): String? {
    var embeddedArtUri: String? = null
    var retriever: MediaMetadataRetriever? = null
    try {
      retriever = MediaMetadataRetriever()
      if (filePath != null && File(filePath).exists()) {
        retriever.setDataSource(filePath)
      } else {
        retriever.setDataSource(context, Uri.parse(contentUri))
      }
      val embeddedPic = retriever.embeddedPicture
      if (embeddedPic != null && embeddedPic.isNotEmpty()) {
        embeddedArtUri = ImageUtils.saveArtworkBytes(context, embeddedPic, "media_$mediaId")
      }
    } catch (oom: OutOfMemoryError) {
      CrashLogger.e(TAG, "OOM extracting embedded picture for $contentUri", oom)
    } catch (e: Exception) {
      CrashLogger.w(TAG, "Audio file without embedded artwork or error reading: $contentUri", e)
    } finally {
      try {
        retriever?.release()
      } catch (e: Exception) {
        CrashLogger.w(TAG, "Error releasing MediaMetadataRetriever", e)
      }
    }
    return embeddedArtUri
  }

  private suspend fun cleanupDeadSongs() {
    try {
      val allIds = songDao.getAllSongIds()
      val deadIds = mutableListOf<String>()

      for (id in allIds) {
        val song = songDao.getSongById(id) ?: continue

        // Demo tracks synthesized internally shouldn't be deleted
        if (id.startsWith("demo_track_")) {
          continue
        }

        var fileExists = false
        if (song.filePath != null) {
          try {
            fileExists = File(song.filePath).exists()
          } catch (e: Exception) {
            CrashLogger.w(TAG, "Error checking file existence for ${song.filePath}", e)
          }
        }

        if (!fileExists) {
          // Verify if ContentResolver can still open file descriptor
          try {
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(song.uriString), "r")
            if (pfd != null) {
              fileExists = true
              pfd.close()
            }
          } catch (e: Exception) {
            CrashLogger.w(TAG, "ContentResolver could not open URI ${song.uriString}", e)
            fileExists = false
          }
        }

        if (!fileExists) {
          deadIds.add(id)
        }
      }

      if (deadIds.isNotEmpty()) {
        CrashLogger.i(TAG, "Removing ${deadIds.size} missing/deleted songs from database")
        songDao.deleteSongsByIds(deadIds)
      }
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error cleaning up dead songs", e)
    }
  }

  private fun triggerMediaScannerOnCommonFolders() {
    try {
      val dirsToScan = mutableListOf<File>()

      fun addDirIfValid(dir: File?) {
        if (dir != null && dir.exists() && dir.isDirectory) {
          dirsToScan.add(dir)
        }
      }

      addDirIfValid(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
      addDirIfValid(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
      addDirIfValid(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS))
      addDirIfValid(Environment.getExternalStoragePublicDirectory("Recordings"))
      addDirIfValid(Environment.getExternalStoragePublicDirectory("Audiobooks"))
      addDirIfValid(File(Environment.getExternalStorageDirectory(), "Bluetooth"))

      // Messenger audio folders
      addDirIfValid(File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Audio"))
      addDirIfValid(File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
      addDirIfValid(File(Environment.getExternalStorageDirectory(), "Telegram/Telegram Audio"))

      val filesToScan = mutableListOf<String>()
      for (dir in dirsToScan) {
        collectAudioFiles(dir, filesToScan, maxDepth = 4)
      }

      if (filesToScan.isNotEmpty()) {
        CrashLogger.i(TAG, "Requesting MediaScannerConnection for ${filesToScan.size} audio files on disk")
        // Batch scanFile requests in chunks of 50
        filesToScan.chunked(50).forEach { chunk ->
          MediaScannerConnection.scanFile(
            context,
            chunk.toTypedArray(),
            null,
            null
          )
        }
      }
    } catch (e: Exception) {
      CrashLogger.w(TAG, "Error walking directories for MediaScanner", e)
    }
  }

  private fun collectAudioFiles(dir: File, result: MutableList<String>, maxDepth: Int) {
    if (maxDepth <= 0 || result.size > 1000) return
    try {
      val files = dir.listFiles() ?: return
      for (file in files) {
        if (file.isDirectory && !file.name.startsWith(".")) {
          collectAudioFiles(file, result, maxDepth - 1)
        } else if (file.isFile) {
          val ext = file.extension.lowercase()
          if (supportedExtensions.contains(ext)) {
            result.add(file.absolutePath)
          }
        }
      }
    } catch (e: Exception) {
      CrashLogger.w(TAG, "Error collecting files from ${dir.absolutePath}", e)
    }
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
      CrashLogger.e(TAG, "Failed importing audio URI $uri", e)
      null
    }
  }

  val coverPipeline = com.example.data.cover.CoverPipeline(context, songDao)

  suspend fun autoFetchMetadataForSong(song: Song): Song = withContext(Dispatchers.IO) {
    var updatedSong = song

    // 1. Fetch Synced Lyrics via robust LyricsPipeline (within concurrency limit)
    try {
      networkSemaphore.withPermit {
        val lyricsPipeline = com.example.data.lyrics.LyricsPipeline(context, songDao)
        lyricsPipeline.fetchLyricsForSong(song)
        songDao.getSongById(song.id)?.let { updatedSong = it }
      }
    } catch (e: Exception) {
      CrashLogger.w(TAG, "Error in auto-fetch lyrics for ${song.title}", e)
    }

    // 2. Fetch High-Res Album Cover via CoverPipeline if not present and cover not locked
    if (updatedSong.albumArtUri.isNullOrBlank() && !updatedSong.coverLocked) {
      try {
        coverPipeline.autoFetchCoverForSong(updatedSong)
        songDao.getSongById(song.id)?.let { updatedSong = it }
      } catch (e: Exception) {
        CrashLogger.w(TAG, "Error fetching cover via CoverPipeline for ${song.title}", e)
      }
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
    } catch (oom: OutOfMemoryError) {
      CrashLogger.e(TAG, "OOM downloading image $imageUrl", oom)
    } catch (e: Exception) {
      CrashLogger.w(TAG, "Failed downloading image $imageUrl", e)
    }
    null
  }

  private fun cleanMetadata(rawTitle: String, rawArtist: String, path: String?): Pair<String, String> {
    var title = rawTitle.trim()
    var artist = rawArtist.trim()

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
