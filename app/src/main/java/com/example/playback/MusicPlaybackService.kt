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
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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

  companion object {
    private const val TAG = "MusicPlaybackService"
    private const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "music_playback_channel"

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow: StateFlow<ExoPlayer?> = _playerFlow.asStateFlow()

    private var instance: MusicPlaybackService? = null
    fun getInstance(): MusicPlaybackService? = instance

    fun startOrUpdate(context: Context, song: com.example.data.model.Song?, isPlaying: Boolean) {
      val intent = Intent(context, MusicPlaybackService::class.java)
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Failed starting foreground service", e)
      }
    }
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
    val notification = buildNotification()
    startForeground(NOTIFICATION_ID, notification)

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
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      val notification = buildNotification()
      startForeground(NOTIFICATION_ID, notification)
    } catch (e: Exception) {
      CrashLogger.e(TAG, "Error in onStartCommand startForeground", e)
    }
    return super.onStartCommand(intent, flags, startId)
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
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val pendingIntent = createContentPendingIntent()
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("SpotiOffline")
      .setContentText("Playing music")
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    if (!player.isPlaying && !player.playWhenReady) {
      try {
        player.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      } catch (e: Exception) {
        CrashLogger.e(TAG, "Error in onTaskRemoved", e)
      }
    }
  }

  fun scheduleIdleTimeout() {
    cancelIdleTimeout()
    idleTimeoutRunnable = Runnable {
      if (!player.isPlaying) {
        try {
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        } catch (e: Exception) {
          CrashLogger.e(TAG, "Error in idleTimeoutRunnable", e)
        }
      }
    }
    handler.postDelayed(idleTimeoutRunnable!!, 10 * 60 * 1000L)
  }

  fun cancelIdleTimeout() {
    idleTimeoutRunnable?.let { handler.removeCallbacks(it) }
    idleTimeoutRunnable = null
  }

  fun scheduleQueueEndTimeout() {
    cancelQueueEndTimeout()
    queueEndRunnable = Runnable {
      if (!player.isPlaying) {
        try {
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        } catch (e: Exception) {
          CrashLogger.e(TAG, "Error in queueEndTimeoutRunnable", e)
        }
      }
    }
    handler.postDelayed(queueEndRunnable!!, 30 * 1000L)
  }

  fun cancelQueueEndTimeout() {
    queueEndRunnable?.let { handler.removeCallbacks(it) }
    queueEndRunnable = null
  }

  override fun onDestroy() {
    cancelIdleTimeout()
    cancelQueueEndTimeout()
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

