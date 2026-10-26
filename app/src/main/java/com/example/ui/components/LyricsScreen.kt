package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LrcLine
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite
import com.example.utils.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LyricsScreen(
  song: Song?,
  lyricsLines: List<LrcLine>,
  activeLyricIndex: Int,
  currentPositionMs: Long,
  isPlaying: Boolean = false,
  onPlayPause: () -> Unit = {},
  onDismiss: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onUpdateOffset: (Long) -> Unit,
  onSaveManualLyrics: (String, Boolean) -> Unit,
  onRetryFetch: ((Song) -> Unit)? = null,
  onOpenDebug: (() -> Unit)? = null,
  onGenerateLyricsWithAi: (suspend (Song) -> Boolean)? = null,
  onAutoSyncWithAi: (suspend (Song, String) -> String?)? = null,
  modifier: Modifier = Modifier
) {
  if (song == null) return

  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val dominantColor = remember(song.id) {
    ImageUtils.getFallbackColorForTrack(song.title)
  }

  val listState = rememberLazyListState()
  var showOffsetControls by remember { mutableStateOf(false) }
  var showEditDialog by remember { mutableStateOf(false) }
  var showTapToSync by remember { mutableStateOf(false) }

  var manualText by remember(song.lyricsText) { mutableStateOf(song.lyricsText ?: "") }
  var isAiGenerating by remember { mutableStateOf(false) }
  var aiJob by remember { mutableStateOf<Job?>(null) }

  var currentOffset by remember(song.syncOffsetMs) {
    mutableFloatStateOf(song.syncOffsetMs.toFloat())
  }

  LaunchedEffect(activeLyricIndex) {
    if (activeLyricIndex >= 0 && lyricsLines.isNotEmpty()) {
      listState.animateScrollToItem(
        index = (activeLyricIndex - 2).coerceAtLeast(0)
      )
    }
  }

  if (showTapToSync) {
    val plainLines = remember(manualText) { manualText.lines() }
    TapToSyncTool(
      song = song,
      plainLines = plainLines,
      currentPositionMs = currentPositionMs,
      isPlaying = isPlaying,
      onPlayPause = onPlayPause,
      onSeekTo = onSeekTo,
      onFinishSync = { syncedLrc ->
        onSaveManualLyrics(syncedLrc, true)
        showTapToSync = false
        showEditDialog = false
      },
      onDismiss = { showTapToSync = false }
    )
    return
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("lyrics_screen"),
    color = SpotifyBlack
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              dominantColor.copy(alpha = 0.85f),
              SpotifyBlack.copy(alpha = 0.95f),
              SpotifyBlack
            )
          )
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.statusBars)
      ) {
        // Top Navigation Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Lyrics",
              tint = SpotifyWhite
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = song.title,
              color = SpotifyWhite,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = song.artist,
              color = SpotifyWhite.copy(alpha = 0.7f),
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Row {
            // Toggle Sync Offset slider
            IconButton(
              onClick = { showOffsetControls = !showOffsetControls },
              modifier = Modifier.testTag("lyrics_sync_toggle")
            ) {
              Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Sync Offset",
                tint = if (showOffsetControls) SpotifyGreen else SpotifyWhite
              )
            }

            // Edit Lyrics
            IconButton(
              onClick = { showEditDialog = true },
              modifier = Modifier.testTag("edit_lyrics_button")
            ) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Lyrics",
                tint = SpotifyWhite
              )
            }
          }
        }

        // Sync Offset Control Panel (if expanded)
        if (showOffsetControls) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 6.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(SpotifyCardElevated)
              .padding(12.dp)
          ) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Sync Offset: ${String.format(Locale.getDefault(), "%+.2fs", currentOffset / 1000f)}",
                  color = SpotifyWhite,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                  currentOffset = 0f
                  onUpdateOffset(0L)
                }) {
                  Text("Reset", color = SpotifyGreen, fontSize = 12.sp)
                }
              }

              Slider(
                value = currentOffset,
                onValueChange = { currentOffset = it },
                onValueChangeFinished = {
                  onUpdateOffset(currentOffset.toLong())
                },
                valueRange = -5000f..5000f,
                colors = SliderDefaults.colors(
                  thumbColor = SpotifyGreen,
                  activeTrackColor = SpotifyGreen,
                  inactiveTrackColor = SpotifyDivider
                )
              )
            }
          }
        }

        // Status Badge
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          val isAi = song.lyricsSource?.contains("AI", ignoreCase = true) == true
          val badgeText = when {
            isAi -> "AI-generated (OpenAI), may contain errors"
            lyricsLines.isNotEmpty() -> "Synced lyrics ✓ (tap line to seek)"
            song.hasLyrics && !song.lyricsText.isNullOrBlank() -> "Lyrics not synced"
            else -> "Searching lyrics..."
          }

          Text(
            text = badgeText,
            color = if (isAi) Color(0xFFFFB74D) else SpotifyWhite.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Lyrics Content
        if (lyricsLines.isNotEmpty()) {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .testTag("lyrics_list"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
          ) {
            itemsIndexed(lyricsLines) { index, line ->
              val isActive = index == activeLyricIndex
              val isPast = index < activeLyricIndex
              val scale by animateFloatAsState(
                targetValue = if (isActive) 1.04f else 1.0f,
                animationSpec = tween(durationMillis = 200),
                label = "lyric_scale"
              )

              val textColor = when {
                isActive -> SpotifyWhite
                isPast -> SpotifyWhite.copy(alpha = 0.55f)
                else -> SpotifyWhite.copy(alpha = 0.35f)
              }

              Text(
                text = line.text,
                color = textColor,
                fontSize = if (isActive) 24.sp else 21.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                lineHeight = 32.sp,
                modifier = Modifier
                  .scale(scale)
                  .clickable { onSeekTo(line.timestampMs) }
                  .padding(vertical = 4.dp)
              )
            }
          }
        } else {
          // Plain Text Lyrics fallback or Empty state
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 24.dp, vertical = 32.dp)
          ) {
            if (!song.lyricsText.isNullOrBlank()) {
              Text(
                text = song.lyricsText,
                color = SpotifyWhite,
                fontSize = 19.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Medium
              )
            } else {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
              ) {
                Text(
                  text = "No lyrics found for this song yet.",
                  color = SpotifyWhite,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "We search online across LRCLIB and embedded tags automatically. You can try searching again, view the live debug request trace, or paste/generate lyrics.",
                  color = SpotifyTextSecondary,
                  fontSize = 13.sp,
                  lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (isAiGenerating) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Transcribing audio via OpenAI AI...", color = SpotifyWhite, fontSize = 13.sp)
                    TextButton(onClick = {
                      aiJob?.cancel()
                      isAiGenerating = false
                    }) {
                      Text("Cancel", color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                  }
                } else {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Button(
                      onClick = { onRetryFetch?.invoke(song) },
                      colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                      modifier = Modifier.weight(1f)
                    ) {
                      Text("Search again", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                      onClick = { showEditDialog = true },
                      modifier = Modifier.weight(1f)
                    ) {
                      Text("Paste manually", color = SpotifyWhite, fontSize = 12.sp)
                    }
                  }

                  if (onGenerateLyricsWithAi != null) {
                    Button(
                      onClick = {
                        isAiGenerating = true
                        aiJob = scope.launch {
                          try {
                            val success = onGenerateLyricsWithAi.invoke(song)
                            isAiGenerating = false
                            if (!success) {
                              Toast.makeText(context, "AI generation failed. Please check key or try again.", Toast.LENGTH_LONG).show()
                            }
                          } catch (e: Exception) {
                            isAiGenerating = false
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                          }
                        }
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = SpotifyDivider),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Generate lyrics from audio (AI)", color = SpotifyWhite, fontSize = 12.sp)
                    }
                  }
                }

                if (onOpenDebug != null) {
                  OutlinedButton(
                    onClick = onOpenDebug,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text("View Lyrics Debug Trace", color = SpotifyGreen, fontSize = 12.sp)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Edit Lyrics Dialog
  if (showEditDialog) {
    val hasTimestamps = manualText.contains(Regex("\\[\\d{1,2}:\\d{2}"))

    AlertDialog(
      onDismissRequest = { showEditDialog = false },
      title = { Text("Edit / Sync Lyrics", color = SpotifyWhite) },
      text = {
        Column {
          Text(
            text = "Paste plain text or synced LRC format [mm:ss.xx]:",
            color = SpotifyTextSecondary,
            fontSize = 12.sp
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = manualText,
            onValueChange = { manualText = it },
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp),
            maxLines = 15
          )

          if (manualText.isNotBlank() && !hasTimestamps) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Pasted text has NO timestamps. Choose sync option:", color = SpotifyWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = { showTapToSync = true },
                modifier = Modifier.weight(1f)
              ) {
                Text("Tap to sync", color = SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }

              if (onAutoSyncWithAi != null) {
                Button(
                  onClick = {
                    isAiGenerating = true
                    aiJob = scope.launch {
                      try {
                        val synced = onAutoSyncWithAi.invoke(song, manualText)
                        isAiGenerating = false
                        if (synced != null) {
                          showEditDialog = false
                        } else {
                          Toast.makeText(context, "Auto-sync with AI failed. Use Tap to sync.", Toast.LENGTH_LONG).show()
                        }
                      } catch (e: Exception) {
                        isAiGenerating = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                      }
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                  modifier = Modifier.weight(1f),
                  enabled = !isAiGenerating
                ) {
                  Text("Auto-sync (AI)", color = SpotifyBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveManualLyrics(manualText, hasTimestamps)
            showEditDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = SpotifyDivider)
        ) {
          Text("Save Text", color = SpotifyWhite, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditDialog = false }) {
          Text("Cancel", color = SpotifyWhite)
        }
      },
      containerColor = SpotifyCardElevated
    )
  }
}
