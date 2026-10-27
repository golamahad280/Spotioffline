package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.lyrics.LyricsCandidateItem
import com.example.data.model.Song
import com.example.ui.theme.*
import com.example.utils.CrashLogger
import com.example.utils.LrcParser
import com.example.utils.ScriptDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

@Composable
fun SearchOtherVersionsDialog(
  song: Song,
  onSelectCandidate: (String, Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  var candidates by remember { mutableStateOf<List<LyricsCandidateItem>>(emptyList()) }
  var isLoading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(song.id) {
    isLoading = true
    errorMessage = null
    val resultList = mutableListOf<LyricsCandidateItem>()
    try {
      withContext(Dispatchers.IO) {
        val query = "${song.artist} ${song.title}".trim()
        val url = "https://lrclib.net/api/search?q=${URLEncoder.encode(query, "UTF-8")}"
        val okClient = OkHttpClient()
        val req = Request.Builder().url(url).build()
        val res = okClient.newCall(req).execute()
        if (res.isSuccessful) {
          val body = res.body?.string() ?: ""
          val jsonArr = org.json.JSONArray(body)
          for (i in 0 until jsonArr.length().coerceAtMost(6)) {
            val item = jsonArr.getJSONObject(i)
            val trackName = item.optString("trackName", "")
            val artistName = item.optString("artistName", "")
            val duration = item.optInt("duration", 0)
            val synced = item.optString("syncedLyrics", "")
            val plain = item.optString("plainLyrics", "")
            val lrc = if (synced.isNotBlank()) synced else plain
            if (lrc.isNotBlank()) {
              val isSynced = synced.isNotBlank()
              val script = ScriptDetector.detectScript(lrc)
              val preview = LrcParser.stripTimestamps(lrc).lines().firstOrNull { it.isNotBlank() } ?: ""
              resultList.add(
                LyricsCandidateItem(
                  source = "LRCLIB",
                  title = trackName,
                  artist = artistName,
                  durationSec = duration,
                  isSynced = isSynced,
                  detectedScript = script,
                  scriptBadge = ScriptDetector.getScriptBadge(script),
                  previewText = preview,
                  fullLyricsText = lrc
                )
              )
            }
          }
        }

        val netEaseSource = com.example.data.lyrics.sources.NetEaseLyricsSource()
        val netEaseCandidates = netEaseSource.searchCandidates("${song.artist} ${song.title}")
        resultList.addAll(netEaseCandidates)
      }
      candidates = resultList.take(8)
      if (candidates.isEmpty()) {
        errorMessage = "No other versions found online."
      }
    } catch (e: Exception) {
      CrashLogger.e("SearchOtherVersions", "Error searching versions", e)
      errorMessage = "Error: ${e.message}"
    } finally {
      isLoading = false
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .testTag("search_other_versions_dialog"),
      color = SpotifyBlack,
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Search Other Versions", color = SpotifyWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("${song.title} • ${song.artist}", color = SpotifyTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = SpotifyWhite)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          when {
            isLoading -> {
              Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = SpotifyGreen)
                Text("Searching free online sources...", color = SpotifyTextSecondary, fontSize = 13.sp)
              }
            }
            errorMessage != null -> {
              Text(errorMessage ?: "", color = Color(0xFFFF5252), fontSize = 14.sp)
            }
            else -> {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                items(candidates) { candidate ->
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .background(SpotifyCardElevated)
                      .clickable {
                        onSelectCandidate(candidate.fullLyricsText, candidate.isSynced)
                      }
                      .padding(14.dp)
                  ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                          Text(candidate.source, color = SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                          if (candidate.isSynced) {
                            Surface(
                              color = SpotifyGreen.copy(alpha = 0.2f),
                              shape = RoundedCornerShape(4.dp)
                            ) {
                              Text("Synced", color = SpotifyGreen, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                          } else {
                            Surface(
                              color = SpotifyDivider,
                              shape = RoundedCornerShape(4.dp)
                            ) {
                              Text("Plain", color = SpotifyTextSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                          }
                          candidate.scriptBadge?.let { badge ->
                            Surface(
                              color = Color(0xFF2E3830),
                              shape = RoundedCornerShape(4.dp)
                            ) {
                              Text(badge, color = SpotifyWhite, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                          }
                        }
                        Text("${candidate.durationSec}s", color = SpotifyTextMuted, fontSize = 11.sp)
                      }
                      Text(
                        text = "${candidate.title} - ${candidate.artist}",
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = candidate.previewText,
                        color = SpotifyTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Cancel", color = SpotifyWhite)
        }
      }
    }
  }
}
