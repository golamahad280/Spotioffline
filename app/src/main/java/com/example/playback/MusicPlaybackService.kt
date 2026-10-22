package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Song

class MusicPlaybackService : Service() {

  companion object {
    private const val CHANNEL_ID = "spoti_offline_playback_channel"
    private const val NOTIFICATION_ID = 1001

    const val ACTION_PLAY = "com.example.ACTION_PLAY"
    const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
    const val ACTION_NEXT = "com.example.ACTION_NEXT"
    const val ACTION_PREV = "com.example.ACTION_PREV"

    private const val EXTRA_TITLE = "extra_title"
    private const val EXTRA_ARTIST = "extra_artist"
    private const val EXTRA_IS_PLAYING = "extra_is_playing"

    fun startOrUpdate(context: Context, song: Song, isPlaying: Boolean) {
      val intent = Intent(context, MusicPlaybackService::class.java).apply {
        putExtra(EXTRA_TITLE, song.title)
        putExtra(EXTRA_ARTIST, song.artist)
        putExtra(EXTRA_IS_PLAYING, isPlaying)
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Exception) {}
    }
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val title = intent?.getStringExtra(EXTRA_TITLE) ?: "SpotiOffline"
    val artist = intent?.getStringExtra(EXTRA_ARTIST) ?: "Offline Music"
    val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, false) ?: false

    val notification = buildNotification(title, artist, isPlaying)
    startForeground(NOTIFICATION_ID, notification)

    return START_NOT_STICKY
  }

  private fun buildNotification(title: String, artist: String, isPlaying: Boolean): Notification {
    val openAppIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      this,
      0,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.spotioffline_launcher_fg_1792666780785)
      .setContentTitle(title)
      .setContentText(artist)
      .setContentIntent(contentPendingIntent)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(isPlaying)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Music Playback",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "SpotiOffline Music Player Controls"
        setShowBadge(false)
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager?.createNotificationChannel(channel)
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
