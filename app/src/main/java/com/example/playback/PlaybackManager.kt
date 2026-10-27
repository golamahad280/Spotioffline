package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.local.SongDao
import com.example.data.model.RepeatMode
import com.example.data.model.Song
import com.example.utils.CrashLogger
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EqualizerBand(
  val bandIndex: Short,
  val centerFreqHz: Int,
  val minLevelMilliBels: Short,
  val maxLevelMilliBels: Short,
  val currentLevelMilliBels: Short
)

class PlaybackManager(
  private val context: Context,
  private val songDao: SongDao,
  private val scope: CoroutineScope
) {

  private val TAG = "PlaybackManager"
  private val prefs = context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
  private var originalQueue: List<Song> = emptyList()

  private var equalizer: Equalizer? = null
  private var positionUpdateJob: Job? = null
  private var sleepTimer: CountDownTimer? = null
  private var playerListenerAttached = false

  private var mediaControllerFuture: ListenableFuture<MediaController>? = null
  private var mediaController: MediaController? = null
  private val pendingCommands = mutableListOf<(ExoPlayer) -> Unit>()

  @Volatile
  private var isRebuildingQueue = false
  private var consecutiveErrors = 0
  private var playedCurrentSongTimeMs = 0L

  private val _currentSong = MutableStateFlow<Song?>(null)
  val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _currentPositionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

  private val _queue = MutableStateFlow<List<Song>>(emptyList())
  val queue: StateFlow<List<Song>> = _queue.asStateFlow()

  private val _queueIndex = MutableStateFlow(0)
  val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

  private val _shuffleEnabled = MutableStateFlow(false)
  val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

  private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  private val _playbackSpeed = MutableStateFlow(1.0f)
  val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

  private val _sleepTimerRemainingMinutes = MutableStateFlow(0)
  val sleepTimerRemainingMinutes: StateFlow<Int> = _sleepTimerRemainingMinutes.asStateFlow()

  private val _equalizerBands = MutableStateFlow<List<EqualizerBand>>(emptyList())
  val equalizerBands: StateFlow<List<EqualizerBand>> = _equalizerBands.asStateFlow()

  private val _currentPreset = MutableStateFlow("Flat")
  val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

  val availablePresets = listOf("Flat", "Bass Boost", "Vocal Boost", "Rock", "Pop", "Electronic", "Jazz")

  init {
    val savedShuffle = prefs.getBoolean("shuffle_enabled", false)
    _shuffleEnabled.value = savedShuffle

    val savedRepeatStr = prefs.getString("repeat_mode", RepeatMode.OFF.name)
    val savedRepeat = try {
      RepeatMode.valueOf(savedRepeatStr ?: RepeatMode.OFF.name)
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error parsing saved repeat mode", e)
      RepeatMode.OFF
    }
    _repeatMode.value = savedRepeat

    // Bind MediaController once to keep MusicPlaybackService alive and let Media3 manage foreground
    try {
      val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
      val future = MediaController.Builder(context, sessionToken).buildAsync()
      mediaControllerFuture = future
      future.addListener({
        try {
          mediaController = future.get()
          CrashLogger.i(TAG, "MediaController successfully connected to MusicPlaybackService")
        } catch (e: Exception) {
          CrashLogger.e(TAG, "Failed to connect MediaController", e)
        }
      }, ContextCompat.getMainExecutor(context))
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error initiating MediaController binding", e)
    }

    // Observe playerFlow asynchronously
    scope.launch(Dispatchers.Main) {
      MusicPlaybackService.playerFlow.collectLatest { player ->
        if (player != null) {
          attachPlayerListener(player)
          drainPendingCommands(player)
        }
      }
    }

    startPositionTicker()
  }

  private fun drainPendingCommands(player: ExoPlayer) {
    val commands = synchronized(pendingCommands) {
      val list = pendingCommands.toList()
      pendingCommands.clear()
      list
    }
    commands.forEach { command ->
      try {
        command(player)
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Error executing pending player command", e)
      }
    }
  }

  fun runWithPlayer(command: (ExoPlayer) -> Unit) {
    val player = getActivePlayer()
    if (player != null) {
      command(player)
    } else {
      synchronized(pendingCommands) {
        pendingCommands.add(command)
      }
    }
  }

  private fun showToast(message: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
  }

  private fun filterPlayableSongs(songs: List<Song>): List<Song> {
    val minSec = prefs.getInt("min_audio_duration_sec", 30)
    val minMs = if (minSec > 0) minSec * 1000L else 0L
    return songs.filter { song ->
      song.playbackError == 0 && (minMs <= 0L || song.durationMs >= minMs)
    }
  }

  fun getActivePlayer(): ExoPlayer? {
    val player = MusicPlaybackService.getInstance()?.player ?: MusicPlaybackService.playerFlow.value
    if (player != null) {
      attachPlayerListener(player)
    }
    return player
  }

  private fun attachPlayerListener(player: ExoPlayer) {
    applyRepeatModeToPlayer(player, _repeatMode.value)

    if (!playerListenerAttached) {
      player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          _isPlaying.value = isPlaying
          if (!isPlaying) {
            playedCurrentSongTimeMs = 0L
          }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_ENDED) {
            handleTrackCompletion()
          }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
          val mode = when (repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
          }
          if (_repeatMode.value != mode) {
            _repeatMode.value = mode
            prefs.edit().putString("repeat_mode", mode.name).apply()
          }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
          if (isRebuildingQueue) return
          try {
            val mediaId = mediaItem?.mediaId ?: getActivePlayer()?.currentMediaItem?.mediaId ?: return
            val list = _queue.value
            val matchIdx = list.indexOfFirst { it.id == mediaId }
            if (matchIdx >= 0) {
              _queueIndex.value = matchIdx
              _currentSong.value = list[matchIdx]
            } else {
              val currentIdx = getActivePlayer()?.currentMediaItemIndex ?: 0
              if (currentIdx in list.indices) {
                _queueIndex.value = currentIdx
                _currentSong.value = list[currentIdx]
              }
            }
            playedCurrentSongTimeMs = 0L
          } catch (e: Exception) {
            CrashLogger.e(TAG, "Error in onMediaItemTransition", e)
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          CrashLogger.e(TAG, "ExoPlayer error occurred: ${error.message}", error)
          consecutiveErrors++

          val failedMediaId = player.currentMediaItem?.mediaId
          val failedSong = _queue.value.firstOrNull { it.id == failedMediaId } ?: _currentSong.value

          scope.launch(Dispatchers.IO) {
            if (failedSong != null) {
              songDao.updatePlaybackError(failedSong.id, 1, System.currentTimeMillis())
            }
          }

          val songTitle = failedSong?.title ?: "track"
          showToast("Skipped $songTitle: can't be played")

          if (consecutiveErrors >= 5) {
            showToast("Several files can't be played")
            consecutiveErrors = 0
            try {
              player.stop()
            } catch (e: Exception) {
              CrashLogger.e(TAG, "Error stopping player after max consecutive errors", e)
            }
            _isPlaying.value = false
          } else {
            try {
              if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
              } else if (player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekToDefaultPosition(0)
                player.prepare()
                player.play()
              } else {
                player.stop()
                _isPlaying.value = false
              }
            } catch (e: Exception) {
              CrashLogger.e(TAG, "Error skipping bad track in onPlayerError", e)
              _isPlaying.value = false
            }
          }
        }
      })
      playerListenerAttached = true
    }
  }

  private fun applyRepeatModeToPlayer(player: ExoPlayer, mode: RepeatMode) {
    player.repeatMode = when (mode) {
      RepeatMode.OFF -> Player.REPEAT_MODE_OFF
      RepeatMode.ALL -> Player.REPEAT_MODE_ALL
      RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }
  }

  fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
    val rawList = if (contextQueue.isNotEmpty()) contextQueue else listOf(song)
    val fullQueue = filterPlayableSongs(rawList).ifEmpty { listOf(song) }
    originalQueue = fullQueue

    if (_shuffleEnabled.value) {
      val shuffledQueue = SmartShuffle.order(fullQueue, song)
      _queue.value = shuffledQueue
      _queueIndex.value = 0
    } else {
      val index = fullQueue.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }
      _queue.value = fullQueue
      _queueIndex.value = index
    }
    playCurrentQueueItem()
  }

  fun setQueue(songs: List<Song>, startIndex: Int = 0) {
    val playable = filterPlayableSongs(songs)
    if (playable.isEmpty()) return
    originalQueue = playable

    if (_shuffleEnabled.value) {
      val selectedSong = playable.getOrNull(startIndex) ?: playable.first()
      val shuffledQueue = SmartShuffle.order(playable, selectedSong)
      _queue.value = shuffledQueue
      _queueIndex.value = 0
    } else {
      _queue.value = playable
      _queueIndex.value = startIndex.coerceIn(0, playable.size - 1)
    }
    playCurrentQueueItem()
  }

  fun playShuffle(songs: List<Song>) {
    val playable = filterPlayableSongs(songs)
    if (playable.isEmpty()) return
    originalQueue = playable
    _shuffleEnabled.value = true
    prefs.edit().putBoolean("shuffle_enabled", true).apply()

    val startingSong = playable.random()
    val shuffledQueue = SmartShuffle.order(playable, startingSong)
    _queue.value = shuffledQueue
    _queueIndex.value = 0
    playCurrentQueueItem()
  }

  private fun createMediaItem(s: Song): MediaItem {
    val displayArtist = if (s.artist.isBlank() || s.artist.equals("Unknown Artist", ignoreCase = true) || s.artist.equals("<unknown>", ignoreCase = true)) {
      "SpotiOffline"
    } else {
      s.artist
    }

    val mediaMetadata = MediaMetadata.Builder()
      .setTitle(s.title)
      .setArtist(displayArtist)
      .setAlbumTitle(s.album)
      .setArtworkUri(if (!s.albumArtUri.isNullOrBlank()) Uri.parse(s.albumArtUri) else null)
      .build()

    return MediaItem.Builder()
      .setUri(Uri.parse(s.uriString))
      .setMediaId(s.id)
      .setMediaMetadata(mediaMetadata)
      .build()
  }

  private fun playCurrentQueueItem() {
    val list = _queue.value
    val idx = _queueIndex.value
    if (idx !in list.indices) return

    val song = list[idx]
    _currentSong.value = song

    try {
      val player = getActivePlayer()
      if (player != null) {
        applyRepeatModeToPlayer(player, _repeatMode.value)

        isRebuildingQueue = true
        player.stop()
        player.clearMediaItems()

        val mediaItems = list.map { createMediaItem(it) }

        player.setMediaItems(mediaItems, idx, 0L)
        player.prepare()
        player.play()
        isRebuildingQueue = false

        setupEqualizer()

        _durationMs.value = player.duration.let { if (it > 0) it else song.durationMs }
        _currentPositionMs.value = 0L

        scope.launch(Dispatchers.IO) {
          songDao.incrementPlayCount(song.id, System.currentTimeMillis())
        }
      } else {
        isRebuildingQueue = false
      }
    } catch (e: Exception) {
      isRebuildingQueue = false
      CrashLogger.e(TAG, "Error playing queue item", e)
      _isPlaying.value = false
    }
  }

  fun updateCurrentSong(song: Song) {
    if (_currentSong.value?.id == song.id) {
      _currentSong.value = song
      try {
        val player = getActivePlayer() ?: return
        val displayArtist = if (song.artist.isBlank() || song.artist.equals("Unknown Artist", ignoreCase = true) || song.artist.equals("<unknown>", ignoreCase = true)) {
          "SpotiOffline"
        } else {
          song.artist
        }
        val mediaMetadata = MediaMetadata.Builder()
          .setTitle(song.title)
          .setArtist(displayArtist)
          .setAlbumTitle(song.album)
          .setArtworkUri(if (!song.albumArtUri.isNullOrBlank()) Uri.parse(song.albumArtUri) else null)
          .build()
        if (player.currentMediaItem?.mediaId == song.id) {
          player.replaceMediaItem(
            player.currentMediaItemIndex,
            player.currentMediaItem!!.buildUpon().setMediaMetadata(mediaMetadata).build()
          )
        }
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Error updating current song metadata on player", e)
      }
    }
  }

  suspend fun refreshCurrentSongFromDb() {
    val id = _currentSong.value?.id ?: return
    val fresh = songDao.getSongById(id)
    if (fresh != null) {
      updateCurrentSong(fresh)
    }
  }

  private fun handleTrackCompletion() {
    if (_repeatMode.value == RepeatMode.OFF) {
      _isPlaying.value = false
      _currentPositionMs.value = 0L
    } else if (_repeatMode.value == RepeatMode.ALL && _shuffleEnabled.value) {
      // Re-shuffle for a fresh lap
      val current = _currentSong.value
      val freshShuffled = SmartShuffle.order(originalQueue, current)
      updatePlayerQueueForShuffle(freshShuffled, current)
    }
  }

  fun togglePlayPause() {
    try {
      val player = getActivePlayer() ?: return
      if (_currentSong.value == null) return

      if (player.isPlaying) {
        player.pause()
        _isPlaying.value = false
      } else {
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
          playCurrentQueueItem()
        } else {
          player.play()
          _isPlaying.value = true
        }
      }
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error in togglePlayPause", e)
    }
  }

  fun next() {
    try {
      val player = getActivePlayer()
      if (player != null && player.hasNextMediaItem()) {
        player.seekToNextMediaItem()
        player.play()
      } else {
        val list = _queue.value
        if (list.isEmpty()) return
        val nextIdx = (_queueIndex.value + 1) % list.size
        _queueIndex.value = nextIdx
        playCurrentQueueItem()
      }
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error in next()", e)
      val list = _queue.value
      if (list.isNotEmpty()) {
        _queueIndex.value = (_queueIndex.value + 1) % list.size
        playCurrentQueueItem()
      }
    }
  }

  fun previous() {
    try {
      val player = getActivePlayer()
      if (player != null) {
        if (player.currentPosition > 3000) {
          player.seekTo(0)
          _currentPositionMs.value = 0L
          return
        }
        if (player.hasPreviousMediaItem()) {
          player.seekToPreviousMediaItem()
          player.play()
          return
        }
      }
      val list = _queue.value
      if (list.isEmpty()) return
      val prevIdx = if (_queueIndex.value - 1 < 0) list.size - 1 else _queueIndex.value - 1
      _queueIndex.value = prevIdx
      playCurrentQueueItem()
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error in previous()", e)
    }
  }

  fun seekTo(positionMs: Long) {
    try {
      getActivePlayer()?.seekTo(positionMs)
      _currentPositionMs.value = positionMs
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error in seekTo", e)
    }
  }

  fun toggleShuffle() {
    val newShuffleState = !_shuffleEnabled.value
    _shuffleEnabled.value = newShuffleState
    prefs.edit().putBoolean("shuffle_enabled", newShuffleState).apply()

    val current = _currentSong.value
    if (newShuffleState) {
      if (originalQueue.isEmpty()) {
        originalQueue = _queue.value
      }
      val newOrderedQueue = SmartShuffle.order(_queue.value, current)
      updatePlayerQueueForShuffle(newOrderedQueue, current)
    } else {
      if (originalQueue.isNotEmpty()) {
        val restoredQueue = originalQueue
        updatePlayerQueueForShuffle(restoredQueue, current)
      }
    }
  }

  fun toggleRepeat() {
    val nextMode = when (_repeatMode.value) {
      RepeatMode.OFF -> RepeatMode.ALL
      RepeatMode.ALL -> RepeatMode.ONE
      RepeatMode.ONE -> RepeatMode.OFF
    }
    _repeatMode.value = nextMode
    prefs.edit().putString("repeat_mode", nextMode.name).apply()
    try {
      getActivePlayer()?.let { applyRepeatModeToPlayer(it, nextMode) }
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error toggling repeat mode", e)
    }
  }

  private fun updatePlayerQueueForShuffle(newQueue: List<Song>, currentSong: Song?) {
    try {
      isRebuildingQueue = true
      val player = getActivePlayer()
      if (newQueue.isEmpty() || player == null) {
        isRebuildingQueue = false
        return
      }

      val targetIndexInNewQueue = newQueue.indexOfFirst { it.id == currentSong?.id }.let { if (it >= 0) it else 0 }
      val mediaItems = newQueue.map { createMediaItem(it) }

      _queue.value = newQueue
      _queueIndex.value = targetIndexInNewQueue
      _currentSong.value = newQueue.getOrNull(targetIndexInNewQueue)

      val currentIdx = player.currentMediaItemIndex
      if (player.mediaItemCount > 0 && currentIdx in 0 until player.mediaItemCount) {
        if (currentIdx < player.mediaItemCount - 1) {
          player.removeMediaItems(currentIdx + 1, player.mediaItemCount)
        }
        if (currentIdx > 0) {
          player.removeMediaItems(0, currentIdx)
        }
        if (targetIndexInNewQueue > 0) {
          player.addMediaItems(0, mediaItems.subList(0, targetIndexInNewQueue))
        }
        if (targetIndexInNewQueue < mediaItems.size - 1) {
          player.addMediaItems(mediaItems.subList(targetIndexInNewQueue + 1, mediaItems.size))
        }
      } else {
        player.setMediaItems(mediaItems, targetIndexInNewQueue, 0L)
      }

      isRebuildingQueue = false
    } catch (e: Exception) {
      isRebuildingQueue = false
      CrashLogger.e(TAG, "Error updating player queue for shuffle", e)
    }
  }

  fun addToQueue(song: Song) {
    if (song.playbackError == 1) return
    isRebuildingQueue = true

    val updated = _queue.value.toMutableList()
    updated.add(song)
    _queue.value = updated

    val origUpdated = originalQueue.toMutableList()
    origUpdated.add(song)
    originalQueue = origUpdated

    try {
      getActivePlayer()?.addMediaItem(createMediaItem(song))
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error adding item to queue", e)
    } finally {
      isRebuildingQueue = false
    }
  }

  fun playNext(song: Song) {
    if (song.playbackError == 1) return
    isRebuildingQueue = true

    val currentList = _queue.value.toMutableList()
    val currentIdx = _queueIndex.value
    val insertAt = (currentIdx + 1).coerceAtMost(currentList.size)
    currentList.add(insertAt, song)
    _queue.value = currentList

    val origList = originalQueue.toMutableList()
    val origCurrentIdx = origList.indexOfFirst { it.id == _currentSong.value?.id }
    val origInsertAt = if (origCurrentIdx >= 0) origCurrentIdx + 1 else origList.size
    origList.add(origInsertAt, song)
    originalQueue = origList

    try {
      val player = getActivePlayer()
      player?.addMediaItem(currentIdx + 1, createMediaItem(song))
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error adding playNext item", e)
    } finally {
      isRebuildingQueue = false
    }
  }

  fun removeFromQueue(index: Int) {
    val list = _queue.value.toMutableList()
    if (index in list.indices && list.size > 1) {
      isRebuildingQueue = true
      val removedSong = list.removeAt(index)
      _queue.value = list

      val origList = originalQueue.toMutableList()
      origList.removeAll { it.id == removedSong.id }
      originalQueue = origList

      if (_queueIndex.value >= list.size) {
        _queueIndex.value = list.size - 1
      }

      try {
        getActivePlayer()?.removeMediaItem(index)
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Error removing item from queue", e)
      } finally {
        isRebuildingQueue = false
      }
    }
  }

  fun setSleepTimer(minutes: Int) {
    sleepTimer?.cancel()
    _sleepTimerRemainingMinutes.value = minutes

    if (minutes <= 0) return

    val millis = minutes * 60 * 1000L
    sleepTimer = object : CountDownTimer(millis, 60000L) {
      override fun onTick(millisUntilFinished: Long) {
        _sleepTimerRemainingMinutes.value = (millisUntilFinished / 60000L).toInt() + 1
      }

      override fun onFinish() {
        _sleepTimerRemainingMinutes.value = 0
        try {
          getActivePlayer()?.pause()
        } catch (e: Exception) {
          CrashLogger.e(TAG, "Error pausing player on sleep timer finish", e)
        }
        _isPlaying.value = false
      }
    }.start()
  }

  fun cancelSleepTimer() {
    sleepTimer?.cancel()
    _sleepTimerRemainingMinutes.value = 0
  }

  private fun setupEqualizer() {
    try {
      val sessionId = getActivePlayer()?.audioSessionId ?: return
      if (sessionId <= 0) return
      if (equalizer == null || equalizer?.hasControl() == false) {
        equalizer = Equalizer(0, sessionId).apply {
          enabled = true
        }
      }

      val eq = equalizer ?: return
      val numBands = eq.numberOfBands
      val minLevel = eq.bandLevelRange[0]
      val maxLevel = eq.bandLevelRange[1]

      val bandList = mutableListOf<EqualizerBand>()
      for (i in 0 until numBands) {
        val bandShort = i.toShort()
        val centerFreq = eq.getCenterFreq(bandShort) / 1000
        val currentLevel = eq.getBandLevel(bandShort)
        bandList.add(
          EqualizerBand(
            bandIndex = bandShort,
            centerFreqHz = centerFreq,
            minLevelMilliBels = minLevel,
            maxLevelMilliBels = maxLevel,
            currentLevelMilliBels = currentLevel
          )
        )
      }
      _equalizerBands.value = bandList
    } catch (e: Exception) {
      CrashLogger.w(TAG, "Failed setting up equalizer", e)
    }
  }

  fun setEqualizerBandLevel(bandIndex: Short, levelMilliBels: Short) {
    try {
      equalizer?.setBandLevel(bandIndex, levelMilliBels)
      val current = _equalizerBands.value.toMutableList()
      val index = current.indexOfFirst { it.bandIndex == bandIndex }
      if (index >= 0) {
        current[index] = current[index].copy(currentLevelMilliBels = levelMilliBels)
        _equalizerBands.value = current
      }
      _currentPreset.value = "Custom"
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error setting equalizer band level", e)
    }
  }

  fun setEqualizerPreset(presetName: String) {
    _currentPreset.value = presetName
    val eq = equalizer ?: return
    try {
      val min = eq.bandLevelRange[0].toInt()
      val max = eq.bandLevelRange[1].toInt()
      val mid = (min + max) / 2

      val boosts: List<Float> = when (presetName) {
        "Bass Boost" -> listOf(0.8f, 0.6f, 0.0f, -0.1f, -0.2f)
        "Vocal Boost" -> listOf(-0.2f, 0.1f, 0.7f, 0.8f, 0.3f)
        "Rock" -> listOf(0.6f, 0.3f, -0.1f, 0.4f, 0.7f)
        "Pop" -> listOf(-0.1f, 0.3f, 0.6f, 0.4f, -0.2f)
        "Electronic" -> listOf(0.7f, 0.5f, 0.0f, 0.4f, 0.6f)
        "Jazz" -> listOf(0.3f, 0.1f, 0.2f, 0.3f, 0.5f)
        else -> listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
      }

      val bands = _equalizerBands.value.toMutableList()
      for (i in bands.indices) {
        val boostFraction = boosts.getOrElse(i) { 0f }
        val targetLevel = (mid + (max - mid) * boostFraction).toInt().toShort()
        eq.setBandLevel(bands[i].bandIndex, targetLevel)
        bands[i] = bands[i].copy(currentLevelMilliBels = targetLevel)
      }
      _equalizerBands.value = bands
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error setting equalizer preset $presetName", e)
    }
  }

  private fun startPositionTicker() {
    positionUpdateJob?.cancel()
    positionUpdateJob = scope.launch(Dispatchers.Main) {
      while (isActive) {
        val player = MusicPlaybackService.getInstance()?.player ?: MusicPlaybackService.playerFlow.value
        if (player != null && _isPlaying.value) {
          try {
            _currentPositionMs.value = player.currentPosition
            if (player.duration > 0) {
              _durationMs.value = player.duration
            }
            playedCurrentSongTimeMs += 200L
            if (playedCurrentSongTimeMs >= 3000L) {
              consecutiveErrors = 0
            }
          } catch (e: Exception) {
            CrashLogger.w(TAG, "Error updating playback position ticker", e)
          }
        }
        delay(200)
      }
    }
  }

  fun release() {
    positionUpdateJob?.cancel()
    sleepTimer?.cancel()
    try {
      equalizer?.release()
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error releasing equalizer", e)
    }
    try {
      mediaController?.release()
      mediaController = null
      mediaControllerFuture?.let { MediaController.releaseFuture(it) }
      mediaControllerFuture = null
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error releasing MediaController", e)
    }
  }
}

