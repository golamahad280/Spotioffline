package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.LrcLine
import com.example.data.model.RepeatMode
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyRedHeart
import com.example.ui.theme.SpotifyTextMuted
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite
import com.example.utils.ImageUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingModal(
  song: Song?,
  isPlaying: Boolean,
  currentPositionMs: Long,
  durationMs: Long,
  lyricsLines: List<LrcLine>,
  activeLyricIndex: Int,
  shuffleEnabled: Boolean,
  repeatMode: RepeatMode,
  sleepTimerRemaining: Int,
  onDismiss: () -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onSeek: (Long) -> Unit,
  onToggleShuffle: () -> Unit,
  onToggleRepeat: () -> Unit,
  onToggleLike: (Song) -> Unit,
  onOpenLyrics: () -> Unit,
  onOpenQueue: () -> Unit,
  onOpenEqualizer: () -> Unit,
  onOpenSleepTimer: () -> Unit,
  onOpenFixMatch: (Song) -> Unit,
  modifier: Modifier = Modifier
) {
  if (song == null) return

  val dominantColor = remember(song.id) {
    ImageUtils.getFallbackColorForTrack(song.title)
  }

  var isUserSeeking by remember { mutableStateOf(false) }
  var seekFraction by remember { mutableFloatStateOf(0f) }
  var showMenu by remember { mutableStateOf(false) }

  val actualFraction = if (durationMs > 0) {
    (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
  } else 0f

  val displayFraction = if (isUserSeeking) seekFraction else actualFraction
  val displayPositionMs = (displayFraction * durationMs).toLong()

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("now_playing_modal"),
    color = SpotifyBlack
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              dominantColor.copy(alpha = 0.7f),
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
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Top Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(48.dp)
              .testTag("now_playing_close")
          ) {
            Icon(
              imageVector = Icons.Default.KeyboardArrowDown,
              contentDescription = "Collapse",
              tint = SpotifyWhite,
              modifier = Modifier.size(32.dp)
            )
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = 8.dp)
          ) {
            Text(
              text = "PLAYING FROM PLAYLIST",
              color = SpotifyTextSecondary,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = song.album.ifBlank { "Your Library" },
              color = SpotifyWhite,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Box {
            IconButton(
              onClick = { showMenu = true },
              modifier = Modifier
                .size(48.dp)
                .testTag("now_playing_menu")
            ) {
              Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = SpotifyWhite
              )
            }
            DropdownMenu(
              expanded = showMenu,
              onDismissRequest = { showMenu = false },
              modifier = Modifier.background(SpotifyCardElevated)
            ) {
              DropdownMenuItem(
                text = { Text("Equalizer", color = SpotifyWhite) },
                onClick = {
                  showMenu = false
                  onOpenEqualizer()
                }
              )
              DropdownMenuItem(
                text = { Text("Sleep Timer", color = SpotifyWhite) },
                onClick = {
                  showMenu = false
                  onOpenSleepTimer()
                }
              )
              DropdownMenuItem(
                text = { Text("Fix Match / Tags", color = SpotifyWhite) },
                onClick = {
                  showMenu = false
                  onOpenFixMatch(song)
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large Album Artwork
        Box(
          modifier = Modifier
            .fillMaxWidth(0.92f)
            .aspectRatio(1f)
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(SpotifyCardElevated),
          contentAlignment = Alignment.Center
        ) {
          if (!song.albumArtUri.isNullOrBlank()) {
            AsyncImage(
              model = song.albumArtUri,
              contentDescription = song.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Icon(
              imageVector = Icons.Default.MusicNote,
              contentDescription = null,
              tint = SpotifyTextSecondary,
              modifier = Modifier.size(80.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Title, Artist and Heart Icon
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = song.title,
              color = SpotifyWhite,
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = song.artist,
              color = SpotifyTextSecondary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = { onToggleLike(song) },
            modifier = Modifier
              .size(48.dp)
              .testTag("now_playing_like")
          ) {
            Icon(
              imageVector = if (song.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
              contentDescription = if (song.isLiked) "Unlike" else "Like",
              tint = if (song.isLiked) SpotifyRedHeart else SpotifyWhite,
              modifier = Modifier.size(28.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Seek Bar Slider
        Slider(
          value = displayFraction,
          onValueChange = {
            isUserSeeking = true
            seekFraction = it
          },
          onValueChangeFinished = {
            isUserSeeking = false
            onSeek((seekFraction * durationMs).toLong())
          },
          colors = SliderDefaults.colors(
            thumbColor = SpotifyWhite,
            activeTrackColor = SpotifyWhite,
            inactiveTrackColor = SpotifyDivider
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("now_playing_slider")
        )

        // Time indicators
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = formatTimeMs(displayPositionMs),
            color = SpotifyTextSecondary,
            fontSize = 12.sp
          )
          Text(
            text = formatTimeMs(durationMs),
            color = SpotifyTextSecondary,
            fontSize = 12.sp
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback Controls Row (Shuffle, Prev, Play/Pause, Next, Repeat)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = onToggleShuffle,
            modifier = Modifier.testTag("now_playing_shuffle")
          ) {
            Icon(
              imageVector = Icons.Default.Shuffle,
              contentDescription = "Shuffle",
              tint = if (shuffleEnabled) SpotifyGreen else SpotifyTextSecondary,
              modifier = Modifier.size(24.dp)
            )
          }

          IconButton(
            onClick = onPrevious,
            modifier = Modifier.testTag("now_playing_prev")
          ) {
            Icon(
              imageVector = Icons.Default.SkipPrevious,
              contentDescription = "Previous",
              tint = SpotifyWhite,
              modifier = Modifier.size(36.dp)
            )
          }

          // Big Spotify Play/Pause Circle Button
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .background(SpotifyWhite)
              .clickable(onClick = onPlayPause)
              .testTag("now_playing_play_pause"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play",
              tint = SpotifyBlack,
              modifier = Modifier.size(38.dp)
            )
          }

          IconButton(
            onClick = onNext,
            modifier = Modifier.testTag("now_playing_next")
          ) {
            Icon(
              imageVector = Icons.Default.SkipNext,
              contentDescription = "Next",
              tint = SpotifyWhite,
              modifier = Modifier.size(36.dp)
            )
          }

          IconButton(
            onClick = onToggleRepeat,
            modifier = Modifier.testTag("now_playing_repeat")
          ) {
            Icon(
              imageVector = when (repeatMode) {
                RepeatMode.ONE -> Icons.Default.RepeatOne
                else -> Icons.Default.Repeat
              },
              contentDescription = "Repeat",
              tint = if (repeatMode != RepeatMode.OFF) SpotifyGreen else SpotifyTextSecondary,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Secondary Action Shortcuts (Equalizer, Sleep Timer, Queue)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onOpenEqualizer) {
            Icon(
              imageVector = Icons.Default.Equalizer,
              contentDescription = "Equalizer",
              tint = SpotifyTextSecondary,
              modifier = Modifier.size(22.dp)
            )
          }

          IconButton(onClick = onOpenSleepTimer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Sleep Timer",
                tint = if (sleepTimerRemaining > 0) SpotifyGreen else SpotifyTextSecondary,
                modifier = Modifier.size(22.dp)
              )
              if (sleepTimerRemaining > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "${sleepTimerRemaining}m",
                  color = SpotifyGreen,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          IconButton(onClick = onOpenQueue) {
            Icon(
              imageVector = Icons.Default.QueueMusic,
              contentDescription = "Queue",
              tint = SpotifyTextSecondary,
              modifier = Modifier.size(22.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mini Lyrics Preview Card
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(dominantColor.copy(alpha = 0.5f))
            .clickable(onClick = onOpenLyrics)
            .padding(18.dp)
            .testTag("lyrics_preview_card")
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "LYRICS",
                color = SpotifyWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color.Black.copy(alpha = 0.3f))
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "MORE",
                  color = SpotifyWhite,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.Outlined.OpenInFull,
                  contentDescription = null,
                  tint = SpotifyWhite,
                  modifier = Modifier.size(12.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (lyricsLines.isNotEmpty()) {
              val currentLine = lyricsLines.getOrNull(activeLyricIndex)?.text ?: lyricsLines.first().text
              Text(
                text = currentLine,
                color = SpotifyWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            } else {
              Text(
                text = if (song.hasLyrics && !song.lyricsText.isNullOrBlank()) {
                  song.lyricsText.lines().take(2).joinToString("\n")
                } else {
                  "Lyrics are being fetched automatically from LRCLIB..."
                },
                color = SpotifyWhite.copy(alpha = 0.8f),
                fontSize = 15.sp,
                lineHeight = 22.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }
}

fun formatTimeMs(millis: Long): String {
  val totalSeconds = (millis / 1000).coerceAtLeast(0)
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
