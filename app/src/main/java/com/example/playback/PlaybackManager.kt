package com.example.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.CountDownTimer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.SongDao
import com.example.data.model.RepeatMode
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

  private val prefs = context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
  private var originalQueue: List<Song> = emptyList()

  private var equalizer: Equalizer? = null
  private var positionUpdateJob: Job? = null
  private var sleepTimer: CountDownTimer? = null
  private var playerListenerAttached = false

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
    } catch (_: Exception) {
      RepeatMode.OFF
    }
    _repeatMode.value = savedRepeat

    ensurePlayerListener()
    startPositionTicker()
  }

  private fun getOrCreatePlayer(): ExoPlayer {
    val servicePlayer = MusicPlaybackService.getInstance()?.player
    if (servicePlayer != null) {
      android.util.Log.d("PlaybackManager", "PLAYER INSTANCES: 1")
      return servicePlayer
    }
    MusicPlaybackService.startOrUpdate(context, _currentSong.value, true)
    var attempts = 0
    while (MusicPlaybackService.getInstance()?.player == null && attempts < 15) {
      try {
        Thread.sleep(50)
      } catch (_: Exception) {}
      attempts++
    }
    val startedPlayer = MusicPlaybackService.getInstance()?.player
    if (startedPlayer != null) {
      android.util.Log.d("PlaybackManager", "PLAYER INSTANCES: 1")
      return startedPlayer
    }
    throw IllegalStateException("MusicPlaybackService player not initialized")
  }

  private fun ensurePlayerListener() {
    try {
      val player = getOrCreatePlayer()
      applyRepeatModeToPlayer(player, _repeatMode.value)

      if (!playerListenerAttached) {
        player.addListener(object : Player.Listener {
          override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
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
            try {
              val currentIdx = getOrCreatePlayer().currentMediaItemIndex
              if (currentIdx in _queue.value.indices) {
                _queueIndex.value = currentIdx
                _currentSong.value = _queue.value[currentIdx]
              }
            } catch (_: Exception) {}
          }
        })
        playerListenerAttached = true
      }
    } catch (_: Exception) {}
  }

  private fun applyRepeatModeToPlayer(player: ExoPlayer, mode: RepeatMode) {
    player.repeatMode = when (mode) {
      RepeatMode.OFF -> Player.REPEAT_MODE_OFF
      RepeatMode.ALL -> Player.REPEAT_MODE_ALL
      RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }
  }

  fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
    val fullQueue = if (contextQueue.isNotEmpty()) contextQueue else listOf(song)
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
    if (songs.isEmpty()) return
    originalQueue = songs

    if (_shuffleEnabled.value) {
      val selectedSong = songs.getOrNull(startIndex) ?: songs.first()
      val shuffledQueue = SmartShuffle.order(songs, selectedSong)
      _queue.value = shuffledQueue
      _queueIndex.value = 0
    } else {
      _queue.value = songs
      _queueIndex.value = startIndex.coerceIn(0, songs.size - 1)
    }
    playCurrentQueueItem()
  }

  fun playShuffle(songs: List<Song>) {
    if (songs.isEmpty()) return
    originalQueue = songs
    _shuffleEnabled.value = true
    prefs.edit().putBoolean("shuffle_enabled", true).apply()

    val startingSong = songs.random()
    val shuffledQueue = SmartShuffle.order(songs, startingSong)
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
      ensurePlayerListener()
      val player = getOrCreatePlayer()
      applyRepeatModeToPlayer(player, _repeatMode.value)

      player.stop()
      player.clearMediaItems()

      val mediaItems = list.map { createMediaItem(it) }

      player.setMediaItems(mediaItems, idx, 0L)
      player.prepare()
      player.play()

      setupEqualizer()

      _durationMs.value = player.duration.let { if (it > 0) it else song.durationMs }
      _currentPositionMs.value = 0L

      scope.launch(Dispatchers.IO) {
        songDao.incrementPlayCount(song.id, System.currentTimeMillis())
      }

      MusicPlaybackService.startOrUpdate(context, song, isPlaying = true)
    } catch (e: Exception) {
      _isPlaying.value = false
    }
  }

  fun updateCurrentSong(song: Song) {
    if (_currentSong.value?.id == song.id) {
      _currentSong.value = song
      try {
        val player = getOrCreatePlayer()
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
      } catch (_: Exception) {}
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
      _currentSong.value?.let { MusicPlaybackService.startOrUpdate(context, it, isPlaying = false) }
    }
  }

  fun togglePlayPause() {
    try {
      ensurePlayerListener()
      val player = getOrCreatePlayer()
      if (_currentSong.value == null) {
        return
      }

      if (player.isPlaying) {
        player.pause()
        _isPlaying.value = false
        _currentSong.value?.let { MusicPlaybackService.startOrUpdate(context, it, isPlaying = false) }
      } else {
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
          playCurrentQueueItem()
        } else {
          player.play()
          _isPlaying.value = true
          _currentSong.value?.let { MusicPlaybackService.startOrUpdate(context, it, isPlaying = true) }
        }
      }
    } catch (_: Exception) {}
  }

  fun next() {
    try {
      ensurePlayerListener()
      val player = getOrCreatePlayer()
      if (player.hasNextMediaItem()) {
        player.seekToNextMediaItem()
        player.play()
      } else {
        val list = _queue.value
        if (list.isEmpty()) return
        val nextIdx = (_queueIndex.value + 1) % list.size
        _queueIndex.value = nextIdx
        playCurrentQueueItem()
      }
    } catch (_: Exception) {
      val list = _queue.value
      if (list.isNotEmpty()) {
        _queueIndex.value = (_queueIndex.value + 1) % list.size
        playCurrentQueueItem()
      }
    }
  }

  fun previous() {
    try {
      ensurePlayerListener()
      val player = getOrCreatePlayer()
      if (player.currentPosition > 3000) {
        player.seekTo(0)
        _currentPositionMs.value = 0L
        return
      }
      if (player.hasPreviousMediaItem()) {
        player.seekToPreviousMediaItem()
        player.play()
      } else {
        val list = _queue.value
        if (list.isEmpty()) return
        val prevIdx = if (_queueIndex.value - 1 < 0) list.size - 1 else _queueIndex.value - 1
        _queueIndex.value = prevIdx
        playCurrentQueueItem()
      }
    } catch (_: Exception) {}
  }

  fun seekTo(positionMs: Long) {
    try {
      getOrCreatePlayer().seekTo(positionMs)
      _currentPositionMs.value = positionMs
    } catch (_: Exception) {}
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
      applyRepeatModeToPlayer(getOrCreatePlayer(), nextMode)
    } catch (e: Exception) {
      android.util.Log.e("PlaybackManager", "Error toggling repeat mode", e)
    }
  }

  private fun updatePlayerQueueForShuffle(newQueue: List<Song>, currentSong: Song?) {
    try {
      val player = getOrCreatePlayer()
      if (newQueue.isEmpty()) return
      val targetIndexInNewQueue = newQueue.indexOfFirst { it.id == currentSong?.id }.let { if (it >= 0) it else 0 }
      val mediaItems = newQueue.map { createMediaItem(it) }

      val currentIdx = player.currentMediaItemIndex
      if (player.mediaItemCount > 0 && currentIdx in 0 until player.mediaItemCount) {
        if (currentIdx < player.mediaItemCount - 1) {
          player.removeMediaItems(currentIdx + 1, player.mediaItemCount)
        }
        if (currentIdx > 0) {
          player.removeMediaItems(0, currentIdx)
        }
        // Current item is now at index 0
        if (targetIndexInNewQueue > 0) {
          player.addMediaItems(0, mediaItems.subList(0, targetIndexInNewQueue))
        }
        if (targetIndexInNewQueue < mediaItems.size - 1) {
          player.addMediaItems(mediaItems.subList(targetIndexInNewQueue + 1, mediaItems.size))
        }
      } else {
        player.setMediaItems(mediaItems, targetIndexInNewQueue, 0L)
      }

      _queue.value = newQueue
      _queueIndex.value = targetIndexInNewQueue
    } catch (e: Exception) {
      android.util.Log.e("PlaybackManager", "Error updating player queue for shuffle", e)
    }
  }

  fun addToQueue(song: Song) {
    val updated = _queue.value.toMutableList()
    updated.add(song)
    _queue.value = updated

    val origUpdated = originalQueue.toMutableList()
    origUpdated.add(song)
    originalQueue = origUpdated

    try {
      getOrCreatePlayer().addMediaItem(createMediaItem(song))
    } catch (e: Exception) {
      android.util.Log.e("PlaybackManager", "Error adding item to queue", e)
    }
  }

  fun playNext(song: Song) {
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
      val player = getOrCreatePlayer()
      player.addMediaItem(currentIdx + 1, createMediaItem(song))
    } catch (e: Exception) {
      android.util.Log.e("PlaybackManager", "Error adding playNext item", e)
    }
  }

  fun removeFromQueue(index: Int) {
    val list = _queue.value.toMutableList()
    if (index in list.indices && list.size > 1) {
      val removedSong = list.removeAt(index)
      _queue.value = list

      val origList = originalQueue.toMutableList()
      origList.removeAll { it.id == removedSong.id }
      originalQueue = origList

      if (_queueIndex.value >= list.size) {
        _queueIndex.value = list.size - 1
      }

      try {
        getOrCreatePlayer().removeMediaItem(index)
      } catch (e: Exception) {
        android.util.Log.e("PlaybackManager", "Error removing item from queue", e)
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
          getOrCreatePlayer().pause()
        } catch (_: Exception) {}
        _isPlaying.value = false
        _currentSong.value?.let { MusicPlaybackService.startOrUpdate(context, it, isPlaying = false) }
      }
    }.start()
  }

  fun cancelSleepTimer() {
    sleepTimer?.cancel()
    _sleepTimerRemainingMinutes.value = 0
  }

  private fun setupEqualizer() {
    try {
      val sessionId = getOrCreatePlayer().audioSessionId
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
    } catch (_: Exception) {}
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
    } catch (_: Exception) {}
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
    } catch (_: Exception) {}
  }

  private fun startPositionTicker() {
    positionUpdateJob?.cancel()
    positionUpdateJob = scope.launch(Dispatchers.Main) {
      while (isActive) {
        val player = MusicPlaybackService.getInstance()?.player
        if (player != null && _isPlaying.value) {
          try {
            _currentPositionMs.value = player.currentPosition
            if (player.duration > 0) {
              _durationMs.value = player.duration
            }
          } catch (_: Exception) {}
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
    } catch (_: Exception) {}
  }
}
