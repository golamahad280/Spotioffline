package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LrcLine
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCard
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyTextMuted
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite
import com.example.utils.LrcParser
import java.util.Locale

@Composable
fun TapToSyncTool(
  song: Song,
  plainLines: List<String>,
  currentPositionMs: Long,
  isPlaying: Boolean,
  onPlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onFinishSync: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val lines = remember(plainLines) { plainLines.filter { it.isNotBlank() } }
  var currentLineIdx by remember { mutableIntStateOf(0) }
  val stampedLines = remember { mutableStateListOf<LrcLine>() }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .testTag("tap_to_sync_screen"),
      color = SpotifyBlack
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Top Bar
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = onDismiss) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SpotifyWhite)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Tap to Sync", color = SpotifyWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
              Text(
                text = "${song.title} • ${formatMs(currentPositionMs)}",
                color = SpotifyTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Button(
              onClick = {
                if (stampedLines.isNotEmpty()) {
                  val formatted = LrcParser.formatToLrc(stampedLines.toList())
                  onFinishSync(formatted)
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
              enabled = stampedLines.isNotEmpty()
            ) {
              Text("Finish", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          val progress = if (lines.isNotEmpty()) (currentLineIdx.toFloat() / lines.size).coerceIn(0f, 1f) else 0f
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(2.dp)),
            color = SpotifyGreen,
            trackColor = SpotifyDivider
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Line ${currentLineIdx.coerceAtMost(lines.size)} of ${lines.size} synced",
            color = SpotifyTextMuted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End)
          )
        }

        // Lyrics Line Viewer Card
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(vertical = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SpotifyCardElevated)
            .padding(20.dp),
          contentAlignment = Alignment.Center
        ) {
          if (currentLineIdx < lines.size) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              Text(
                text = "CURRENT LINE",
                color = SpotifyGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
              Text(
                text = lines[currentLineIdx],
                color = SpotifyWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
              )

              if (currentLineIdx + 1 < lines.size) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "UP NEXT:",
                  color = SpotifyTextMuted,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = lines[currentLineIdx + 1],
                  color = SpotifyTextSecondary,
                  fontSize = 15.sp,
                  textAlign = TextAlign.Center
                )
              }
              if (currentLineIdx + 2 < lines.size) {
                Text(
                  text = lines[currentLineIdx + 2],
                  color = SpotifyTextMuted,
                  fontSize = 13.sp,
                  textAlign = TextAlign.Center
                )
              }
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text("✓ All Lines Synced!", color = SpotifyGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
              Text(
                text = "Tap 'Finish' at the top right to save these synced lyrics permanently to your device.",
                color = SpotifyTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
              )
            }
          }
        }

        // Tap Button & Controls
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          // Big Tap Button
          Button(
            onClick = {
              if (currentLineIdx < lines.size) {
                stampedLines.add(LrcLine(timestampMs = currentPositionMs, text = lines[currentLineIdx]))
                currentLineIdx++
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(72.dp)
              .testTag("tap_next_line_button"),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            shape = RoundedCornerShape(16.dp),
            enabled = currentLineIdx < lines.size
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("TAP NEXT LINE", color = SpotifyBlack, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
              Text("Stamps line at ${formatMs(currentPositionMs)}", color = SpotifyBlack.copy(alpha = 0.7f), fontSize = 11.sp)
            }
          }

          // Auxiliary Controls Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Undo
            OutlinedButton(
              onClick = {
                if (currentLineIdx > 0 && stampedLines.isNotEmpty()) {
                  stampedLines.removeAt(stampedLines.lastIndex)
                  currentLineIdx--
                }
              },
              enabled = currentLineIdx > 0
            ) {
              Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp), tint = SpotifyWhite)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Undo", color = SpotifyWhite, fontSize = 12.sp)
            }

            // Back 5s
            OutlinedButton(
              onClick = {
                val target = (currentPositionMs - 5000L).coerceAtLeast(0L)
                onSeekTo(target)
              }
            ) {
              Icon(Icons.Default.FastRewind, contentDescription = null, modifier = Modifier.size(16.dp), tint = SpotifyWhite)
              Spacer(modifier = Modifier.width(4.dp))
              Text("-5s", color = SpotifyWhite, fontSize = 12.sp)
            }

            // Play / Pause
            IconButton(
              onClick = onPlayPause,
              modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(SpotifyCardElevated)
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = SpotifyWhite
              )
            }

            // Skip line
            OutlinedButton(
              onClick = {
                if (currentLineIdx < lines.size) {
                  currentLineIdx++
                }
              },
              enabled = currentLineIdx < lines.size
            ) {
              Text("Skip", color = SpotifyTextSecondary, fontSize = 12.sp)
              Spacer(modifier = Modifier.width(4.dp))
              Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp), tint = SpotifyTextSecondary)
            }
          }
        }
      }
    }
  }
}

private fun formatMs(ms: Long): String {
  val totalSec = ms / 1000
  val min = totalSec / 60
  val sec = totalSec % 60
  val frac = (ms % 1000) / 100
  return String.format(Locale.US, "%02d:%02d.%01d", min, sec, frac)
}
