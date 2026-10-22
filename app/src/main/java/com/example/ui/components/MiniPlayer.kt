package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Song
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyRedHeart
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite

@Composable
fun MiniPlayer(
  song: Song?,
  isPlaying: Boolean,
  currentPositionMs: Long,
  durationMs: Long,
  onPlayPauseClick: () -> Unit,
  onLikeClick: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (song == null) return

  val progress = if (durationMs > 0) {
    (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
  } else 0f

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(SpotifyCardElevated)
      .testTag("mini_player")
      .clickable(onClick = onClick)
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Thumbnail Art
        if (!song.albumArtUri.isNullOrBlank()) {
          AsyncImage(
            model = song.albumArtUri,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(4.dp))
          )
        } else {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(SpotifyDivider),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.MusicNote,
              contentDescription = null,
              tint = SpotifyTextSecondary,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = song.title,
            color = SpotifyWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = song.artist,
            color = SpotifyTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        // Like Button
        IconButton(
          onClick = onLikeClick,
          modifier = Modifier
            .size(40.dp)
            .testTag("mini_player_like")
        ) {
          Icon(
            imageVector = if (song.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (song.isLiked) "Unlike" else "Like",
            tint = if (song.isLiked) SpotifyRedHeart else SpotifyTextSecondary,
            modifier = Modifier.size(22.dp)
          )
        }

        // Play/Pause Button
        IconButton(
          onClick = onPlayPauseClick,
          modifier = Modifier
            .size(40.dp)
            .testTag("mini_player_play_pause")
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = SpotifyWhite,
            modifier = Modifier.size(28.dp)
          )
        }
      }

      // 2dp Progress Line at the very bottom
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
          .fillMaxWidth()
          .height(2.dp),
        color = SpotifyGreen,
        trackColor = SpotifyDivider
      )
    }
  }
}
