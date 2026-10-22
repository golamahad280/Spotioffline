package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity

class MusicPlaybackService : MediaSessionService() {

  private var mediaSession: MediaSession? = null
  lateinit var player: ExoPlayer
    private set

  private val handler = Handler(Looper.getMainLooper())
  private var idleTimeoutRunnable: Runnable? = null
  private var queueEndRunnable: Runnable? = null

  companion object {
    private var instance: MusicPlaybackService? = null
    fun getInstance(): MusicPlaybackService? = instance

    fun startOrUpdate(context: Context, song: com.example.data.model.Song, isPlaying: Boolean) {
      val intent = Intent(context, MusicPlaybackService::class.java)
      try {
        context.startService(intent)
      } catch (_: Exception) {}
    }
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    android.util.Log.d("MusicPlaybackService", "PLAYER INSTANCES: 1")

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

    player.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
          cancelIdleTimeout()
          cancelQueueEndTimeout()
        } else {
          if (player.playbackState == Player.STATE_ENDED) {
            scheduleQueueEndTimeout()
          } else {
            scheduleIdleTimeout()
          }
          try {
            stopForeground(false)
          } catch (_: Exception) {}
        }
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          if (!player.isPlaying) {
            scheduleQueueEndTimeout()
          }
        }
      }
    })

    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    mediaSession = MediaSession.Builder(this, player)
      .setSessionActivity(pendingIntent)
      .build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    val isPlaying = player.isPlaying
    if (!isPlaying) {
      try {
        player.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      } catch (_: Exception) {}
    }
  }

  private fun scheduleIdleTimeout() {
    cancelIdleTimeout()
    idleTimeoutRunnable = Runnable {
      if (!player.isPlaying) {
        try {
          player.release()
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        } catch (_: Exception) {}
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
          player.release()
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        } catch (_: Exception) {}
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
    mediaSession?.run {
      player.release()
      release()
      mediaSession = null
    }
    instance = null
    super.onDestroy()
  }
}
