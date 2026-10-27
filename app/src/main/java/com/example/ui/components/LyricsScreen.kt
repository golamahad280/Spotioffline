package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.ui.theme.*
import com.example.utils.ImageUtils
import com.example.utils.LrcParser
import com.example.utils.ScriptDetector
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
  onRestoreOriginal: (() -> Unit)? = null,
  onDeleteLyrics: (() -> Unit)? = null,
  onSaveEnglish: ((String?) -> Unit)? = null,
  onGenerateEnglish: (suspend (String, Boolean, String) -> String)? = null,
  modifier: Modifier = Modifier
) {
  if (song == null) return

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val dominantColor = remember(song.id) {
    ImageUtils.getFallbackColorForTrack(song.title)
  }

  val listState = rememberLazyListState()
  var showOffsetControls by remember { mutableStateOf(false) }
  var showEditorModal by remember { mutableStateOf(false) }
  var showTapToSync by remember { mutableStateOf(false) }
  var showSearchVersions by remember { mutableStateOf(false) }
  var showEnglishModeChoice by remember { mutableStateOf(false) }

  var viewMode by remember(song.id) { mutableStateOf("ORIGINAL") } // "ORIGINAL" or "ENGLISH"
  var tapToSyncPlainLines by remember { mutableStateOf<List<String>>(emptyList()) }

  var currentOffset by remember(song.syncOffsetMs) {
    mutableFloatStateOf(song.syncOffsetMs.toFloat())
  }

  val displayLyricsText = if (viewMode == "ENGLISH" && !song.lyricsEnText.isNullOrBlank()) {
    song.lyricsEnText
  } else {
    song.lyricsText
  }

  val displayLines = remember(displayLyricsText) {
    LrcParser.parse(displayLyricsText)
  }

  val activeEnIndex = remember(displayLines, currentPositionMs, currentOffset) {
    LrcParser.findActiveLineIndex(displayLines, currentPositionMs, currentOffset.toLong())
  }

  val activeIdx = if (viewMode == "ENGLISH") activeEnIndex else activeLyricIndex

  LaunchedEffect(activeIdx) {
    if (activeIdx >= 0 && displayLines.isNotEmpty()) {
      listState.animateScrollToItem(
        index = (activeIdx - 2).coerceAtLeast(0)
      )
    }
  }

  if (showTapToSync) {
    TapToSyncTool(
      song = song,
      plainLines = tapToSyncPlainLines,
      currentPositionMs = currentPositionMs,
      isPlaying = isPlaying,
      onPlayPause = onPlayPause,
      onSeekTo = onSeekTo,
      onFinishSync = { syncedLrc ->
        onSaveManualLyrics(syncedLrc, true)
        showTapToSync = false
        showEditorModal = false
      },
      onDismiss = { showTapToSync = false }
    )
    return
  }

  if (showSearchVersions) {
    SearchOtherVersionsDialog(
      song = song,
      onSelectCandidate = { lrcText, isSynced ->
        onSaveManualLyrics(lrcText, isSynced)
        showSearchVersions = false
        Toast.makeText(context, "Applied selected version", Toast.LENGTH_SHORT).show()
      },
      onDismiss = { showSearchVersions = false }
    )
  }

  if (showEditorModal) {
    LyricsEditorModal(
      song = song,
      onSave = { text, isSynced ->
        onSaveManualLyrics(text, isSynced)
        if (!isSynced) {
          scope.launch {
            snackbarHostState.showSnackbar(
              message = "Lyrics saved, not synced yet",
              actionLabel = "Tap to sync",
              duration = SnackbarDuration.Long
            ).let { result ->
              if (result == SnackbarResult.ActionPerformed) {
                tapToSyncPlainLines = text.lines().filter { it.isNotBlank() }
                showTapToSync = true
              }
            }
          }
        }
      },
      onAutoSyncAi = onAutoSyncWithAi,
      onRefetchOnline = { s -> onRetryFetch?.invoke(s) },
      onSearchOtherVersions = {
        showEditorModal = false
        showSearchVersions = true
      },
      onRestoreOriginal = { onRestoreOriginal?.invoke() },
      onDeleteLyrics = { onDeleteLyrics?.invoke() },
      onSaveEnglish = { enText -> onSaveEnglish?.invoke(enText) },
      onDismiss = { showEditorModal = false },
      onOpenTapToSync = { plainText ->
        tapToSyncPlainLines = plainText.lines().filter { it.isNotBlank() }
        showTapToSync = true
      }
    )
  }

  if (showEnglishModeChoice) {
    AlertDialog(
      onDismissRequest = { showEnglishModeChoice = false },
      title = { Text("English View Mode", color = SpotifyWhite) },
      text = { Text("Choose how you want English lyrics generated:", color = SpotifyTextSecondary, fontSize = 13.sp) },
      confirmButton = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              showEnglishModeChoice = false
              scope.launch {
                try {
                  val textToTranslate = song.lyricsText ?: ""
                  val isSynced = song.isSyncedLyrics
                  val res = onGenerateEnglish?.invoke(textToTranslate, isSynced, "TRANSLATION")
                  if (!res.isNullOrBlank()) {
                    viewMode = "ENGLISH"
                    Toast.makeText(context, "Translation generated successfully!", Toast.LENGTH_SHORT).show()
                  }
                } catch (e: Exception) {
                  Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Translation to English", color = SpotifyBlack, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = {
              showEnglishModeChoice = false
              scope.launch {
                try {
                  val textToTranslate = song.lyricsText ?: ""
                  val isSynced = song.isSyncedLyrics
                  val res = onGenerateEnglish?.invoke(textToTranslate, isSynced, "ROMANIZED")
                  if (!res.isNullOrBlank()) {
                    viewMode = "ENGLISH"
                    Toast.makeText(context, "Romanized lyrics generated successfully!", Toast.LENGTH_SHORT).show()
                  }
                } catch (e: Exception) {
                  Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
              }
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Romanized (Latin letters)", color = SpotifyWhite)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showEnglishModeChoice = false }) {
          Text("Cancel", color = SpotifyWhite)
        }
      },
      containerColor = SpotifyCardElevated
    )
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = song.title,
                color = SpotifyWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              ScriptDetector.getScriptBadge(song.lyricsScript)?.let { badge ->
                Surface(
                  color = Color(0xFF2E3830),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(
                    text = badge,
                    color = SpotifyGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                  )
                }
              }
            }
            Text(
              text = song.artist,
              color = SpotifyWhite.copy(alpha = 0.7f),
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Row {
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

            IconButton(
              onClick = { showEditorModal = true },
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

        // Sync Offset Control Panel
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

        // Status Badge & English / Original View Mode Chip Row
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
            displayLines.isNotEmpty() -> "Synced lyrics ✓ (tap line to seek)"
            !displayLyricsText.isNullOrBlank() -> "Lyrics not synced"
            else -> "Searching lyrics..."
          }

          Text(
            text = badgeText,
            color = if (isAi) Color(0xFFFFB74D) else SpotifyWhite.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )

          // Original | English chip toggle row
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(SpotifyCardElevated)
              .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            ChipButton("Original", viewMode == "ORIGINAL") {
              viewMode = "ORIGINAL"
            }
            ChipButton("English", viewMode == "ENGLISH") {
              if (song.lyricsEnText.isNullOrBlank()) {
                showEnglishModeChoice = true
              } else {
                viewMode = "ENGLISH"
              }
            }
          }
        }

        // Sync Banner
        if (!song.isSyncedLyrics && !displayLyricsText.isNullOrBlank() && displayLines.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clickable {
                        tapToSyncPlainLines = displayLyricsText.lines().filter { it.isNotBlank() }
                        showTapToSync = true
                    },
                color = SpotifyGreen,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Sync these lyrics (Tap to sync)",
                    color = SpotifyBlack,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Lyrics Content
        if (displayLines.isNotEmpty()) {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .testTag("lyrics_list"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
          ) {
            itemsIndexed(displayLines) { index, line ->
              val isActive = index == activeIdx
              val isPast = index < activeIdx
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
            if (!displayLyricsText.isNullOrBlank()) {
              Text(
                text = displayLyricsText,
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
                  text = "We search online across LRCLIB and NetEase automatically. You can try searching other versions, view live debug trace, or paste/generate lyrics.",
                  color = SpotifyTextSecondary,
                  fontSize = 13.sp,
                  lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

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
                    onClick = { showEditorModal = true },
                    modifier = Modifier.weight(1f)
                  ) {
                    Text("Paste manually", color = SpotifyWhite, fontSize = 12.sp)
                  }
                }

                OutlinedButton(
                  onClick = { showSearchVersions = true },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text("Search other versions", color = SpotifyGreen, fontSize = 12.sp)
                }

                if (onOpenDebug != null) {
                  OutlinedButton(
                    onClick = onOpenDebug,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text("View Lyrics Debug Trace", color = SpotifyWhite, fontSize = 12.sp)
                  }
                }
              }
            }
          }
        }
      }
        SnackbarHost(
          hostState = snackbarHostState,
          modifier = Modifier.align(Alignment.BottomCenter)
        )
      }
    }
  }

@Composable
private fun ChipButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    color = if (isSelected) SpotifyGreen else Color.Transparent,
    shape = RoundedCornerShape(14.dp)
  ) {
    Text(
      text = text,
      color = if (isSelected) SpotifyBlack else SpotifyWhite,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
    )
  }
}
