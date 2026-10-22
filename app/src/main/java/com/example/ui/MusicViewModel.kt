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

  // Navigation & Modal states
  private val _currentTab = MutableStateFlow(BottomTab.HOME)
  val currentTab: StateFlow<BottomTab> = _currentTab.asStateFlow()

  private val _isNowPlayingExpanded = MutableStateFlow(false)
  val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

  private val _isLyricsExpanded = MutableStateFlow(false)
  val isLyricsExpanded: StateFlow<Boolean> = _isLyricsExpanded.asStateFlow()

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

  val scanProgress: StateFlow<ScanProgress> = audioScanner.scanProgress

  // Playback flows forwarded from PlaybackManager
  val currentSong: StateFlow<Song?> = playbackManager.currentSong
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

  // Parsed lyrics lines for current playing song
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
    viewModelScope.launch(Dispatchers.IO) {
      val count = songDao.getSongCount()
      if (count == 0) {
        loadDemoTracks()
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

  fun openFixMatch(song: Song) {
    _selectedSongForFixMatch.value = song
  }

  fun closeFixMatch() {
    _selectedSongForFixMatch.value = null
  }

  // Playback Controls
  fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
    playbackManager.playSong(song, contextQueue)
    // Trigger background online enhancement if missing lyrics/cover
    if (!song.hasLyrics || song.albumArtUri == null) {
      viewModelScope.launch(Dispatchers.IO) {
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

  fun updateLyrics(songId: String, text: String, isSynced: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      songDao.updateLyrics(songId, text, isSynced)
    }
  }

  fun fixMatch(songId: String, title: String, artist: String, album: String) {
    viewModelScope.launch(Dispatchers.IO) {
      songDao.updateMetadata(songId, title, artist, album)
      val updated = songDao.getSongById(songId)
      if (updated != null) {
        audioScanner.autoFetchMetadataForSong(updated)
      }
      closeFixMatch()
    }
  }

  // Scanning & Importing
  fun scanDeviceAudio() {
    viewModelScope.launch(Dispatchers.IO) {
      audioScanner.scanDeviceAudio()
    }
  }

  fun importAudioUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      val imported = audioScanner.importAudioUri(uri)
      if (imported != null) {
        audioScanner.autoFetchMetadataForSong(imported)
      }
    }
  }

  fun loadDemoTracks() {
    viewModelScope.launch(Dispatchers.IO) {
      val demoSongs = DemoAudioGenerator.generateDemoTracks(getApplication())
      songDao.insertSongs(demoSongs)
    }
  }

  // Playlist management
  fun createPlaylist(name: String, description: String? = null) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistDao.insertPlaylist(Playlist(name = name, description = description))
    }
  }

  fun deletePlaylist(playlistId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistDao.deletePlaylist(playlistId)
      if (_activePlaylist.value?.id == playlistId) {
        _activePlaylist.value = null
      }
    }
  }

  fun addSongToPlaylist(playlistId: Long, songId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val count = playlistDao.getSongCountInPlaylist(playlistId)
      playlistDao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, songId, count))
    }
  }

  fun removeSongFromPlaylist(playlistId: Long, songId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
  }

  fun getSongsInPlaylist(playlistId: Long) = playlistDao.getSongsInPlaylist(playlistId)
  fun getSongsByArtist(artist: String) = songDao.getSongsByArtist(artist)

  // Cache management
  fun clearLyricsCache() {
    viewModelScope.launch(Dispatchers.IO) {
      songDao.clearAllLyrics()
    }
  }

  fun clearCoverCache() {
    viewModelScope.launch(Dispatchers.IO) {
      songDao.clearAllAlbumArt()
    }
  }

  override fun onCleared() {
    super.onCleared()
    playbackManager.release()
  }
}
