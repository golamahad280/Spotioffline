package com.example.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.CountDownTimer
import android.util.Log
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

  companion object {
    private const val TAG = "PlaybackManager"
  }

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
    ensurePlayerListener()
    startPositionTicker()
  }

  private fun getOrCreatePlayer(): ExoPlayer? {
    val servicePlayer = MusicPlaybackService.getInstance()?.player
    if (servicePlayer != null) {
      Log.d(TAG, "PLAYER INSTANCES: 1")
      return servicePlayer
    }
    MusicPlaybackService.startOrUpdate(context, true)
    val startedPlayer = MusicPlaybackService.getInstance()?.player
    if (startedPlayer != null) {
      Log.d(TAG, "PLAYER INSTANCES: 1")
      return startedPlayer
    }
    return null
  }

  private fun ensurePlayerListener() {
    try {
      val player = getOrCreatePlayer() ?: return
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

          override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            try {
              val currentIdx = getOrCreatePlayer()?.currentMediaItemIndex ?: 0
              if (currentIdx in _queue.value.indices) {
                _queueIndex.value = currentIdx
                _currentSong.value = _queue.value[currentIdx]
              }
            } catch (e: Exception) {
              Log.e(TAG, "Error in onMediaItemTransition", e)
            }
          }
        })
        playerListenerAttached = true
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in ensurePlayerListener", e)
    }
  }

  fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
    val fullQueue = if (contextQueue.isNotEmpty()) contextQueue else listOf(song)
    val index = fullQueue.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }

    _queue.value = fullQueue
    _queueIndex.value = index
    playCurrentQueueItem()
  }

  fun setQueue(songs: List<Song>, startIndex: Int = 0) {
    if (songs.isEmpty()) return
    _queue.value = songs
    _queueIndex.value = startIndex.coerceIn(0, songs.size - 1)
    playCurrentQueueItem()
  }

  private fun playCurrentQueueItem() {
    val list = _queue.value
    val idx = _queueIndex.value
    if (idx !in list.indices) return

    val song = list[idx]
    _currentSong.value = song

    try {
      MusicPlaybackService.startOrUpdate(context, true)
      ensurePlayerListener()
      val player = getOrCreatePlayer()
      if (player == null) {
        Log.e(TAG, "Player is null in playCurrentQueueItem")
        return
      }

      player.stop()
      player.clearMediaItems()

      val mediaItems = list.map { s ->
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

        MediaItem.Builder()
          .setUri(Uri.parse(s.uriString))
          .setMediaId(s.id)
          .setMediaMetadata(mediaMetadata)
          .build()
      }

      player.setMediaItems(mediaItems, idx, 0L)
      player.prepare()
      player.play()
      _isPlaying.value = true

      setupEqualizer()

      _durationMs.value = player.duration.let { if (it > 0) it else song.durationMs }
      _currentPositionMs.value = 0L

      scope.launch(Dispatchers.IO) {
        try {
          songDao.incrementPlayCount(song.id, System.currentTimeMillis())
        } catch (e: Exception) {
          Log.e(TAG, "Error incrementing play count", e)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in playCurrentQueueItem", e)
      _isPlaying.value = false
    }
  }

  fun updateCurrentSong(song: Song) {
    if (_currentSong.value?.id == song.id) {
      _currentSong.value = song
      try {
        val player = getOrCreatePlayer() ?: return
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
        Log.e(TAG, "Error in updateCurrentSong", e)
      }
    }
  }

  suspend fun refreshCurrentSongFromDb() {
    val id = _currentSong.value?.id ?: return
    try {
      val fresh = songDao.getSongById(id)
      if (fresh != null) {
        updateCurrentSong(fresh)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in refreshCurrentSongFromDb", e)
    }
  }

  private fun handleTrackCompletion() {
    when (_repeatMode.value) {
      RepeatMode.ONE -> {
        try {
          val player = getOrCreatePlayer() ?: return
          player.seekTo(0)
          player.play()
        } catch (e: Exception) {
          Log.e(TAG, "Error in handleTrackCompletion RepeatMode.ONE", e)
        }
      }
      RepeatMode.ALL -> {
        next()
      }
      RepeatMode.OFF -> {
        if (_queueIndex.value < _queue.value.size - 1) {
          next()
        } else {
          _isPlaying.value = false
          _currentPositionMs.value = 0L
          MusicPlaybackService.startOrUpdate(context, false)
        }
      }
    }
  }

  fun togglePlayPause() {
    try {
      ensurePlayerListener()
      val player = getOrCreatePlayer()
      if (player == null || _currentSong.value == null) {
        return
      }

      if (player.isPlaying) {
        player.pause()
        _isPlaying.value = false
        MusicPlaybackService.startOrUpdate(context, false)
      } else {
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
          playCurrentQueueItem()
        } else {
          player.play()
          _isPlaying.value = true
          MusicPlaybackService.startOrUpdate(context, true)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in togglePlayPause", e)
    }
  }

  fun next() {
    try {
      ensurePlayerListener()
      val player = getOrCreatePlayer()
      if (player != null && player.hasNextMediaItem()) {
        player.seekToNextMediaItem()
        player.play()
      } else {
        val list = _queue.value
        if (list.isEmpty()) return
        val nextIdx = if (_shuffleEnabled.value) {
          (list.indices).random()
        } else {
          (_queueIndex.value + 1) % list.size
        }
        _queueIndex.value = nextIdx
        playCurrentQueueItem()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in next", e)
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
      if (player != null && player.currentPosition > 3000) {
        player.seekTo(0)
        _currentPositionMs.value = 0L
        return
      }
      if (player != null && player.hasPreviousMediaItem()) {
        player.seekToPreviousMediaItem()
        player.play()
      } else {
        val list = _queue.value
        if (list.isEmpty()) return
        val prevIdx = if (_queueIndex.value - 1 < 0) list.size - 1 else _queueIndex.value - 1
        _queueIndex.value = prevIdx
        playCurrentQueueItem()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in previous", e)
    }
  }

  fun seekTo(positionMs: Long) {
    try {
      getOrCreatePlayer()?.seekTo(positionMs)
      _currentPositionMs.value = positionMs
    } catch (e: Exception) {
      Log.e(TAG, "Error in seekTo", e)
    }
  }

  fun toggleShuffle() {
    _shuffleEnabled.value = !_shuffleEnabled.value
  }

  fun toggleRepeat() {
    _repeatMode.value = when (_repeatMode.value) {
      RepeatMode.OFF -> RepeatMode.ALL
      RepeatMode.ALL -> RepeatMode.ONE
      RepeatMode.ONE -> RepeatMode.OFF
    }
  }

  fun addToQueue(song: Song) {
    val updated = _queue.value.toMutableList()
    updated.add(song)
    _queue.value = updated
  }

  fun playNext(song: Song) {
    val updated = _queue.value.toMutableList()
    val insertAt = (_queueIndex.value + 1).coerceAtMost(updated.size)
    updated.add(insertAt, song)
    _queue.value = updated
  }

  fun removeFromQueue(index: Int) {
    val list = _queue.value.toMutableList()
    if (index in list.indices && list.size > 1) {
      list.removeAt(index)
      _queue.value = list
      if (_queueIndex.value >= list.size) {
        _queueIndex.value = list.size - 1
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
          getOrCreatePlayer()?.pause()
        } catch (e: Exception) {
          Log.e(TAG, "Error in sleep timer onFinish", e)
        }
        _isPlaying.value = false
        MusicPlaybackService.startOrUpdate(context, false)
      }
    }.start()
  }

  fun cancelSleepTimer() {
    sleepTimer?.cancel()
    _sleepTimerRemainingMinutes.value = 0
  }

  private fun setupEqualizer() {
    try {
      val sessionId = getOrCreatePlayer()?.audioSessionId ?: return
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
      Log.e(TAG, "Error in setupEqualizer", e)
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
      Log.e(TAG, "Error in setEqualizerBandLevel", e)
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
      Log.e(TAG, "Error in setEqualizerPreset", e)
    }
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
          } catch (e: Exception) {
            Log.e(TAG, "Error in position ticker", e)
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
      Log.e(TAG, "Error releasing equalizer", e)
    }
  }
}
