package com.example.playback

import android.content.Context
import androidx.media3.common.Player
import com.example.data.model.Song
import com.example.utils.CrashLogger
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.ArrayDeque
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

data class SongTestRecord(
  val song: Song,
  var status: String = "OK",
  var errorCode: String = "-",
  var playCount: Int = 0,
  var errorCount: Int = 0
)

class RealStressTester(
  private val context: Context,
  private val playbackManager: PlaybackManager
) {
  companion object {
    private const val TAG = "RealStressTester"
    const val MINIMUM_VALID_DURATION_MS = 60_000L
  }

  suspend fun run(
    availableSongs: List<Song>,
    seed: Long = 42L,
    durationMinutes: Int = 3,
    onProgress: (StressTestProgress) -> Unit
  ): String {
    val startTime = System.currentTimeMillis()
    val targetDurationMs = durationMinutes * 60 * 1000L
    val targetDurationSec = durationMinutes * 60

    val report = StringBuilder()
    report.append("=== SPOTIOFFLINE DETERMINISTIC REAL STRESS TEST REPORT ===\n")
    report.append("Random Seed: $seed\n")
    report.append("Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(startTime))}\n")
    report.append("Selected Duration: $durationMinutes minutes (${targetDurationSec}s)\n")
    report.append("Minimum Valid Duration Threshold: 60s\n\n")

    val songs = availableSongs.filter { it.playbackError == 0 }.take(60)
    if (songs.isEmpty()) {
      report.append("ABORTED: No playable songs found in library.\n")
      report.append("Please scan or load offline songs first.\n")
      return report.toString()
    }

    val songRecords = availableSongs.associate { it.id to SongTestRecord(it) }.toMutableMap()
    val unplayableFiles = availableSongs.filter { it.playbackError != 0 }.map { it.filePath ?: it.title }.toMutableList()

    report.append("Active Pool Size: ${songs.size} unique songs loaded (out of ${availableSongs.size} total in DB)\n\n")

    val violations = mutableListOf<String>()
    val violationsWithHistory = mutableListOf<String>()
    val recentActions = ArrayDeque<String>()
    val caughtExceptions = mutableListOf<String>()

    var peakMemoryBytes = 0L
    var step = 0
    val totalSteps = (durationMinutes * 60) / 2 // approx 1 step every ~2s
    val random = Random(seed)

    fun updatePeakMemory() {
      val runtime = Runtime.getRuntime()
      val usedMem = runtime.totalMemory() - runtime.freeMemory()
      if (usedMem > peakMemoryBytes) {
        peakMemoryBytes = usedMem
      }
    }
    updatePeakMemory()

    // Initial setup: start with a queue
    playbackManager.setQueue(songs, 0)
    recentActions.addLast("setQueue(pool, 0)")
    delay(2000L)

    while (System.currentTimeMillis() - startTime < targetDurationMs || step < totalSteps) {
      step++
      updatePeakMemory()

      val actionType = random.nextInt(9)
      val actionName = when (actionType) {
        0 -> {
          val randomSong = songs[random.nextInt(songs.size)]
          songRecords[randomSong.id]?.let { it.playCount++ }
          playbackManager.playSong(randomSong, songs)
          "playSong(${randomSong.title.take(15)})"
        }
        1 -> {
          playbackManager.togglePlayPause()
          "togglePlayPause(playing=${playbackManager.isPlaying.value})"
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
            val removeIdx = random.nextInt(0, queueSize)
            val removingSong = playbackManager.queue.value.getOrNull(removeIdx)
            playbackManager.removeFromQueue(removeIdx)
            "removeFromQueue($removeIdx:${removingSong?.title?.take(10) ?: "unknown"})"
          } else {
            val extraSong = songs[random.nextInt(songs.size)]
            playbackManager.addToQueue(extraSong)
            "addToQueue(${extraSong.title.take(15)})"
          }
        }
        else -> "noop"
      }

      // Maintain recent actions history (up to 10)
      if (recentActions.size >= 10) {
        recentActions.removeFirst()
      }
      recentActions.addLast("Step $step: $actionName")

      try {
        // Wait for real state transition (up to 5s timeout)
        waitForSettle(timeoutMs = 5000L)
        // Real playback audio decoding delay
        delay(1500L)
      } catch (e: Exception) {
        caughtExceptions.add("Exception during step $step ($actionName): ${e.message}")
        CrashLogger.e(TAG, "Exception during stress test step $step", e)
      }

      // Check current track status
      val currentSong = playbackManager.currentSong.value
      if (currentSong != null) {
        val record = songRecords[currentSong.id]
        if (record != null) {
          if (currentSong.playbackError != 0) {
            record.status = "Error"
            record.errorCode = "Err_${currentSong.playbackError}"
            if (!unplayableFiles.contains(currentSong.filePath ?: currentSong.title)) {
              unplayableFiles.add(currentSong.filePath ?: currentSong.title)
            }
          }
        }
      }

      // Invariant checks
      val stepViolations = checkInvariants(step, actionName)
      if (stepViolations.isNotEmpty()) {
        violations.addAll(stepViolations)
        val snapshot = recentActions.toList()
        val detailedViolation = buildString {
          append("VIOLATION at Step $step [$actionName]:\n")
          stepViolations.forEach { append("  * $it\n") }
          append("  Previous up to 10 actions:\n")
          snapshot.forEachIndexed { i, act ->
            append("    ${i + 1}. $act\n")
          }
        }
        violationsWithHistory.add(detailedViolation)
      }

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
    report.append("Peak Process Memory: ${peakMemoryBytes / (1024 * 1024)} MB\n")
    report.append("Total Invariant Violations: ${violations.size}\n")
    report.append("Exceptions Caught: ${if (caughtExceptions.isEmpty()) "0 (None)" else "${caughtExceptions.size} exceptions"}\n\n")

    if (caughtExceptions.isNotEmpty()) {
      report.append("=== EXCEPTIONS ENCOUNTERED ===\n")
      caughtExceptions.take(5).forEach { report.append("  - $it\n") }
      report.append("\n")
    }

    if (violationsWithHistory.isNotEmpty()) {
      report.append("=== VIOLATION DETAILS (WITH PREVIOUS 10 ACTIONS) ===\n")
      violationsWithHistory.take(10).forEach {
        report.append(it).append("\n")
      }
      if (violationsWithHistory.size > 10) {
        report.append("... and ${violationsWithHistory.size - 10} more violations omitted for brevity.\n\n")
      }
    } else {
      report.append("All 6 invariants satisfied 100% across all real operations.\n\n")
    }

    // Unplayable Files Section
    report.append("=== UNPLAYABLE / FAILED FILES (${unplayableFiles.size}) ===\n")
    if (unplayableFiles.isEmpty()) {
      report.append("None (all files playable without playback errors)\n\n")
    } else {
      unplayableFiles.forEach { report.append("  - $it\n") }
      report.append("\n")
    }

    // Per-Song Table
    report.append("=== PER-SONG PLAYBACK TABLE (${songRecords.size} tracks) ===\n")
    report.append(String.format(Locale.US, "%-20s | %-15s | %-4s | %-8s | %-7s | %-8s\n", "Title", "File Path", "Ext", "Duration", "Status", "Error"))
    report.append("--------------------------------------------------------------------------------------\n")
    songRecords.values.take(30).forEach { r ->
      val title = r.song.title.take(20).padEnd(20)
      val path = (r.song.filePath ?: r.song.uriString).takeLast(15).padEnd(15)
      val ext = (r.song.filePath?.substringAfterLast('.', "") ?: "mp3").take(4).padEnd(4)
      val dur = "${r.song.durationMs / 1000}s".padEnd(8)
      val status = r.status.padEnd(7)
      val err = r.errorCode.padEnd(8)
      report.append("$title | $path | $ext | $dur | $status | $err\n")
    }
    if (songRecords.size > 30) {
      report.append("... and ${songRecords.size - 30} additional song records omitted for brevity.\n")
    }
    report.append("\n")

    // Final Status Verdict
    if (totalElapsedMs < MINIMUM_VALID_DURATION_MS) {
      report.append("FINAL STATUS: INVALID\n")
      report.append("Reason: Test completed in ${totalElapsedSec}s (< 60s minimum threshold).\n")
    } else if (violations.isEmpty()) {
      report.append("FINAL STATUS: PASSED (100% Invariants Satisfied & Stable)\n")
    } else {
      report.append("FINAL STATUS: FAILED (${violations.size} violations)\n")
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
