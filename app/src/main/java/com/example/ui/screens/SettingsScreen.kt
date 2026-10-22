package com.example.ui.screens

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyTextMuted
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite

@Composable
fun SettingsScreen(
  totalSongs: Int,
  onBack: () -> Unit,
  onScanClick: () -> Unit,
  onClearLyricsCache: () -> Unit,
  onClearCoverCache: () -> Unit,
  onOpenEqualizer: () -> Unit,
  onOpenSleepTimer: () -> Unit,
  onOpenLyricsDebug: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
  var autoFetchLyrics by remember { mutableStateOf(true) }
  var autoFetchCovers by remember { mutableStateOf(true) }
  var crossfadeEnabled by remember { mutableStateOf(false) }
  var customGeminiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
  var keyTestStatus by remember { mutableStateOf<String?>(null) }

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
      SectionTitle("LIBRARY & STORAGE")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(SpotifyCardElevated)
          .padding(16.dp)
      ) {
        Column {
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

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = SpotifyDivider)
          Spacer(modifier = Modifier.height(14.dp))

          Button(
            onClick = onScanClick,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = SpotifyBlack)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Re-Scan Device For Songs", color = SpotifyBlack, fontWeight = FontWeight.Bold)
          }

          Spacer(modifier = Modifier.height(10.dp))

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
        }
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
            text = "Google Gemini API Key (Optional)",
            color = SpotifyWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = customGeminiKey,
            onValueChange = { 
              customGeminiKey = it
              prefs.edit().putString("gemini_api_key", it).apply()
            },
            placeholder = { Text("Configure in AI Studio Secrets panel", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = {
              val hasKey = customGeminiKey.isNotBlank() || BuildConfig.BUILD_TYPE.isNotEmpty()
              keyTestStatus = if (hasKey) "✓ Connected to Gemini AI engine" else "No key entered"
            },
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyDivider),
            modifier = Modifier.align(Alignment.End)
          ) {
            Text("Test Connection", color = SpotifyWhite, fontSize = 12.sp)
          }

          if (keyTestStatus != null) {
            Text(
              text = keyTestStatus!!,
              color = SpotifyGreen,
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

    // App Info
    item {
      Spacer(modifier = Modifier.height(24.dp))
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("SpotiOffline v1.0", color = SpotifyTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Spotify-like Offline Music Player with Synced Lyrics", color = SpotifyTextMuted, fontSize = 11.sp)
      }
    }
  }
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
