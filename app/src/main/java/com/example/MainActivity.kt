package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Song
import com.example.ui.BottomTab
import com.example.ui.MusicViewModel
import com.example.ui.components.EqualizerDialog
import com.example.ui.components.FixMatchDialog
import com.example.ui.components.LyricsScreen
import com.example.ui.components.MiniPlayer
import com.example.ui.components.NowPlayingModal
import com.example.ui.components.QueueSheet
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.SpotifyBottomNav
import com.example.ui.screens.ArtistDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SpotifyBlack

class MainActivity : ComponentActivity() {

  private val viewModel: MusicViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      MyApplicationTheme {
        MainAppContent(viewModel = viewModel)
      }
    }
  }
}

@Composable
fun MainAppContent(viewModel: MusicViewModel) {
  // Permission Launcher
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
        permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
    if (audioGranted) {
      viewModel.scanDeviceAudio()
    }
  }

  // File Picker Launcher for SAF audio import
  val filePickerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    uris.forEach { uri ->
      viewModel.importAudioUri(uri)
    }
  }

  // Request permissions once on launch if needed
  LaunchedEffect(Unit) {
    val neededPermissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      neededPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
      neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      neededPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    permissionLauncher.launch(neededPermissions.toTypedArray())
  }

  // Collect ViewModel states
  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
  val isLyricsExpanded by viewModel.isLyricsExpanded.collectAsStateWithLifecycle()
  val isQueueOpen by viewModel.isQueueOpen.collectAsStateWithLifecycle()
  val isEqualizerOpen by viewModel.isEqualizerOpen.collectAsStateWithLifecycle()
  val isSleepTimerOpen by viewModel.isSleepTimerOpen.collectAsStateWithLifecycle()
  val selectedSongForFixMatch by viewModel.selectedSongForFixMatch.collectAsStateWithLifecycle()
  val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
  val activeArtist by viewModel.activeArtist.collectAsStateWithLifecycle()
  val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()

  val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
  val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
  val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
  val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
  val artists by viewModel.artists.collectAsStateWithLifecycle()
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()

  val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
  val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
  val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
  val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
  val queue by viewModel.queue.collectAsStateWithLifecycle()
  val queueIndex by viewModel.queueIndex.collectAsStateWithLifecycle()
  val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
  val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
  val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
  val equalizerBands by viewModel.equalizerBands.collectAsStateWithLifecycle()
  val currentPreset by viewModel.currentPreset.collectAsStateWithLifecycle()

  val currentLyricsLines by viewModel.currentLyricsLines.collectAsStateWithLifecycle()
  val activeLyricIndex by viewModel.activeLyricIndex.collectAsStateWithLifecycle()

  // Back Navigation Handler
  BackHandler(
    enabled = isLyricsExpanded || isNowPlayingExpanded || isQueueOpen ||
        isEqualizerOpen || isSleepTimerOpen || selectedSongForFixMatch != null ||
        isSettingsOpen || activePlaylist != null || activeArtist != null ||
        currentTab != BottomTab.HOME
  ) {
    when {
      isLyricsExpanded -> viewModel.closeLyrics()
      isNowPlayingExpanded -> viewModel.closeNowPlaying()
      isQueueOpen -> viewModel.closeQueue()
      isEqualizerOpen -> viewModel.closeEqualizer()
      isSleepTimerOpen -> viewModel.closeSleepTimer()
      selectedSongForFixMatch != null -> viewModel.closeFixMatch()
      isSettingsOpen -> viewModel.closeSettings()
      activePlaylist != null -> viewModel.closePlaylist()
      activeArtist != null -> viewModel.closeArtist()
      currentTab != BottomTab.HOME -> viewModel.setTab(BottomTab.HOME)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SpotifyBlack)
  ) {
    // Primary View Layer with status bar insets
    Box(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
    ) {
      when {
        isSettingsOpen -> {
          SettingsScreen(
            totalSongs = allSongs.size,
            onBack = { viewModel.closeSettings() },
            onScanClick = { viewModel.scanDeviceAudio() },
            onClearLyricsCache = { viewModel.clearLyricsCache() },
            onClearCoverCache = { viewModel.clearCoverCache() },
            onOpenEqualizer = { viewModel.openEqualizer() },
            onOpenSleepTimer = { viewModel.openSleepTimer() }
          )
        }

        activePlaylist != null -> {
          val playlistSongs by viewModel.getSongsInPlaylist(activePlaylist!!.id).collectAsStateWithLifecycle(emptyList())
          PlaylistDetailScreen(
            playlistName = activePlaylist!!.name,
            songs = playlistSongs,
            isLikedSongs = false,
            onBack = { viewModel.closePlaylist() },
            onSongClick = { song, list -> viewModel.playSong(song, list) },
            onDeletePlaylist = { viewModel.deletePlaylist(activePlaylist!!.id) }
          )
        }

        activeArtist != null -> {
          val artistSongs by viewModel.getSongsByArtist(activeArtist!!).collectAsStateWithLifecycle(emptyList())
          ArtistDetailScreen(
            artistName = activeArtist!!,
            songs = artistSongs,
            onBack = { viewModel.closeArtist() },
            onSongClick = { song, list -> viewModel.playSong(song, list) }
          )
        }

        else -> {
          when (currentTab) {
            BottomTab.HOME -> {
              HomeScreen(
                allSongs = allSongs,
                likedSongs = likedSongs,
                recentlyPlayed = recentlyPlayed,
                mostPlayed = mostPlayed,
                artists = artists,
                scanProgress = scanProgress,
                onSongClick = { song, list -> viewModel.playSong(song, list) },
                onArtistClick = { artist -> viewModel.openArtist(artist) },
                onLikedClick = { viewModel.setTab(BottomTab.LIBRARY) },
                onScanClick = { viewModel.scanDeviceAudio() },
                onLoadDemoClick = { viewModel.loadDemoTracks() },
                onOpenSettings = { viewModel.openSettings() }
              )
            }

            BottomTab.SEARCH -> {
              SearchScreen(
                allSongs = allSongs,
                onSongClick = { song: Song, list: List<Song> -> viewModel.playSong(song, list) }
              )
            }

            BottomTab.LIBRARY -> {
              LibraryScreen(
                allSongs = allSongs,
                likedSongs = likedSongs,
                playlists = playlists,
                artists = artists,
                onSongClick = { song: Song, list: List<Song> -> viewModel.playSong(song, list) },
                onPlaylistClick = { pl -> viewModel.openPlaylist(pl) },
                onArtistClick = { artist -> viewModel.openArtist(artist) },
                onLikedClick = {
                  // Open liked songs screen or filter
                  viewModel.openPlaylist(
                    com.example.data.model.Playlist(id = -1, name = "Liked Songs")
                  )
                },
                onScanClick = { viewModel.scanDeviceAudio() },
                onPickAudioFiles = { filePickerLauncher.launch(arrayOf("audio/*")) },
                onCreatePlaylist = { name -> viewModel.createPlaylist(name) }
              )
            }
          }
        }
      }
    }

    // Persistent Floating Bottom Section (MiniPlayer + BottomNav)
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
    ) {
      AnimatedVisibility(
        visible = currentSong != null && !isNowPlayingExpanded && !isLyricsExpanded,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
      ) {
        MiniPlayer(
          song = currentSong,
          isPlaying = isPlaying,
          currentPositionMs = currentPositionMs,
          durationMs = durationMs,
          onPlayPauseClick = { viewModel.togglePlayPause() },
          onLikeClick = { currentSong?.let { viewModel.toggleLike(it) } },
          onClick = { viewModel.openNowPlaying() }
        )
      }

      SpotifyBottomNav(
        currentTab = currentTab,
        onTabSelected = { tab -> viewModel.setTab(tab) }
      )
    }

    // Full-Screen Overlays
    AnimatedVisibility(
      visible = isNowPlayingExpanded && !isLyricsExpanded,
      enter = slideInVertically { it },
      exit = slideOutVertically { it }
    ) {
      NowPlayingModal(
        song = currentSong,
        isPlaying = isPlaying,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        lyricsLines = currentLyricsLines,
        activeLyricIndex = activeLyricIndex,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        sleepTimerRemaining = sleepTimerRemaining,
        onDismiss = { viewModel.closeNowPlaying() },
        onPlayPause = { viewModel.togglePlayPause() },
        onNext = { viewModel.next() },
        onPrevious = { viewModel.previous() },
        onSeek = { pos -> viewModel.seekTo(pos) },
        onToggleShuffle = { viewModel.toggleShuffle() },
        onToggleRepeat = { viewModel.toggleRepeat() },
        onToggleLike = { song -> viewModel.toggleLike(song) },
        onOpenLyrics = { viewModel.openLyrics() },
        onOpenQueue = { viewModel.openQueue() },
        onOpenEqualizer = { viewModel.openEqualizer() },
        onOpenSleepTimer = { viewModel.openSleepTimer() },
        onOpenFixMatch = { song -> viewModel.openFixMatch(song) }
      )
    }

    AnimatedVisibility(
      visible = isLyricsExpanded,
      enter = slideInVertically { it },
      exit = slideOutVertically { it }
    ) {
      LyricsScreen(
        song = currentSong,
        lyricsLines = currentLyricsLines,
        activeLyricIndex = activeLyricIndex,
        currentPositionMs = currentPositionMs,
        onDismiss = { viewModel.closeLyrics() },
        onSeekTo = { pos -> viewModel.seekTo(pos) },
        onUpdateOffset = { offset ->
          currentSong?.let { viewModel.updateSyncOffset(it.id, offset) }
        },
        onSaveManualLyrics = { text, isSynced ->
          currentSong?.let { viewModel.updateLyrics(it.id, text, isSynced) }
        }
      )
    }

    // Queue Sheet Modal
    if (isQueueOpen) {
      QueueSheet(
        queue = queue,
        queueIndex = queueIndex,
        currentSong = currentSong,
        onSelectTrack = { idx ->
          viewModel.seekTo(0)
          val track = queue.getOrNull(idx)
          if (track != null) viewModel.playSong(track, queue)
        },
        onRemoveTrack = { idx -> viewModel.removeFromQueue(idx) },
        onDismiss = { viewModel.closeQueue() }
      )
    }

    // Equalizer Dialog
    if (isEqualizerOpen) {
      EqualizerDialog(
        bands = equalizerBands,
        currentPreset = currentPreset,
        availablePresets = viewModel.availablePresets,
        onBandChange = { band, level -> viewModel.setEqualizerBandLevel(band, level) },
        onPresetChange = { preset -> viewModel.setEqualizerPreset(preset) },
        onDismiss = { viewModel.closeEqualizer() }
      )
    }

    // Sleep Timer Dialog
    if (isSleepTimerOpen) {
      SleepTimerDialog(
        currentMinutesRemaining = sleepTimerRemaining,
        onSelectMinutes = { mins -> viewModel.setSleepTimer(mins) },
        onDismiss = { viewModel.closeSleepTimer() }
      )
    }

    // Fix Match Dialog
    if (selectedSongForFixMatch != null) {
      FixMatchDialog(
        song = selectedSongForFixMatch,
        onConfirm = { id, title, artist, album ->
          viewModel.fixMatch(id, title, artist, album)
        },
        onDismiss = { viewModel.closeFixMatch() }
      )
    }
  }
}
