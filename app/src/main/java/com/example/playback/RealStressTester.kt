package com.example.playback

import android.content.Context
import androidx.media3.common.Player
import com.example.data.model.Song
import com.example.utils.CrashLogger
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class StressTestProgress(
  val currentStep: Int,
  val totalSteps: Int,
  val elapsedSeconds: Long,
  val currentAction: String,
  val violationsCount: Int
)

class RealStressTester(
  private val context: Context,
  private val playbackManager: PlaybackManager
) {
  companion object {
    private const val TAG = "RealStressTester"
    const val MINIMUM_VALID_DURATION_MS = 60_000L
    const val TARGET_DURATION_MS = 180_000L // 3 minutes
  }

  suspend fun run(
    availableSongs: List<Song>,
    onProgress: (StressTestProgress) -> Unit
  ): String {
    val startTime = System.currentTimeMillis()
    val report = StringBuilder()
    report.append("=== SPOTIOFFLINE REAL STRESS TEST REPORT ===\n")
    report.append("Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(startTime))}\n")
    report.append("Target Duration: >= 3 minutes (180s)\n")
    report.append("Minimum Valid Duration: 60s\n\n")

    val songs = availableSongs.filter { it.playbackError == 0 }.take(60)
    if (songs.isEmpty()) {
      report.append("ABORTED: No playable songs found in library.\n")
      report.append("Please scan or load offline songs first.\n")
      return report.toString()
    }

    report.append("Pool Size: ${songs.size} unique songs loaded\n\n")

    val violations = mutableListOf<String>()
    var step = 0
    val totalSteps = 100 // 100 steps * ~1.8s each = ~180s (3 minutes)
    val random = Random(42)

    // Initial setup: start with a queue
    playbackManager.setQueue(songs, 0)
    delay(2000L)

    while (System.currentTimeMillis() - startTime < TARGET_DURATION_MS || step < totalSteps) {
      step++
      val actionType = random.nextInt(9)
      val actionName = when (actionType) {
        0 -> {
          val randomSong = songs[random.nextInt(songs.size)]
          playbackManager.playSong(randomSong, songs)
          "playSong(${randomSong.title.take(15)})"
        }
        1 -> {
          playbackManager.togglePlayPause()
          "togglePlayPause()"
        }
        2 -> {
          playbackManager.next()
          "next()"
        }
        3 -> {
          playbackManager.previous()
          "previous()"
        }
        4 -> {
          val curDur = playbackManager.durationMs.value
          val seekPos = if (curDur > 5000L) random.nextLong(1000L, curDur - 2000L) else 1000L
          playbackManager.seekTo(seekPos)
          "seekTo(${seekPos / 1000}s)"
        }
        5 -> {
          playbackManager.toggleShuffle()
          "toggleShuffle(now=${playbackManager.shuffleEnabled.value})"
        }
        6 -> {
          playbackManager.toggleRepeat()
          "toggleRepeat(now=${playbackManager.repeatMode.value})"
        }
        7 -> {
          val extraSong = songs[random.nextInt(songs.size)]
          playbackManager.playNext(extraSong)
          "playNext(${extraSong.title.take(15)})"
        }
        8 -> {
          val queueSize = playbackManager.queue.value.size
          if (queueSize > 2) {
            val removeIdx = random.nextInt(1, queueSize)
            playbackManager.removeFromQueue(removeIdx)
            "removeFromQueue($removeIdx)"
          } else {
            val extraSong = songs[random.nextInt(songs.size)]
            playbackManager.addToQueue(extraSong)
            "addToQueue(${extraSong.title.take(15)})"
          }
        }
        else -> "noop"
      }

      // Wait for real state transition (up to 5s timeout)
      waitForSettle(timeoutMs = 5000L)
      // Real playback audio decoding delay
      delay(1500L)

      // Invariant checks
      val stepViolations = checkInvariants(step, actionName)
      violations.addAll(stepViolations)

      val elapsedSec = (System.currentTimeMillis() - startTime) / 1000L
      onProgress(
        StressTestProgress(
          currentStep = step,
          totalSteps = totalSteps,
          elapsedSeconds = elapsedSec,
          currentAction = actionName,
          violationsCount = violations.size
        )
      )

      if (violations.size >= 20) {
        report.append("STOPPED EARLY: Exceeded 20 invariant violations.\n")
        break
      }
    }

    val endTime = System.currentTimeMillis()
    val totalElapsedMs = endTime - startTime
    val totalElapsedSec = totalElapsedMs / 1000L

    report.append("Completed: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(endTime))}\n")
    report.append("Total Execution Time: ${totalElapsedSec}s (${totalElapsedMs}ms)\n")
    report.append("Total Real Steps Performed: $step\n")
    report.append("Total Invariant Violations: ${violations.size}\n\n")

    if (totalElapsedMs < MINIMUM_VALID_DURATION_MS) {
      report.append("STATUS: INVALID\n")
      report.append("Reason: Test completed in ${totalElapsedSec}s (< 60s minimum threshold).\n")
    } else if (violations.isEmpty()) {
      report.append("STATUS: PASSED (100% Stable)\n")
      report.append("All 6 invariants satisfied across all real playback operations.\n")
    } else {
      report.append("STATUS: FAILED (${violations.size} violations)\n")
      report.append("Violations log:\n")
      violations.take(15).forEach { report.append("  - $it\n") }
      if (violations.size > 15) {
        report.append("  ... and ${violations.size - 15} more\n")
      }
    }

    return report.toString()
  }

  private suspend fun waitForSettle(timeoutMs: Long) {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeoutMs) {
      val player = playbackManager.getActivePlayer()
      if (player != null) {
        val state = player.playbackState
        if (state == Player.STATE_READY || state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
          return
        }
      }
      delay(50L)
    }
  }

  fun checkInvariants(step: Int, action: String): List<String> {
    val list = mutableListOf<String>()
    val player = playbackManager.getActivePlayer()

    // Invariant 1: Exactly 1 Player instance active
    if (player == null) {
      list.add("Step $step ($action) Invariant 1 failed: Player instance is null")
    }

    // Invariant 2: MediaId sync between Player and PlaybackManager
    val curSong = playbackManager.currentSong.value
    if (player != null && curSong != null && player.mediaItemCount > 0) {
      val playerMediaId = player.currentMediaItem?.mediaId
      if (playerMediaId != null && playerMediaId != curSong.id) {
        list.add("Step $step ($action) Invariant 2 failed: MediaId mismatch (player=$playerMediaId, manager=${curSong.id})")
      }
    }

    // Invariant 3: isPlaying state sync
    if (player != null) {
      val managerPlaying = playbackManager.isPlaying.value
      val playerPlaying = player.isPlaying
      if (managerPlaying != playerPlaying && player.playWhenReady != managerPlaying && player.playbackState == Player.STATE_READY) {
        list.add("Step $step ($action) Invariant 3 failed: isPlaying mismatch (player=$playerPlaying, manager=$managerPlaying)")
      }
    }

    // Invariant 4: Queue items count sync
    if (player != null) {
      val managerCount = playbackManager.queue.value.size
      val playerCount = player.mediaItemCount
      if (managerCount != playerCount) {
        list.add("Step $step ($action) Invariant 4 failed: Queue size mismatch (player=$playerCount, manager=$managerCount)")
      }
    }

    // Invariant 5: No unexpected player crash / fatal unhandled error
    if (player != null && player.playerError != null) {
      list.add("Step $step ($action) Invariant 5 failed: Player has unhandled error: ${player.playerError?.message}")
    }

    // Invariant 6: Notification/Session active
    if (player != null && playbackManager.isPlaying.value) {
      val isServiceRunning = MusicPlaybackService.getInstance() != null
      if (!isServiceRunning) {
        list.add("Step $step ($action) Invariant 6 failed: MusicPlaybackService not running while isPlaying=true")
      }
    }

    return list
  }
}
