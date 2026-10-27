package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.utils.CrashLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlaybackService : MediaSessionService() {

  private var mediaSession: MediaSession? = null
  lateinit var player: ExoPlayer
    private set

  private val handler = Handler(Looper.getMainLooper())
  private var idleTimeoutRunnable: Runnable? = null
  private var queueEndRunnable: Runnable? = null
  private var playerListener: Player.Listener? = null

  companion object {
    private const val TAG = "MusicPlaybackService"
    const val NOTIFICATION_ID = 1
    const val CHANNEL_ID = "music_playback_channel"

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow: StateFlow<ExoPlayer?> = _playerFlow.asStateFlow()

    private var instance: MusicPlaybackService? = null
    fun getInstance(): MusicPlaybackService? = instance
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    CrashLogger.d(TAG, "Creating MusicPlaybackService")

    player = ExoPlayer.Builder(this)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
          .setUsage(C.USAGE_MEDIA)
          .build(),
        true
      )
      .setWakeMode(C.WAKE_MODE_LOCAL)
      .setHandleAudioBecomingNoisy(true)
      .build()

    _playerFlow.value = player

    createNotificationChannel()

    val pendingIntent = createContentPendingIntent()
    mediaSession = MediaSession.Builder(this, player)
      .setSessionActivity(pendingIntent)
      .build()

    addSession(mediaSession!!)

    val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
      .setNotificationId(NOTIFICATION_ID)
      .setChannelId(CHANNEL_ID)
      .build().apply {
        setSmallIcon(android.R.drawable.ic_media_play)
      }
    setMediaNotificationProvider(notificationProvider)

    setupPlayerListener()
  }

  private fun setupPlayerListener() {
    val listener = object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
          cancelIdleTimeout()
          cancelQueueEndTimeout()
        } else {
          scheduleIdleTimeout()
        }
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          if (player.repeatMode == Player.REPEAT_MODE_OFF) {
            scheduleQueueEndTimeout()
          }
        } else if (playbackState == Player.STATE_READY && player.isPlaying) {
          cancelIdleTimeout()
          cancelQueueEndTimeout()
        }
      }
    }
    player.addListener(listener)
    playerListener = listener
  }

  private fun createContentPendingIntent(): PendingIntent {
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channelName = "Music Playback"
      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW).apply {
        description = "SpotiOffline Music Playback"
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    if (!player.isPlaying && !player.playWhenReady) {
      try {
        stopSelf()
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Error in onTaskRemoved", e)
      }
    }
  }

  private fun scheduleIdleTimeout() {
    cancelIdleTimeout()
    idleTimeoutRunnable = Runnable {
      if (!player.isPlaying) {
        try {
          stopSelf()
        } catch (e: Exception) {
          CrashLogger.e(TAG, "Error in idleTimeoutRunnable", e)
        }
      }
    }
    handler.postDelayed(idleTimeoutRunnable!!, 10 * 60 * 1000L)
  }

  private fun cancelIdleTimeout() {
    idleTimeoutRunnable?.let { handler.removeCallbacks(it) }
    idleTimeoutRunnable = null
  }

  private fun scheduleQueueEndTimeout() {
    cancelQueueEndTimeout()
    queueEndRunnable = Runnable {
      if (!player.isPlaying) {
        try {
          stopSelf()
        } catch (e: Exception) {
          CrashLogger.e(TAG, "Error in queueEndTimeoutRunnable", e)
        }
      }
    }
    handler.postDelayed(queueEndRunnable!!, 30 * 1000L)
  }

  private fun cancelQueueEndTimeout() {
    queueEndRunnable?.let { handler.removeCallbacks(it) }
    queueEndRunnable = null
  }

  override fun onDestroy() {
    cancelIdleTimeout()
    cancelQueueEndTimeout()
    playerListener?.let {
      player.removeListener(it)
      playerListener = null
    }
    _playerFlow.value = null
    mediaSession?.run {
      player.release()
      release()
      mediaSession = null
    }
    instance = null
    super.onDestroy()
  }
}

