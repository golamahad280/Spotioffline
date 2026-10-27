package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.Song
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyTextMuted
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite
import com.example.utils.CrashLogger
import com.example.ui.components.*
import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.filled.Notifications
import androidx.core.content.ContextCompat
import com.example.playback.MusicPlaybackService
import com.example.playback.PlaybackManager
import com.example.playback.RealStressTester
import com.example.playback.StressTestProgress
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
  totalSongs: Int,
  hiddenSongs: List<Song> = emptyList(),
  onRestoreSong: (String) -> Unit = {},
  onBack: () -> Unit,
  onScanClick: () -> Unit,
  onClearLyricsCache: () -> Unit,
  onClearCoverCache: () -> Unit,
  onOpenEqualizer: () -> Unit,
  onOpenSleepTimer: () -> Unit,
  onOpenLyricsDebug: () -> Unit,
  availableSongs: List<Song> = emptyList(),
  playbackManager: PlaybackManager? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
  var autoFetchLyrics by remember { mutableStateOf(true) }
  var autoFetchCovers by remember { mutableStateOf(true) }
  var crossfadeEnabled by remember { mutableStateOf(false) }
  var openAiApiKey by remember { mutableStateOf(prefs.getString("openai_api_key", "") ?: "") }
  var openAiChatModel by remember { mutableStateOf(prefs.getString("openai_chat_model", "gpt-4o-mini") ?: "gpt-4o-mini") }
  var openAiTranscriptionModel by remember { mutableStateOf(prefs.getString("openai_transcription_model", "whisper-1") ?: "whisper-1") }
  var autoAiLyrics by remember { mutableStateOf(prefs.getBoolean("auto_ai_lyrics", false)) }
  var isKeyVisible by remember { mutableStateOf(false) }
  var keyTestStatus by remember { mutableStateOf<String?>(null) }

  var showCrashLogsDialog by remember { mutableStateOf(false) }
  var showHiddenSongsDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(SpotifyBlack)
      .padding(horizontal = 16.dp)
      .testTag("settings_screen"),
    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = SpotifyWhite
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Settings",
          color = SpotifyWhite,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // Storage & Library Section
    item {
      SettingsSection("LIBRARY & STORAGE")
      SettingsCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = SpotifyGreen)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Total Offline Audio Tracks", color = SpotifyWhite, fontSize = 14.sp)
          }
          Text("$totalSongs tracks", color = SpotifyGreen, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = SpotifyDivider)

        Button(
          onClick = onScanClick,
          colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = SpotifyBlack)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Re-Scan Device For Songs", color = SpotifyBlack, fontWeight = FontWeight.Bold)
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              onClearLyricsCache()
              Toast.makeText(context, "Lyrics cache cleared", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("Clear Lyrics", color = SpotifyWhite, fontSize = 12.sp)
          }

          OutlinedButton(
            onClick = {
              onClearCoverCache()
              Toast.makeText(context, "Cover cache cleared", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("Clear Covers", color = SpotifyWhite, fontSize = 12.sp)
          }
        }

        HorizontalDivider(color = SpotifyDivider)

        SettingsRow(
          icon = Icons.Default.VisibilityOff,
          title = "Hidden Songs",
          trailing = if (hiddenSongs.isEmpty()) "0 hidden >" else "${hiddenSongs.size} hidden >",
          onClick = { showHiddenSongsDialog = true }
        )
      }
    }

    // Developer Section
    item {
      var showStressTestDialog by remember { mutableStateOf(false) }

      SettingsSection("DEVELOPER")
      SettingsCard {
        SettingsRow(
          icon = Icons.Default.BugReport,
          title = "Crash & Stability Log",
          subtitle = "View stack traces, exit reasons & ring buffer",
          trailing = "View Logs >",
          onClick = { showCrashLogsDialog = true }
        )

        HorizontalDivider(color = SpotifyDivider)

        SettingsRow(
          icon = Icons.Default.AutoAwesome,
          title = "Run Real Stress Test",
          subtitle = ">=3 min run with 100 real actions driving player & real files",
          trailing = "Run Test >",
          onClick = { showStressTestDialog = true }
        )

        HorizontalDivider(color = SpotifyDivider)

        // Notification Debug integration - keeping it as a card/sub-section or directly here
        // For now, simple integration
        NotificationDebugCard(modifier = Modifier.padding(top = 8.dp))
      }
      
      if (showStressTestDialog) {
        StressTestDialog(
          availableSongs = availableSongs,
          playbackManager = playbackManager,
          onDismiss = { showStressTestDialog = false }
        )
      }
    }

    // AI & API Keys Section
    item {
      SectionTitle("AI & METADATA APIS")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(SpotifyCardElevated)
          .padding(16.dp)
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = SpotifyGreen)
            Spacer(modifier = Modifier.width(10.dp))
            Text("AI Metadata Resolution & LRCLIB", color = SpotifyWhite, fontWeight = FontWeight.Bold)
          }

          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "LRCLIB & iTunes APIs are integrated out-of-the-box (no key needed). Once synced lyrics or 4K covers are downloaded, they are permanently cached for 100% offline playback.",
            color = SpotifyTextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
          )

          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "OpenAI API Key (Optional)",
            color = SpotifyWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Get a key at platform.openai.com > API keys. A ChatGPT Plus subscription is not an API key; API usage is billed separately.",
            color = SpotifyTextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
          )
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
            value = openAiApiKey,
            onValueChange = {
              openAiApiKey = it
              prefs.edit().putString("openai_api_key", it).apply()
            },
            placeholder = { Text("sk-...", fontSize = 12.sp) },
            singleLine = true,
            visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                Text(if (isKeyVisible) "Hide" else "Show", color = SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            },
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = openAiChatModel,
              onValueChange = {
                openAiChatModel = it
                prefs.edit().putString("openai_chat_model", it).apply()
              },
              label = { Text("Chat model", fontSize = 11.sp) },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
              value = openAiTranscriptionModel,
              onValueChange = {
                openAiTranscriptionModel = it
                prefs.edit().putString("openai_transcription_model", it).apply()
              },
              label = { Text("Transcription model", fontSize = 11.sp) },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Auto AI lyrics (default OFF)", color = SpotifyWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
              Text("Automatically use OpenAI as last lyrics fallback", color = SpotifyTextSecondary, fontSize = 11.sp)
            }
            Switch(
              checked = autoAiLyrics,
              onCheckedChange = {
                autoAiLyrics = it
                prefs.edit().putBoolean("auto_ai_lyrics", it).apply()
              },
              colors = SwitchDefaults.colors(
                checkedThumbColor = SpotifyWhite,
                checkedTrackColor = SpotifyGreen,
                uncheckedThumbColor = SpotifyTextSecondary,
                uncheckedTrackColor = SpotifyDivider
              )
            )
          }

          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Notice: Audio files and lyrics prompts are sent to OpenAI when AI metadata cleanup or AI lyrics generation/sync features are used.",
            color = SpotifyTextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
          )

          val scope = androidx.compose.runtime.rememberCoroutineScope()
          var isTestingKey by remember { mutableStateOf(false) }

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (isTestingKey) {
              androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = SpotifyGreen,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(8.dp))
            }
            Button(
              onClick = {
                if (openAiApiKey.isBlank()) {
                  keyTestStatus = "No key entered. Please enter a valid OpenAI API key."
                  return@Button
                }
                isTestingKey = true
                keyTestStatus = "Connecting to OpenAI API..."
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                  try {
                    val openAiSource = com.example.data.lyrics.sources.OpenAILyricsSource(context)
                    val result = openAiSource.testApiKey(openAiApiKey)
                    isTestingKey = false
                    keyTestStatus = result.second
                  } catch (e: Exception) {
                    isTestingKey = false
                    keyTestStatus = "✗ Connection failed: ${e.message}"
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = SpotifyDivider),
              enabled = !isTestingKey
            ) {
              Text("Test Connection", color = SpotifyWhite, fontSize = 12.sp)
            }
          }

          if (keyTestStatus != null) {
            Text(
              text = keyTestStatus!!,
              color = if (keyTestStatus!!.startsWith("✓")) SpotifyGreen else Color(0xFFFF5252),
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(top = 4.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = SpotifyDivider)
          Spacer(modifier = Modifier.height(10.dp))

          // Lyrics Debug Panel button
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(onClick = onOpenLyricsDebug)
              .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = SpotifyGreen)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Lyrics Pipeline Debug Panel", color = SpotifyWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Open Debug >", color = SpotifyGreen, fontSize = 13.sp)
          }
        }
      }
    }

    // Audio & Playback Preferences
    item {
      SectionTitle("AUDIO & PLAYBACK")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(SpotifyCardElevated)
          .padding(16.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          SettingToggleRow(
            title = "Auto-fetch Synced Lyrics",
            subtitle = "Download LRC timestamps via LRCLIB for offline use",
            checked = autoFetchLyrics,
            onCheckedChange = { autoFetchLyrics = it }
          )

          SettingToggleRow(
            title = "Auto-fetch 4K Album Covers",
            subtitle = "Download high-res artwork from iTunes CDN",
            checked = autoFetchCovers,
            onCheckedChange = { autoFetchCovers = it }
          )

          SettingToggleRow(
            title = "Crossfade",
            subtitle = "Smooth gapless transition between audio tracks",
            checked = crossfadeEnabled,
            onCheckedChange = { crossfadeEnabled = it }
          )

          HorizontalDivider(color = SpotifyDivider)

          // Ignore Audio Shorter Than Selector
          val playbackPrefs = remember { context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE) }
          var minDurationSec by remember { mutableStateOf(playbackPrefs.getInt("min_audio_duration_sec", 30)) }

          Column(modifier = Modifier.fillMaxWidth()) {
            Text("Ignore audio shorter than", color = SpotifyWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Filter out voice notes, ringtones & short clips", color = SpotifyTextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(0 to "Off", 15 to "15 s", 30 to "30 s", 60 to "60 s").forEach { (sec, label) ->
                val isSelected = minDurationSec == sec
                Button(
                  onClick = {
                    minDurationSec = sec
                    playbackPrefs.edit().putInt("min_audio_duration_sec", sec).apply()
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) SpotifyGreen else SpotifyDivider,
                    contentColor = if (isSelected) SpotifyBlack else SpotifyWhite
                  ),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                ) {
                  Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
              }
            }
          }

          HorizontalDivider(color = SpotifyDivider)

          // Equalizer Shortcut
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(onClick = onOpenEqualizer)
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.Equalizer, contentDescription = null, tint = SpotifyWhite)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Equalizer & Presets", color = SpotifyWhite, fontSize = 14.sp)
            }
            Text("Adjust >", color = SpotifyGreen, fontSize = 13.sp)
          }

          // Sleep Timer Shortcut
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(onClick = onOpenSleepTimer)
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = SpotifyWhite)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Sleep Timer", color = SpotifyWhite, fontSize = 14.sp)
            }
            Text("Set >", color = SpotifyGreen, fontSize = 13.sp)
          }
        }
      }
    }

    // App Info & Build Stamp
    item {
      Spacer(modifier = Modifier.height(24.dp))
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("SpotiOffline v1.0", color = SpotifyTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Spotify-like Offline Music Player with Synced Lyrics", color = SpotifyTextMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Build: ${BuildConfig.BUILD_STAMP}", color = SpotifyTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
      }
    }
  }

  // Crash & Stability Logs Dialog
  if (showCrashLogsDialog) {
    CrashLogsDialog(
      onDismiss = { showCrashLogsDialog = false }
    )
  }

  // Hidden Songs Dialog
  if (showHiddenSongsDialog) {
    HiddenSongsDialog(
      hiddenSongs = hiddenSongs,
      onRestoreSong = onRestoreSong,
      onDismiss = { showHiddenSongsDialog = false }
    )
  }
}

@Composable
fun CrashLogsDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  var logs by remember { mutableStateOf(CrashLogger.getLogs(context)) }
  var selectedFile by remember { mutableStateOf<File?>(null) }
  var selectedFileContent by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (selectedFile != null) selectedFile!!.name else "Crash & Stability Logs",
          color = SpotifyWhite,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SpotifyWhite)
        }
      }
    },
    text = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(350.dp)
      ) {
        if (selectedFile != null && selectedFileContent != null) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
          ) {
            Text(
              text = selectedFileContent!!,
              color = SpotifyWhite,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        } else {
          if (logs.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Text("No crash or stability logs recorded.", color = SpotifyTextSecondary, fontSize = 13.sp)
            }
          } else {
            LazyColumn(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxSize()
            ) {
              items(logs) { file ->
                val dateStr = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyBlack)
                    .clickable {
                      selectedFile = file
                      selectedFileContent = try { file.readText() } catch (e: Exception) { "Error reading file: ${e.message}" }
                    }
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = file.name,
                      color = SpotifyWhite,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "$dateStr • ${file.length()} bytes",
                      color = SpotifyTextSecondary,
                      fontSize = 11.sp
                    )
                  }
                  Text("View >", color = SpotifyGreen, fontSize = 12.sp)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (selectedFile != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, selectedFileContent ?: "")
                putExtra(Intent.EXTRA_SUBJECT, "Crash Log: ${selectedFile!!.name}")
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Crash Log"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
          ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = SpotifyBlack, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
          OutlinedButton(
            onClick = {
              selectedFile = null
              selectedFileContent = null
            }
          ) {
            Text("Back", color = SpotifyWhite, fontSize = 12.sp)
          }
        }
      } else {
        if (logs.isNotEmpty()) {
          Button(
            onClick = {
              CrashLogger.clearLogs(context)
              logs = CrashLogger.getLogs(context)
              Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyDivider)
          ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear All", color = SpotifyWhite, fontSize = 12.sp)
          }
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = SpotifyWhite)
      }
    },
    containerColor = SpotifyCardElevated
  )
}

@Composable
fun HiddenSongsDialog(
  hiddenSongs: List<Song>,
  onRestoreSong: (String) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Hidden Songs (${hiddenSongs.size})", color = SpotifyWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = onDismiss) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SpotifyWhite)
        }
      }
    },
    text = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(300.dp)
      ) {
        if (hiddenSongs.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hidden songs. Removed songs appear here.", color = SpotifyTextSecondary, fontSize = 13.sp)
          }
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(hiddenSongs) { song ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(SpotifyBlack)
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = song.title,
                    color = SpotifyWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = song.artist,
                    color = SpotifyTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Button(
                  onClick = { onRestoreSong(song.id) },
                  colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                  modifier = Modifier.height(32.dp)
                ) {
                  Text("Restore", color = SpotifyBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Done", color = SpotifyGreen, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = SpotifyCardElevated
  )
}

@Composable
private fun SectionTitle(title: String) {
  Text(
    text = title,
    color = SpotifyTextSecondary,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.sp,
    modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)
  )
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, color = SpotifyWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
      Text(text = subtitle, color = SpotifyTextSecondary, fontSize = 11.sp)
    }
    Spacer(modifier = Modifier.width(8.dp))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = SpotifyBlack,
        checkedTrackColor = SpotifyGreen,
        uncheckedThumbColor = SpotifyWhite,
        uncheckedTrackColor = SpotifyDivider
      )
    )
  }
}

@Composable
fun NotificationDebugCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var permStatus by remember { mutableStateOf("Checking...") }
  var serviceStatus by remember { mutableStateOf("Checking...") }
  var notifStatus by remember { mutableStateOf("Checking...") }

  fun queryStatus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      permStatus = if (granted) "yes" else "no"
    } else {
      permStatus = "yes"
    }

    val isRunning = MusicPlaybackService.getInstance() != null
    serviceStatus = if (isRunning) "yes" else "no"

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm != null) {
      val active = nm.activeNotifications.firstOrNull { it.id == MusicPlaybackService.NOTIFICATION_ID }
      if (active != null) {
        val acts = active.notification.actions
        val count = acts?.size ?: 0
        notifStatus = "yes ($count actions)"
      } else {
        notifStatus = "no"
      }
    } else {
      notifStatus = if (isRunning) "yes" else "no"
    }
  }

  androidx.compose.runtime.LaunchedEffect(Unit) {
    queryStatus()
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(SpotifyCardElevated)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = SpotifyGreen)
        Spacer(modifier = Modifier.width(10.dp))
        Text("Notification Debug", color = SpotifyWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      }
      Button(
        onClick = { queryStatus() },
        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
      ) {
        Text("Check now", color = SpotifyBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }

    HorizontalDivider(color = SpotifyDivider)

    DebugRow("Notification permission", permStatus, if (permStatus == "yes") SpotifyGreen else Color(0xFFFF5252))
    DebugRow("Service running", serviceStatus, if (serviceStatus == "yes") SpotifyGreen else Color(0xFFFF5252))
    DebugRow("Active media notification", notifStatus, if (notifStatus.startsWith("yes")) SpotifyGreen else Color(0xFFFF5252))
  }
}

@Composable
fun DebugRow(label: String, value: String, valueColor: Color) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, color = SpotifyTextSecondary, fontSize = 12.sp)
    Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
  }
}

@Composable
fun StressTestDialog(
  availableSongs: List<Song>,
  playbackManager: PlaybackManager?,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

  var isRunning by remember { mutableStateOf(false) }
  var isFinished by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf<StressTestProgress?>(null) }
  var logReport by remember { mutableStateOf("") }

  var selectedDurationMinutes by remember { mutableStateOf(3) }
  var seedInput by remember { mutableStateOf("42") }

  AlertDialog(
    onDismissRequest = { if (!isRunning) onDismiss() },
    title = {
      Text(
        text = if (isFinished) "Deterministic Stress Test Report" else "On-Device Real Stress Test",
        color = SpotifyWhite,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(360.dp)
      ) {
        if (!isRunning && !isFinished) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = "This real stress tester drives PlaybackManager and ExoPlayer on real audio files, performing continuous playback actions and validating 6 system invariants.",
              color = SpotifyTextSecondary,
              fontSize = 12.sp,
              lineHeight = 16.sp
            )

            Text("Duration Choice:", color = SpotifyWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(3 to "3 Minutes", 10 to "10 Minutes").forEach { (mins, label) ->
                val isSelected = selectedDurationMinutes == mins
                Button(
                  onClick = { selectedDurationMinutes = mins },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) SpotifyGreen else SpotifyDivider,
                    contentColor = if (isSelected) SpotifyBlack else SpotifyWhite
                  ),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                  modifier = Modifier.height(34.dp)
                ) {
                  Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
              }
            }

            OutlinedTextField(
              value = seedInput,
              onValueChange = { seedInput = it.filter { ch -> ch.isDigit() } },
              label = { Text("Random Seed", color = SpotifyTextSecondary, fontSize = 12.sp) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            Text(
              text = "Available playable tracks in pool: ${availableSongs.count { it.playbackError == 0 }} (samples up to 60)",
              color = SpotifyGreen,
              fontSize = 12.sp
            )
          }
        } else if (isRunning) {
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            val elapsed = progress?.elapsedSeconds ?: 0L
            val current = progress?.currentStep ?: 0
            val total = progress?.totalSteps ?: 100
            val targetSec = selectedDurationMinutes * 60
            val progFraction = (elapsed.toFloat() / targetSec.toFloat()).coerceIn(0f, 1f)

            androidx.compose.material3.LinearProgressIndicator(
              progress = { progFraction },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
              color = SpotifyGreen,
              trackColor = SpotifyDivider,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Real Stress Test Running", color = SpotifyWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Step $current / $total", color = SpotifyGreen, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Elapsed: ${elapsed}s / ${targetSec}s (min 60s)", color = SpotifyTextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Action: ${progress?.currentAction ?: "Starting..."}", color = SpotifyWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            val viol = progress?.violationsCount ?: 0
            Text("Invariant Violations: $viol", color = if (viol == 0) SpotifyGreen else Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        } else {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
          ) {
            Text(
              text = logReport,
              color = SpotifyWhite,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }
    },
    confirmButton = {
      if (!isRunning && !isFinished) {
        Button(
          onClick = {
            if (playbackManager != null) {
              isRunning = true
              isFinished = false
              val seed = seedInput.toLongOrNull() ?: 42L
              coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val tester = RealStressTester(context, playbackManager)
                val report = tester.run(
                  availableSongs = availableSongs,
                  seed = seed,
                  durationMinutes = selectedDurationMinutes
                ) { p ->
                  progress = p
                }
                logReport = report
                isRunning = false
                isFinished = true
              }
            } else {
              logReport = "ERROR: PlaybackManager not available."
              isFinished = true
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
        ) {
          Text("Start Real Test", color = SpotifyBlack, fontWeight = FontWeight.Bold)
        }
      } else if (isFinished) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, logReport)
                putExtra(Intent.EXTRA_SUBJECT, "SpotiOffline Real Stress Test Report")
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Report"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
          ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = SpotifyBlack, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
          OutlinedButton(onClick = onDismiss) {
            Text("Close", color = SpotifyWhite, fontSize = 12.sp)
          }
        }
      }
    },
    dismissButton = {
      if (!isRunning && !isFinished) {
        TextButton(onClick = onDismiss) {
          Text("Cancel", color = SpotifyWhite)
        }
      }
    },
    containerColor = SpotifyCardElevated
  )
}

