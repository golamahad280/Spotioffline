package com.example.ui.components

import androidx.compose.foundation.background
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
  onDismiss: () -> Unit
) {
  val scope = rememberCoroutineScope()
  var isRunningSelfTest by remember { mutableStateOf(false) }
  var selfTestResults by remember { mutableStateOf<Map<String, Boolean>?>(null) }

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
        // Song Metadata Box
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
                text = "SELECTED / CURRENT TRACK",
                color = SpotifyTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
              Text(
                text = "Title: ${currentSong?.title ?: "None selected"}",
                color = SpotifyWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "Artist: ${currentSong?.artist ?: "None"}",
                color = SpotifyWhite,
                fontSize = 13.sp
              )
              Text(
                text = "Cleaned Title: ${debugInfo.cleanedTitle.ifBlank { currentSong?.title ?: "-" }}",
                color = SpotifyTextSecondary,
                fontSize = 12.sp
              )
              Text(
                text = "Cleaned Artist: ${debugInfo.cleanedArtist.ifBlank { currentSong?.artist ?: "-" }}",
                color = SpotifyTextSecondary,
                fontSize = 12.sp
              )
              Text(
                text = "Duration: ${(currentSong?.durationMs ?: 0L) / 1000}s",
                color = SpotifyTextSecondary,
                fontSize = 12.sp
              )
              Text(
                text = "Status: ${currentSong?.lyricsStatus ?: debugInfo.finalResult}",
                color = when (currentSong?.lyricsStatus ?: debugInfo.finalResult) {
                  "READY", "SYNCED" -> SpotifyGreen
                  "NOT_FOUND" -> SpotifyRedHeart
                  "FETCHING" -> SpotifyWhite
                  else -> SpotifyTextMuted
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
              if (!currentSong?.lyricsSource.isNullOrBlank()) {
                Text(
                  text = "Source: ${currentSong?.lyricsSource}",
                  color = SpotifyGreen,
                  fontSize = 12.sp
                )
              }
            }
          }
        }

        // Action Buttons: Fetch Now & Self Test
        item {
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
                  isRunningSelfTest = true
                  selfTestResults = onRunSelfTest()
                  isRunningSelfTest = false
                }
              },
              modifier = Modifier.weight(1.3f)
            ) {
              if (isRunningSelfTest) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SpotifyGreen, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
              }
              Text("Self-Test (Blinding Lights)", color = SpotifyWhite, fontSize = 11.sp)
            }
          }
        }

        // Self-Test Results display (Step 7)
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
            modifier = Modifier.padding(top = 8.dp)
          )
        }

        if (debugInfo.steps.isEmpty()) {
          item {
            Text(
              text = "No request trace yet. Tap 'Fetch Now' to execute the 5-step LRCLIB cascade.",
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
                    text = if (step.httpStatus != null) "HTTP ${step.httpStatus}" else "Failed",
                    color = if (step.httpStatus in 200..299) SpotifyGreen else SpotifyRedHeart,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                  )
                }
                Text(
                  text = step.url,
                  color = SpotifyTextSecondary,
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace,
                  lineHeight = 14.sp
                )
                Text(
                  text = "Summary: ${step.summary}",
                  color = if (step.httpStatus in 200..299) SpotifyGreen else SpotifyTextMuted,
                  fontSize = 11.sp
                )
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
