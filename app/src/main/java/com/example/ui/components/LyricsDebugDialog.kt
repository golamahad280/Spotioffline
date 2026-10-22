package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.lyrics.LyricsDebugInfo
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyRedHeart
import com.example.ui.theme.SpotifyTextMuted
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite
import kotlinx.coroutines.launch

@Composable
fun LyricsDebugDialog(
  currentSong: Song?,
  debugInfo: LyricsDebugInfo,
  onRetryFetch: (Song) -> Unit,
  onRunSelfTest: suspend () -> Map<String, Boolean>,
  onRunSetFireToTheRainTest: suspend () -> Pair<Boolean, LyricsDebugInfo>,
  onDismiss: () -> Unit
) {
  val scope = rememberCoroutineScope()
  var isRunningSelfTest by remember { mutableStateOf(false) }
  var selfTestResults by remember { mutableStateOf<Map<String, Boolean>?>(null) }

  var isRunningSetFireTest by remember { mutableStateOf(false) }
  var setFireTestResult by remember { mutableStateOf<Pair<Boolean, LyricsDebugInfo>?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Lyrics Pipeline Debug",
            color = SpotifyWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SpotifyWhite)
        }
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("lyrics_debug_dialog"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Song Metadata Box (displays requested songId, exact title/artist/album/duration)
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(SpotifyBlack)
              .padding(12.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "REQUESTED TRACK DATA (FROM ROOM DB)",
                color = SpotifyTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
              Text(
                text = "Song ID: ${debugInfo.songId.ifBlank { currentSong?.id ?: "None" }}",
                color = SpotifyWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Exact Title: ${debugInfo.rawTitle.ifBlank { currentSong?.title ?: "None selected" }}",
                color = SpotifyWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "Exact Artist: ${debugInfo.rawArtist.ifBlank { currentSong?.artist ?: "None" }}",
                color = SpotifyWhite,
                fontSize = 13.sp
              )
              Text(
                text = "Cleaned Title: ${debugInfo.cleanedTitle.ifBlank { currentSong?.title ?: "-" }}",
                color = SpotifyGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
              Text(
                text = "Cleaned Artist: ${debugInfo.cleanedArtist.ifBlank { currentSong?.artist ?: "-" }}",
                color = SpotifyGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
              if (!debugInfo.identifiedArtist.isNullOrBlank()) {
                Text(
                  text = "Identified Artist (iTunes): ${debugInfo.identifiedArtist}",
                  color = SpotifyGreen,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              Text(
                text = "Album: ${debugInfo.album.ifBlank { currentSong?.album ?: "-" }}",
                color = SpotifyTextSecondary,
                fontSize = 12.sp
              )
              Text(
                text = "Duration: ${(if (debugInfo.durationMs > 0) debugInfo.durationMs else (currentSong?.durationMs ?: 0L)) / 1000}s (${if (debugInfo.durationMs > 0) debugInfo.durationMs else (currentSong?.durationMs ?: 0L)} ms)",
                color = SpotifyTextSecondary,
                fontSize = 12.sp
              )
              Text(
                text = "Pipeline Result: ${debugInfo.finalResult.ifBlank { currentSong?.lyricsStatus ?: "IDLE" }}",
                color = when {
                  debugInfo.finalResult.contains("SYNCED") || currentSong?.lyricsStatus == "READY" -> SpotifyGreen
                  debugInfo.finalResult.contains("ERROR") -> SpotifyRedHeart
                  debugInfo.finalResult.contains("SKIPPED") -> SpotifyTextSecondary
                  debugInfo.finalResult == "NOT_FOUND" -> SpotifyRedHeart
                  debugInfo.finalResult == "FETCHING" -> SpotifyWhite
                  else -> SpotifyTextMuted
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
              if (!debugInfo.finalSource.isNullOrBlank()) {
                Text(
                  text = "Source: ${debugInfo.finalSource}",
                  color = SpotifyGreen,
                  fontSize = 12.sp
                )
              }
              if (!debugInfo.errorDetail.isNullOrBlank()) {
                Text(
                  text = "Error Detail: ${debugInfo.errorDetail}",
                  color = SpotifyRedHeart,
                  fontSize = 11.sp
                )
              }
            }
          }
        }

        // Action Buttons: Fetch Now, Set Fire to the Rain Test & Self Test
        item {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              if (currentSong != null) {
                Button(
                  onClick = { onRetryFetch(currentSong) },
                  colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = SpotifyBlack, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Fetch Now", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }

              OutlinedButton(
                onClick = {
                  scope.launch {
                    isRunningSetFireTest = true
                    setFireTestResult = onRunSetFireToTheRainTest()
                    isRunningSetFireTest = false
                  }
                },
                modifier = Modifier.weight(1.3f)
              ) {
                if (isRunningSetFireTest) {
                  CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SpotifyGreen, strokeWidth = 2.dp)
                  Spacer(modifier = Modifier.width(4.dp))
                }
                Text("Test Set Fire to Rain", color = SpotifyWhite, fontSize = 11.sp)
              }
            }

            OutlinedButton(
              onClick = {
                scope.launch {
                  isRunningSelfTest = true
                  selfTestResults = onRunSelfTest()
                  isRunningSelfTest = false
                }
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              if (isRunningSelfTest) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SpotifyGreen, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
              }
              Text("Run 5-Stage Self-Test (Blinding Lights)", color = SpotifyTextSecondary, fontSize = 11.sp)
            }
          }
        }

        // Set Fire to the Rain Verification Test Box (Mandatory Test Requirement)
        if (setFireTestResult != null) {
          item {
            val (passed, info) = setFireTestResult!!
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyBlack)
                .border(1.dp, if (passed) SpotifyGreen else SpotifyRedHeart, RoundedCornerShape(8.dp))
                .padding(12.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "VERIFICATION TEST: Set_Fire_to_the_Rain(128k)",
                    color = SpotifyWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = if (passed) "PASS" else "FAIL",
                    color = if (passed) SpotifyGreen else SpotifyRedHeart,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                  )
                }
                HorizontalDivider(color = SpotifyDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                Text("Input: Set_Fire_to_the_Rain(128k) (Unknown Artist, ShareMe, 242s)", color = SpotifyTextSecondary, fontSize = 11.sp)
                Text("Cleaned Title: ${info.cleanedTitle}", color = if (info.cleanedTitle.equals("Set Fire to the Rain", true)) SpotifyGreen else SpotifyRedHeart, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Identified Artist: ${info.cleanedArtist}", color = if (info.cleanedArtist.contains("Adele", true)) SpotifyGreen else SpotifyRedHeart, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Duration: ${info.durationSec}s", color = if (info.durationSec == 242) SpotifyGreen else SpotifyRedHeart, fontSize = 12.sp)
                Text("Lyrics Result: ${info.finalResult} (${info.finalSource ?: "Not found"})", color = if (passed) SpotifyGreen else SpotifyRedHeart, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        // Top Candidates Box (if candidates found from LRCLIB search)
        if (debugInfo.candidates.isNotEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyBlack)
                .padding(10.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "LRCLIB TOP CANDIDATES FOUND (${debugInfo.candidates.size})",
                  color = SpotifyTextSecondary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
                debugInfo.candidates.forEachIndexed { idx, candidate ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "${idx + 1}. ${candidate.trackName} - ${candidate.artistName} (${candidate.durationSec}s)",
                      color = SpotifyWhite,
                      fontSize = 11.sp,
                      modifier = Modifier.weight(1f)
                    )
                    Text(
                      text = if (candidate.hasSynced) "Synced" else if (candidate.hasPlain) "Plain" else "-",
                      color = if (candidate.hasSynced) SpotifyGreen else SpotifyTextMuted,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }
            }
          }
        }

        // Self-Test Results display (Blinding Lights)
        if (selfTestResults != null) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyBlack)
                .padding(12.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = "SELF-TEST STAGES (Blinding Lights - The Weeknd)",
                  color = SpotifyGreen,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
                selfTestResults!!.forEach { (stage, passed) ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(text = stage, color = SpotifyWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (passed) SpotifyGreen else SpotifyRedHeart,
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = if (passed) "PASS" else "FAIL",
                        color = if (passed) SpotifyGreen else SpotifyRedHeart,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Trace of each step attempted
        item {
          Text(
            text = "STEP-BY-STEP SOURCES & REQUEST TRACE",
            color = SpotifyTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp)
          )
        }

        if (debugInfo.steps.isEmpty()) {
          item {
            Text(
              text = "No request trace yet. Tap 'Fetch Now' to execute the identification & LRCLIB cascade.",
              color = SpotifyTextMuted,
              fontSize = 12.sp,
              modifier = Modifier.padding(vertical = 8.dp)
            )
          }
        } else {
          items(debugInfo.steps) { step ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyBlack)
                .padding(10.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = step.stepName,
                    color = SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                  Text(
                    text = if (step.httpStatus != null) "HTTP ${step.httpStatus}" else "Notice",
                    color = when (step.httpStatus) {
                      in 200..299 -> SpotifyGreen
                      404 -> SpotifyTextSecondary
                      null -> SpotifyTextMuted
                      else -> SpotifyRedHeart
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                  )
                }
                if (step.url.isNotBlank()) {
                  Text(
                    text = step.url,
                    color = SpotifyTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                  )
                }
                Text(
                  text = "Summary: ${step.summary}",
                  color = when (step.httpStatus) {
                    in 200..299 -> SpotifyGreen
                    404 -> SpotifyTextSecondary
                    null -> SpotifyWhite
                    else -> SpotifyRedHeart
                  },
                  fontSize = 11.sp
                )
                if (!step.responseSnippet.isNullOrBlank() && step.responseSnippet != "OK") {
                  Text(
                    text = "Response snippet: ${step.responseSnippet}",
                    color = SpotifyTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Done", color = SpotifyWhite)
      }
    },
    containerColor = SpotifyCardElevated
  )
}
