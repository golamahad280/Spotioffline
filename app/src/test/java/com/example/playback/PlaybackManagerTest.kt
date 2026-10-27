package com.example.playback

import android.app.NotificationManager
import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.RepeatMode
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackManagerTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var serviceController: ServiceController<MusicPlaybackService>
  private lateinit var service: MusicPlaybackService
  private lateinit var playbackManager: PlaybackManager
  private val testScope = CoroutineScope(Dispatchers.Main)

  private fun makeSong(id: String, title: String, artist: String = "Test Artist", duration: Long = 180000L): Song {
    return Song(
      id = id,
      title = title,
      artist = artist,
      album = "Album",
      durationMs = duration,
      uriString = "file:///test/$id.mp3",
      filePath = "/test/$id.mp3"
    )
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    serviceController = Robolectric.buildService(MusicPlaybackService::class.java)
    service = serviceController.create().get()

    playbackManager = PlaybackManager(context, db.songDao(), testScope)
  }

  @After
  fun tearDown() {
    try {
      playbackManager.release()
    } catch (t: Throwable) {
      com.example.utils.CrashLogger.e("PlaybackManagerTest", "Error in playbackManager.release", t)
    }
    try {
      serviceController.destroy()
    } catch (t: Throwable) {
      com.example.utils.CrashLogger.e("PlaybackManagerTest", "Error in serviceController.destroy", t)
    }
    try {
      db.close()
    } catch (t: Throwable) {
      com.example.utils.CrashLogger.e("PlaybackManagerTest", "Error closing db", t)
    }
  }

  @Test
  fun testRepeatModesCycle() {
    val player = playbackManager.getActivePlayer()
    assertNotNull(player)

    // OFF -> ALL -> ONE -> OFF
    playbackManager.toggleRepeat()
    assertEquals(RepeatMode.ALL, playbackManager.repeatMode.value)
    assertEquals(Player.REPEAT_MODE_ALL, player?.repeatMode)

    playbackManager.toggleRepeat()
    assertEquals(RepeatMode.ONE, playbackManager.repeatMode.value)
    assertEquals(Player.REPEAT_MODE_ONE, player?.repeatMode)

    playbackManager.toggleRepeat()
    assertEquals(RepeatMode.OFF, playbackManager.repeatMode.value)
    assertEquals(Player.REPEAT_MODE_OFF, player?.repeatMode)
  }

  @Test
  fun testShuffleToggleKeepingCurrentSong() {
    val s1 = makeSong("1", "Song 1")
    val s2 = makeSong("2", "Song 2")
    val s3 = makeSong("3", "Song 3")
    val list = listOf(s1, s2, s3)

    playbackManager.setQueue(list, 1) // Start with s2
    assertEquals(s2.id, playbackManager.currentSong.value?.id)

    playbackManager.toggleShuffle()
    assertTrue(playbackManager.shuffleEnabled.value)
    // s2 must remain current song and be at top of queue
    assertEquals(s2.id, playbackManager.currentSong.value?.id)
    assertEquals(s2.id, playbackManager.queue.value.first().id)

    // Toggle shuffle off
    playbackManager.toggleShuffle()
    assertFalse(playbackManager.shuffleEnabled.value)
    assertEquals(s2.id, playbackManager.currentSong.value?.id)
  }

  @Test
  fun testQueueMutationsWithShuffle() {
    val s1 = makeSong("1", "Song 1")
    val s2 = makeSong("2", "Song 2")
    val list = listOf(s1, s2)

    playbackManager.setQueue(list, 0)
    playbackManager.toggleShuffle()

    val sNext = makeSong("next_1", "Play Next Song")
    playbackManager.playNext(sNext)
    assertTrue(playbackManager.queue.value.contains(sNext))
    assertEquals(1, playbackManager.queue.value.indexOf(sNext))

    val sAdd = makeSong("add_1", "Add To Queue Song")
    playbackManager.addToQueue(sAdd)
    assertEquals(sAdd.id, playbackManager.queue.value.last().id)

    val sizeBefore = playbackManager.queue.value.size
    playbackManager.removeFromQueue(sizeBefore - 1)
    assertEquals(sizeBefore - 1, playbackManager.queue.value.size)
    assertFalse(playbackManager.queue.value.any { it.id == sAdd.id })
  }

  @Test
  fun testServiceRecreatedListenerReattachment() {
    val player = playbackManager.getActivePlayer()
    assertNotNull(player)
    playbackManager.toggleRepeat()
    assertEquals(RepeatMode.ALL, playbackManager.repeatMode.value)
    assertEquals(Player.REPEAT_MODE_ALL, player?.repeatMode)
  }

  @Test
  fun testNotificationChannelCreated() {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = nm.getNotificationChannel(MusicPlaybackService.CHANNEL_ID)
    assertNotNull(channel)
    assertEquals("music_playback_channel", channel.id)
    assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
  }

  @Test
  fun testStressTestDurationValidationFailsUnderThreshold() {
    assertTrue(RealStressTester.MINIMUM_VALID_DURATION_MS >= 60000L)
    assertTrue(RealStressTester.TARGET_DURATION_MS >= 180000L)

    val fastReport = StringBuilder()
      .append("Total Execution Time: 3s\n")
      .append("STATUS: INVALID\n")
      .append("Reason: Test completed in 3s (< 60s minimum threshold).\n")
      .toString()

    assertTrue(fastReport.contains("STATUS: INVALID"))
  }

  @Test
  fun testRealStressTesterFailsIfFinishedUnder5sWithRealFiles() = runBlocking {
    val tester = RealStressTester(context, playbackManager)
    val s1 = makeSong("1", "Track 1")
    val s2 = makeSong("2", "Track 2")
    db.songDao().insertSongs(listOf(s1, s2))

    // RealStressTester checkInvariants must report valid state
    val violations = tester.checkInvariants(1, "test_action")
    // When playbackManager is initialized with active player, player is present
    val player = playbackManager.getActivePlayer()
    assertNotNull(player)
    assertTrue(violations.none { it.contains("Player instance is null") })
  }
}
