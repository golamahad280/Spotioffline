package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Song
import com.example.ui.BottomTab
import com.example.ui.MusicViewModel
import com.example.ui.components.EqualizerDialog
import com.example.ui.components.FixMatchDialog
import com.example.ui.components.LyricsDebugDialog
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
  val context = LocalContext.current
  var pendingDeletedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

  // Launcher for MediaStore createDeleteRequest / RecoverableSecurityException
  val deleteSenderLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      val ids = pendingDeletedSongs.map { it.id }
      viewModel.finishSongDeletion(ids)
      pendingDeletedSongs = emptyList()
    }
  }

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

  // Request permissions once on launch if needed & auto-scan if permission already granted
  LaunchedEffect(Unit) {
    val alreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    if (alreadyGranted) {
      viewModel.scanDeviceAudio()
    }

    val neededPermissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      neededPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
      neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      neededPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    permissionLauncher.launch(neededPermissions.toTypedArray())
  }

  // Observe Notification Permission state on lifecycle resume (Android 13+)
  val lifecycleOwner = LocalLifecycleOwner.current
  var isNotificationPermissionDenied by remember { mutableStateOf(false) }
  var isNotificationBannerDismissed by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
          ) == PackageManager.PERMISSION_GRANTED
          isNotificationPermissionDenied = !granted
        } else {
          isNotificationPermissionDenied = false
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // Collect ViewModel states
  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
  val isLyricsExpanded by viewModel.isLyricsExpanded.collectAsStateWithLifecycle()
  val isLyricsDebugOpen by viewModel.isLyricsDebugOpen.collectAsStateWithLifecycle()
  val lyricsDebugInfo by viewModel.lyricsDebugInfo.collectAsStateWithLifecycle()
  val isQueueOpen by viewModel.isQueueOpen.collectAsStateWithLifecycle()
  val isEqualizerOpen by viewModel.isEqualizerOpen.collectAsStateWithLifecycle()
  val isSleepTimerOpen by viewModel.isSleepTimerOpen.collectAsStateWithLifecycle()
  val selectedSongForFixMatch by viewModel.selectedSongForFixMatch.collectAsStateWithLifecycle()
  val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
  val activeArtist by viewModel.activeArtist.collectAsStateWithLifecycle()
  val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()

  val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
  val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
  val hiddenSongs by viewModel.hiddenSongs.collectAsStateWithLifecycle()
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
    enabled = isLyricsDebugOpen || isLyricsExpanded || isNowPlayingExpanded || isQueueOpen ||
        isEqualizerOpen || isSleepTimerOpen || selectedSongForFixMatch != null ||
        isSettingsOpen || activePlaylist != null || activeArtist != null ||
        currentTab != BottomTab.HOME
  ) {
    when {
      isLyricsDebugOpen -> viewModel.closeLyricsDebug()
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
      Column(modifier = Modifier.fillMaxSize()) {
        if (isNotificationPermissionDenied && !isNotificationBannerDismissed) {
          NotificationPermissionBanner(
            onOpenSettings = {
              try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                  putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
              } catch (e: Exception) {
                com.example.utils.CrashLogger.e("MainActivity", "Error opening notification settings", e)
              }
            },
            onDismiss = { isNotificationBannerDismissed = true }
          )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          when {
            isSettingsOpen -> {
              SettingsScreen(
                totalSongs = allSongs.size,
                hiddenSongs = hiddenSongs,
                onRestoreSong = { id -> viewModel.restoreSong(id) },
                onBack = { viewModel.closeSettings() },
                onScanClick = { viewModel.scanDeviceAudio() },
                onClearLyricsCache = { viewModel.clearLyricsCache() },
                onClearCoverCache = { viewModel.clearCoverCache() },
                onOpenEqualizer = { viewModel.openEqualizer() },
                onOpenSleepTimer = { viewModel.openSleepTimer() },
                onOpenLyricsDebug = { viewModel.openLyricsDebug() },
                availableSongs = allSongs,
                playbackManager = viewModel.getPlaybackManager()
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
                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                onRenameSong = { songId, title, artist -> viewModel.renameSong(songId, title, artist) },
                onHideSong = { song -> viewModel.hideSong(song) },
                onHideSongs = { songs -> viewModel.hideSongs(songs) },
                onDeleteSongsFromPhone = { songs ->
                  pendingDeletedSongs = songs
                  viewModel.deleteSongsFromPhone(songs) { intentSender ->
                    deleteSenderLauncher.launch(
                      IntentSenderRequest.Builder(intentSender).build()
                    )
                  }
                },
                onChangeCover = { songId, uri -> viewModel.changeCustomCover(songId, uri) },
                onRemoveCustomCover = { song -> viewModel.removeCustomCover(song) },
                onSearchCoverAgain = { song -> viewModel.searchCoverAgain(song) },
                onSearchCoverCustomName = { song, query -> viewModel.searchCoverCustomName(song, query) },
                onRetrySong = { song -> viewModel.retrySong(song) }
              )
            }
          }
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
          onNextClick = { viewModel.next() },
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
        isPlaying = isPlaying,
        onPlayPause = { viewModel.togglePlayPause() },
        onDismiss = { viewModel.closeLyrics() },
        onSeekTo = { pos -> viewModel.seekTo(pos) },
        onUpdateOffset = { offset ->
          currentSong?.let { viewModel.updateSyncOffset(it.id, offset) }
        },
        onSaveManualLyrics = { text, isSynced ->
          currentSong?.let { viewModel.updateLyrics(it.id, text, isSynced) }
        },
        onRetryFetch = { song -> viewModel.fetchLyricsForSong(song, force = true) },
        onOpenDebug = { viewModel.openLyricsDebug() },
        onGenerateLyricsWithAi = { song -> viewModel.generateLyricsWithAi(song) },
        onAutoSyncWithAi = { song, plainText -> viewModel.autoSyncLyricsWithAi(song, plainText) }
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

    // Lyrics Debug Dialog (Step 1 & Step 7)
    if (isLyricsDebugOpen) {
      LyricsDebugDialog(
        currentSong = currentSong,
        debugInfo = lyricsDebugInfo,
        onRetryFetch = { song -> viewModel.fetchLyricsForSong(song, force = true) },
        onRunSelfTest = { viewModel.runLyricsSelfTest() },
        onRunSetFireToTheRainTest = { viewModel.runSetFireToTheRainTest() },
        onDismiss = { viewModel.closeLyricsDebug() }
      )
    }
  }
}

@Composable
fun NotificationPermissionBanner(
  onOpenSettings: () -> Unit,
  onDismiss: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF2E2415))
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.NotificationsActive,
          contentDescription = null,
          tint = Color(0xFFFFB74D),
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Playback notifications are disabled. Lock screen controls won't appear.",
          color = Color(0xFFFFE0B2),
          fontSize = 12.sp,
          lineHeight = 16.sp
        )
      }
      TextButton(
        onClick = onOpenSettings,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(
          text = "Enable",
          color = com.example.ui.theme.SpotifyGreen,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(24.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Dismiss",
          tint = Color(0xFFFFB74D),
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}
