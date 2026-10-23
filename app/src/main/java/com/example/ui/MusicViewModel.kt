package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.LrcLine
import com.example.data.model.Playlist
import com.example.data.model.PlaylistSongCrossRef
import com.example.data.model.RepeatMode
import com.example.data.model.ScanProgress
import com.example.data.model.Song
import com.example.data.scanner.AudioScanner
import com.example.playback.EqualizerBand
import com.example.playback.PlaybackManager
import com.example.utils.DemoAudioGenerator
import com.example.utils.LrcParser
import android.os.Build
import android.provider.MediaStore
import com.example.utils.CrashLogger
import com.example.utils.ImageUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BottomTab {
  HOME,
  SEARCH,
  LIBRARY
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

  private val database = AppDatabase.getDatabase(application)
  private val songDao = database.songDao()
  private val playlistDao = database.playlistDao()

  private val audioScanner = AudioScanner(application, songDao)
  private val playbackManager = PlaybackManager(application, songDao, viewModelScope)
  val lyricsPipeline = com.example.data.lyrics.LyricsPipeline(application, songDao)
  val lyricsDebugInfo = lyricsPipeline.debugInfo

  // Navigation & Modal states
  private val _currentTab = MutableStateFlow(BottomTab.HOME)
  val currentTab: StateFlow<BottomTab> = _currentTab.asStateFlow()

  private val _isNowPlayingExpanded = MutableStateFlow(false)
  val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

  private val _isLyricsExpanded = MutableStateFlow(false)
  val isLyricsExpanded: StateFlow<Boolean> = _isLyricsExpanded.asStateFlow()

  private val _isLyricsDebugOpen = MutableStateFlow(false)
  val isLyricsDebugOpen: StateFlow<Boolean> = _isLyricsDebugOpen.asStateFlow()

  private val _isQueueOpen = MutableStateFlow(false)
  val isQueueOpen: StateFlow<Boolean> = _isQueueOpen.asStateFlow()

  private val _isEqualizerOpen = MutableStateFlow(false)
  val isEqualizerOpen: StateFlow<Boolean> = _isEqualizerOpen.asStateFlow()

  private val _isSleepTimerOpen = MutableStateFlow(false)
  val isSleepTimerOpen: StateFlow<Boolean> = _isSleepTimerOpen.asStateFlow()

  private val _selectedSongForFixMatch = MutableStateFlow<Song?>(null)
  val selectedSongForFixMatch: StateFlow<Song?> = _selectedSongForFixMatch.asStateFlow()

  private val _activePlaylist = MutableStateFlow<Playlist?>(null)
  val activePlaylist: StateFlow<Playlist?> = _activePlaylist.asStateFlow()

  private val _activeArtist = MutableStateFlow<String?>(null)
  val activeArtist: StateFlow<String?> = _activeArtist.asStateFlow()

  private val _isSettingsOpen = MutableStateFlow(false)
  val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

  // Room data flows
  val allSongs: StateFlow<List<Song>> = songDao.getAllSongs()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val likedSongs: StateFlow<List<Song>> = songDao.getLikedSongs()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentlyPlayed: StateFlow<List<Song>> = songDao.getRecentlyPlayed(20)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val mostPlayed: StateFlow<List<Song>> = songDao.getMostPlayed(20)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val artists: StateFlow<List<String>> = songDao.getAllArtists()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val playlists: StateFlow<List<Playlist>> = playlistDao.getAllPlaylists()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val hiddenSongs: StateFlow<List<Song>> = songDao.getHiddenSongs()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    CrashLogger.e("MusicViewModel", "Coroutine exception caught in background task", throwable)
  }

  val scanProgress: StateFlow<ScanProgress> = audioScanner.scanProgress

  // Playback flows - currentSong combines player selection with Room DB for live cache updates!
  val currentSong: StateFlow<Song?> = combine(playbackManager.currentSong, songDao.getAllSongs()) { active, all ->
    if (active == null) null
    else all.firstOrNull { it.id == active.id } ?: active
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
  val currentPositionMs: StateFlow<Long> = playbackManager.currentPositionMs
  val durationMs: StateFlow<Long> = playbackManager.durationMs
  val queue: StateFlow<List<Song>> = playbackManager.queue
  val queueIndex: StateFlow<Int> = playbackManager.queueIndex
  val shuffleEnabled: StateFlow<Boolean> = playbackManager.shuffleEnabled
  val repeatMode: StateFlow<RepeatMode> = playbackManager.repeatMode
  val sleepTimerRemaining: StateFlow<Int> = playbackManager.sleepTimerRemainingMinutes
  val equalizerBands: StateFlow<List<EqualizerBand>> = playbackManager.equalizerBands
  val currentPreset: StateFlow<String> = playbackManager.currentPreset
  val availablePresets: List<String> = playbackManager.availablePresets

  // Parsed lyrics lines for current playing song directly from DB
  val currentLyricsLines: StateFlow<List<LrcLine>> = combine(currentSong) { (song) ->
    if (song?.hasLyrics == true && !song.lyricsText.isNullOrBlank()) {
      LrcParser.parse(song.lyricsText)
    } else {
      emptyList()
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Real-time active lyric line index
  val activeLyricIndex: StateFlow<Int> = combine(
    currentLyricsLines,
    currentPositionMs,
    currentSong
  ) { lines, pos, song ->
    val offset = song?.syncOffsetMs ?: 0L
    LrcParser.findActiveLineIndex(lines, pos, offset)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

  init {
    // Check if initial database is empty; if so, generate demo tracks so user has instant playable content!
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val count = songDao.getSongCount()
      if (count == 0) {
        loadDemoTracks()
      } else {
        startBackgroundLyricsQueue()
        startBackgroundCoverQueue()
      }
    }
  }

  fun setTab(tab: BottomTab) {
    _currentTab.value = tab
    _activePlaylist.value = null
    _activeArtist.value = null
    _isSettingsOpen.value = false
  }

  fun openNowPlaying() {
    _isNowPlayingExpanded.value = true
  }

  fun closeNowPlaying() {
    _isNowPlayingExpanded.value = false
  }

  fun openLyrics() {
    _isLyricsExpanded.value = true
  }

  fun closeLyrics() {
    _isLyricsExpanded.value = false
  }

  fun openQueue() {
    _isQueueOpen.value = true
  }

  fun closeQueue() {
    _isQueueOpen.value = false
  }

  fun openEqualizer() {
    _isEqualizerOpen.value = true
  }

  fun closeEqualizer() {
    _isEqualizerOpen.value = false
  }

  fun openSleepTimer() {
    _isSleepTimerOpen.value = true
  }

  fun closeSleepTimer() {
    _isSleepTimerOpen.value = false
  }

  fun openSettings() {
    _isSettingsOpen.value = true
  }

  fun closeSettings() {
    _isSettingsOpen.value = false
  }

  fun openPlaylist(playlist: Playlist) {
    _activePlaylist.value = playlist
    _activeArtist.value = null
  }

  fun closePlaylist() {
    _activePlaylist.value = null
  }

  fun openArtist(artistName: String) {
    _activeArtist.value = artistName
    _activePlaylist.value = null
  }

  fun closeArtist() {
    _activeArtist.value = null
  }

  fun openLyricsDebug() {
    currentSong.value?.let {
      lyricsPipeline.setInspectSongId(it.id)
    }
    _isLyricsDebugOpen.value = true
  }

  fun closeLyricsDebug() {
    lyricsPipeline.activeDebugSongId = null
    _isLyricsDebugOpen.value = false
  }

  suspend fun runLyricsSelfTest(): Map<String, Boolean> {
    return lyricsPipeline.runSelfTest()
  }

  suspend fun runSetFireToTheRainTest(): Pair<Boolean, com.example.data.lyrics.LyricsDebugInfo> {
    return lyricsPipeline.runSetFireToTheRainTest()
  }

  fun fetchLyricsForSong(song: Song, force: Boolean = true) {
    viewModelScope.launch(Dispatchers.IO) {
      lyricsPipeline.setInspectSongId(song.id)
      val success = lyricsPipeline.fetchLyricsForSong(song, force = force)
      if (success) {
        playbackManager.refreshCurrentSongFromDb()
      }
    }
  }

  // Playback Controls
  fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
    playbackManager.playSong(song, contextQueue)
    viewModelScope.launch(Dispatchers.IO) {
      if (!song.hasLyrics || song.lyricsText.isNullOrBlank()) {
        val success = lyricsPipeline.fetchLyricsForSong(song, force = false)
        if (success) {
          playbackManager.refreshCurrentSongFromDb()
        }
      }
      if (song.albumArtUri == null) {
        audioScanner.autoFetchMetadataForSong(song)
      }
    }
  }

  fun togglePlayPause() = playbackManager.togglePlayPause()
  fun next() = playbackManager.next()
  fun previous() = playbackManager.previous()
  fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)
  fun toggleShuffle() = playbackManager.toggleShuffle()
  fun toggleRepeat() = playbackManager.toggleRepeat()
  fun addToQueue(song: Song) = playbackManager.addToQueue(song)
  fun playNext(song: Song) = playbackManager.playNext(song)
  fun removeFromQueue(index: Int) = playbackManager.removeFromQueue(index)

  fun setSleepTimer(minutes: Int) {
    playbackManager.setSleepTimer(minutes)
    closeSleepTimer()
  }

  fun setEqualizerBandLevel(bandIndex: Short, level: Short) {
    playbackManager.setEqualizerBandLevel(bandIndex, level)
  }

  fun setEqualizerPreset(preset: String) {
    playbackManager.setEqualizerPreset(preset)
  }

  fun toggleLike(song: Song) {
    viewModelScope.launch(Dispatchers.IO) {
      val newLiked = !song.isLiked
      songDao.updateLiked(song.id, newLiked)
      if (currentSong.value?.id == song.id) {
        // Will be reflected automatically via Room flow
      }
    }
  }

  fun updateSyncOffset(songId: String, offsetMs: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      songDao.updateSyncOffset(songId, offsetMs)
    }
  }

  fun updateLyrics(songId: String, text: String, isSynced: Boolean, source: String? = null) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val effectiveSource = source ?: if (isSynced) "Manual LRC" else "Manual Plain"
      songDao.updateLyricsSuccess(
        songId = songId,
        lyrics = text,
        isSynced = isSynced,
        source = effectiveSource,
        fetchedAt = System.currentTimeMillis()
      )
      playbackManager.refreshCurrentSongFromDb()
      CrashLogger.i("MusicViewModel", "Updated manual lyrics for song $songId (synced=$isSynced, source=$effectiveSource)")
    }
  }

  suspend fun generateLyricsWithAi(song: Song): Boolean {
    val success = lyricsPipeline.generateLyricsWithGemini(song)
    if (success) {
      playbackManager.refreshCurrentSongFromDb()
    }
    return success
  }

  suspend fun autoSyncLyricsWithAi(song: Song, plainLyrics: String): String? {
    val synced = lyricsPipeline.autoSyncLyricsWithGemini(song, plainLyrics)
    if (synced != null) {
      playbackManager.refreshCurrentSongFromDb()
    }
    return synced
  }

  fun searchCoverAgain(song: Song) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      audioScanner.coverPipeline.autoFetchCoverForSong(song, force = true)
      playbackManager.refreshCurrentSongFromDb()
    }
  }

  fun searchCoverCustomName(song: Song, query: String) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      audioScanner.coverPipeline.searchCoverWithCustomQuery(song, query)
      playbackManager.refreshCurrentSongFromDb()
    }
  }

  fun openFixMatch(song: Song) {
    _selectedSongForFixMatch.value = song
  }

  fun closeFixMatch() {
    _selectedSongForFixMatch.value = null
  }

  fun fixMatch(songId: String, title: String, artist: String, album: String) {
    viewModelScope.launch(Dispatchers.IO) {
      songDao.updateMetadata(songId, title, artist, album)
      val updated = songDao.getSongById(songId)
      if (updated != null) {
        audioScanner.autoFetchMetadataForSong(updated)
        lyricsPipeline.fetchLyricsForSong(updated, force = true)
        playbackManager.refreshCurrentSongFromDb()
      }
      closeFixMatch()
    }
  }

  // Scanning & Importing
  fun scanDeviceAudio() {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      audioScanner.scanDeviceAudio()
      startBackgroundLyricsQueue()
      startBackgroundCoverQueue()
    }
  }

  fun startBackgroundLyricsQueue() {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val songsNeeding = songDao.getSongsNeedingLyrics()
      for (s in songsNeeding) {
        lyricsPipeline.fetchLyricsForSong(s, force = false)
      }
    }
  }

  fun startBackgroundCoverQueue() {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val songsNeeding = songDao.getSongsNeedingCover()
      for (s in songsNeeding) {
        audioScanner.coverPipeline.autoFetchCoverForSong(s, force = false)
      }
    }
  }

  fun importAudioUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val imported = audioScanner.importAudioUri(uri)
      if (imported != null) {
        audioScanner.autoFetchMetadataForSong(imported)
      }
    }
  }

  // Song Management (Rename, Hide, Restore, Delete, Custom Cover)
  fun hideSong(song: Song) {
    hideSongs(listOf(song))
  }

  fun hideSongs(songs: List<Song>) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val ids = songs.map { it.id }
      handleQueueAndPlaybackForRemovedSongs(ids.toSet())
      songDao.setHiddenBatch(ids, true)
      CrashLogger.i("MusicViewModel", "Marked ${ids.size} songs as hidden")
    }
  }

  fun restoreSong(songId: String) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      songDao.setHidden(songId, false)
      CrashLogger.i("MusicViewModel", "Restored song $songId from hidden")
    }
  }

  fun renameSong(songId: String, newTitle: String, newArtist: String) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      songDao.updateSongMetadataLocked(
        songId = songId,
        title = newTitle.trim(),
        artist = newArtist.trim(),
        titleLocked = true,
        artistLocked = true
      )
      playbackManager.refreshCurrentSongFromDb()
      CrashLogger.i("MusicViewModel", "Renamed song $songId to $newTitle by $newArtist")
    }
  }

  fun changeCustomCover(songId: String, imageUri: Uri) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val savedPath = ImageUtils.saveCustomArtwork(getApplication(), imageUri, songId)
      if (savedPath != null) {
        songDao.updateCustomCoverLocked(songId, savedPath, coverLocked = true)
        playbackManager.refreshCurrentSongFromDb()
        CrashLogger.i("MusicViewModel", "Updated custom cover for song $songId: $savedPath")
      }
    }
  }

  fun removeCustomCover(song: Song) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      songDao.updateCustomCoverLocked(song.id, null, coverLocked = false)
      val updated = songDao.getSongById(song.id)
      if (updated != null) {
        audioScanner.autoFetchMetadataForSong(updated)
      }
      playbackManager.refreshCurrentSongFromDb()
      CrashLogger.i("MusicViewModel", "Removed custom cover for song ${song.id}")
    }
  }

  fun deleteSongsFromPhone(
    songs: List<Song>,
    onIntentSenderRequired: (android.content.IntentSender) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val uris = songs.mapNotNull {
        try { Uri.parse(it.uriString) } catch (_: Exception) { null }
      }

      // Android 11+ (API 30+) MediaStore.createDeleteRequest
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
        try {
          val pi = MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, uris)
          onIntentSenderRequired(pi.intentSender)
          return@launch
        } catch (e: Exception) {
          CrashLogger.e("MusicViewModel", "Error creating delete request", e)
        }
      }

      // Android 10 RecoverableSecurityException or older direct delete
      val deletedIds = mutableListOf<String>()
      for (song in songs) {
        var deleted = false
        val uri = try { Uri.parse(song.uriString) } catch (_: Exception) { null }
        if (uri != null) {
          try {
            val count = getApplication<Application>().contentResolver.delete(uri, null, null)
            if (count > 0) deleted = true
          } catch (sec: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is android.app.RecoverableSecurityException) {
              onIntentSenderRequired(sec.userAction.actionIntent.intentSender)
              return@launch
            }
          } catch (e: Exception) {
            CrashLogger.e("MusicViewModel", "Failed deleting URI ${song.uriString}", e)
          }
        }
        if (!deleted && song.filePath != null) {
          try {
            val file = java.io.File(song.filePath)
            if (file.exists()) {
              deleted = file.delete()
            }
          } catch (e: Exception) {
            CrashLogger.e("MusicViewModel", "Failed deleting file ${song.filePath}", e)
          }
        }
        if (deleted) {
          deletedIds.add(song.id)
        }
      }

      if (deletedIds.isNotEmpty()) {
        finishSongDeletion(deletedIds)
      }
    }
  }

  fun finishSongDeletion(deletedIds: List<String>) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      handleQueueAndPlaybackForRemovedSongs(deletedIds.toSet())
      songDao.deleteSongsByIds(deletedIds)
      CrashLogger.i("MusicViewModel", "Finished deletion of ${deletedIds.size} songs from DB")
    }
  }

  private fun handleQueueAndPlaybackForRemovedSongs(affectedIds: Set<String>) {
    val activeSong = currentSong.value
    if (activeSong != null && affectedIds.contains(activeSong.id)) {
      if (queue.value.size > 1) {
        playbackManager.next()
      } else {
        if (isPlaying.value) playbackManager.togglePlayPause()
      }
    }
    val currentQ = queue.value
    for (i in currentQ.indices.reversed()) {
      if (affectedIds.contains(currentQ[i].id)) {
        playbackManager.removeFromQueue(i)
      }
    }
  }

  fun loadDemoTracks() {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val demoSongs = DemoAudioGenerator.generateDemoTracks(getApplication())
      songDao.insertSongs(demoSongs)
    }
  }

  // Playlist management
  fun createPlaylist(name: String, description: String? = null) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      playlistDao.insertPlaylist(Playlist(name = name, description = description))
    }
  }

  fun deletePlaylist(playlistId: Long) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      playlistDao.deletePlaylist(playlistId)
      if (_activePlaylist.value?.id == playlistId) {
        _activePlaylist.value = null
      }
    }
  }

  fun addSongToPlaylist(playlistId: Long, songId: String) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      val count = playlistDao.getSongCountInPlaylist(playlistId)
      playlistDao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, songId, count))
    }
  }

  fun removeSongFromPlaylist(playlistId: Long, songId: String) {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
  }

  fun getSongsInPlaylist(playlistId: Long) = playlistDao.getSongsInPlaylist(playlistId)
  fun getSongsByArtist(artist: String) = songDao.getSongsByArtist(artist)

  // Cache management
  fun clearLyricsCache() {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      songDao.clearAllLyrics()
    }
  }

  fun clearCoverCache() {
    viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
      songDao.clearAllAlbumArt()
    }
  }

  override fun onCleared() {
    super.onCleared()
    playbackManager.release()
  }
}
