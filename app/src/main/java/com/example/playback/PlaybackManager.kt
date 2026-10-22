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

  private var exoPlayer: ExoPlayer? = null
  private var equalizer: Equalizer? = null
  private var positionUpdateJob: Job? = null
  private var sleepTimer: CountDownTimer? = null

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
    startPositionTicker()
  }

  private fun getOrCreatePlayer(): ExoPlayer {
    val servicePlayer = MusicPlaybackService.getInstance()?.player
    if (servicePlayer != null) {
      android.util.Log.d("PlaybackManager", "PLAYER INSTANCES: 1")
      return servicePlayer
    }
    // Start service to initialize the single ExoPlayer instance
    MusicPlaybackService.startOrUpdate(context, _currentSong.value ?: Song("", "", "", "", 0L, ""), true)
    val startedPlayer = MusicPlaybackService.getInstance()?.player
    if (startedPlayer != null) {
      android.util.Log.d("PlaybackManager", "PLAYER INSTANCES: 1")
      return startedPlayer
    }
    throw IllegalStateException("MusicPlaybackService player not initialized")
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
      val player = getOrCreatePlayer()
      player.stop()
      player.clearMediaItems()

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

      val mediaItem = MediaItem.Builder()
        .setUri(Uri.parse(song.uriString))
        .setMediaId(song.id)
        .setMediaMetadata(mediaMetadata)
        .build()

      player.setMediaItem(mediaItem)
      player.prepare()
      player.play()

      setupEqualizer()

      _isPlaying.value = true
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
      // Also update player media metadata if active
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
        // If current media item matches, update metadata
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
    when (_repeatMode.value) {
      RepeatMode.ONE -> {
        val player = getOrCreatePlayer()
        player.seekTo(0)
        player.play()
        _isPlaying.value = true
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
          _currentSong.value?.let { MusicPlaybackService.startOrUpdate(context, it, isPlaying = false) }
        }
      }
    }
  }

  fun togglePlayPause() {
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
  }

  fun next() {
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

  fun previous() {
    val player = getOrCreatePlayer()
    if (player.currentPosition > 3000) {
      player.seekTo(0)
      _currentPositionMs.value = 0L
      return
    }

    val list = _queue.value
    if (list.isEmpty()) return

    val prevIdx = if (_queueIndex.value - 1 < 0) list.size - 1 else _queueIndex.value - 1
    _queueIndex.value = prevIdx
    playCurrentQueueItem()
  }

  fun seekTo(positionMs: Long) {
    try {
      getOrCreatePlayer().seekTo(positionMs)
      _currentPositionMs.value = positionMs
    } catch (_: Exception) {}
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
      exoPlayer?.release()
    } catch (_: Exception) {}
  }
}
