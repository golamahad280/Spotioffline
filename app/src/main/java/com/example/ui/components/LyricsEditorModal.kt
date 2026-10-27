package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Song
import com.example.ui.theme.*
import com.example.utils.LrcParser
import kotlinx.coroutines.launch

@Composable
fun LyricsEditorModal(
  song: Song,
  onSave: (String, Boolean) -> Unit,
  onAutoSyncAi: (suspend (Song, String) -> String?)?,
  onRefetchOnline: (Song) -> Unit,
  onSearchOtherVersions: () -> Unit,
  onRestoreOriginal: () -> Unit,
  onDeleteLyrics: () -> Unit,
  onSaveEnglish: (String?) -> Unit,
  onDismiss: () -> Unit,
  onOpenTapToSync: (String) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Text, 1: LRC (raw), 2: English

  // Text tab view shows clean lines without timestamps
  var textTabContent by remember(song.id) {
    mutableStateOf(LrcParser.stripTimestamps(song.lyricsText))
  }

  // LRC tab shows raw LRC
  var lrcTabContent by remember(song.id) {
    mutableStateOf(song.lyricsText ?: "")
  }

  // English tab shows enText
  var englishTabContent by remember(song.id) {
    mutableStateOf(song.lyricsEnText ?: "")
  }

  var isAiProcessing by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .testTag("lyrics_editor_modal"),
      color = SpotifyBlack
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.statusBars)
          .padding(16.dp),
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
              Icon(Icons.Default.Close, contentDescription = "Close", tint = SpotifyWhite)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Lyrics Editor", color = SpotifyWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
              Text(song.title, color = SpotifyTextSecondary, fontSize = 12.sp)
            }
            Button(
              onClick = {
                val contentToSave = when (selectedTab) {
                  0 -> textTabContent // Save as plain text or convert
                  1 -> lrcTabContent
                  2 -> lrcTabContent // Keep main unchanged when editing English
                  else -> lrcTabContent
                }
                val hasTs = LrcParser.hasLrcTimestamps(contentToSave)
                onSave(contentToSave, hasTs)
                if (selectedTab == 2) {
                  onSaveEnglish(englishTabContent)
                }
                onDismiss()
              },
              colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
              Text("Save", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Tabs: Text | LRC (raw) | English
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            TabButton("Text", selectedTab == 0) { selectedTab = 0 }
            TabButton("LRC (raw)", selectedTab == 1) { selectedTab = 1 }
            TabButton("English", selectedTab == 2) { selectedTab = 2 }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Editor Input
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(SpotifyCardElevated, RoundedCornerShape(12.dp))
            .padding(12.dp)
        ) {
          when (selectedTab) {
            0 -> {
              OutlinedTextField(
                value = textTabContent,
                onValueChange = {
                  textTabContent = it
                  lrcTabContent = it // sync
                },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("Enter or paste clean lyrics lines without timestamps...", color = SpotifyTextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = SpotifyGreen,
                  unfocusedBorderColor = SpotifyDivider,
                  focusedTextColor = SpotifyWhite,
                  unfocusedTextColor = SpotifyWhite
                )
              )
            }
            1 -> {
              OutlinedTextField(
                value = lrcTabContent,
                onValueChange = {
                  lrcTabContent = it
                  textTabContent = LrcParser.stripTimestamps(it)
                },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("[00:12.34] Paste raw LRC timestamps here...", color = SpotifyTextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = SpotifyGreen,
                  unfocusedBorderColor = SpotifyDivider,
                  focusedTextColor = SpotifyWhite,
                  unfocusedTextColor = SpotifyWhite
                )
              )
            }
            2 -> {
              OutlinedTextField(
                value = englishTabContent,
                onValueChange = { englishTabContent = it },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("Paste English translation or romanized lyrics here...", color = SpotifyTextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = SpotifyGreen,
                  unfocusedBorderColor = SpotifyDivider,
                  focusedTextColor = SpotifyWhite,
                  unfocusedTextColor = SpotifyWhite
                )
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Always-Visible Actions Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                val plain = textTabContent.ifBlank { LrcParser.stripTimestamps(lrcTabContent) }
                onOpenTapToSync(plain)
              },
              colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
              modifier = Modifier.weight(1f)
            ) {
              Text("Tap to sync", color = SpotifyBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (onAutoSyncAi != null) {
              Button(
                onClick = {
                  isAiProcessing = true
                  scope.launch {
                    try {
                      val plain = textTabContent.ifBlank { LrcParser.stripTimestamps(lrcTabContent) }
                      val synced = onAutoSyncAi(song, plain)
                      isAiProcessing = false
                      if (synced != null) {
                        lrcTabContent = synced
                        textTabContent = LrcParser.stripTimestamps(synced)
                        Toast.makeText(context, "Auto-sync (AI) succeeded!", Toast.LENGTH_SHORT).show()
                      } else {
                        Toast.makeText(context, "Auto-sync (AI) failed. Check API key.", Toast.LENGTH_LONG).show()
                      }
                    } catch (e: Exception) {
                      isAiProcessing = false
                      Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyCardElevated),
                modifier = Modifier.weight(1f),
                enabled = !isAiProcessing
              ) {
                Text(if (isAiProcessing) "Syncing..." else "Auto-sync (AI)", color = SpotifyWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                val stripped = LrcParser.stripTimestamps(lrcTabContent)
                lrcTabContent = stripped
                textTabContent = stripped
                Toast.makeText(context, "Timestamps removed", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.weight(1f)
            ) {
              Text("Remove timestamps", color = SpotifyWhite, fontSize = 11.sp)
            }

            OutlinedButton(
              onClick = { onRefetchOnline(song) },
              modifier = Modifier.weight(1f)
            ) {
              Text("Re-fetch online", color = SpotifyWhite, fontSize = 11.sp)
            }

            OutlinedButton(
              onClick = onSearchOtherVersions,
              modifier = Modifier.weight(1f)
            ) {
              Text("Search versions", color = SpotifyWhite, fontSize = 11.sp)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                onRestoreOriginal()
                Toast.makeText(context, "Restored original lyrics", Toast.LENGTH_SHORT).show()
                onDismiss()
              },
              modifier = Modifier.weight(1f)
            ) {
              Text("Restore original", color = SpotifyWhite, fontSize = 11.sp)
            }

            OutlinedButton(
              onClick = {
                onDeleteLyrics()
                Toast.makeText(context, "Lyrics deleted", Toast.LENGTH_SHORT).show()
                onDismiss()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D1F1F)),
              modifier = Modifier.weight(1f)
            ) {
              Text("Delete lyrics", color = Color(0xFFFF5252), fontSize = 11.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RowScope.TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(
      containerColor = if (isSelected) SpotifyGreen else SpotifyCardElevated
    ),
    modifier = Modifier.weight(1f)
  ) {
    Text(
      text = text,
      color = if (isSelected) SpotifyBlack else SpotifyWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 12.sp
    )
  }
}
