package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyBlue
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyPurple
import com.example.ui.theme.SpotifyRedHeart
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite

@Composable
fun PlaylistDetailScreen(
  playlistName: String,
  songs: List<Song>,
  isLikedSongs: Boolean = false,
  onBack: () -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onDeletePlaylist: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val headerGradient = if (isLikedSongs) {
    listOf(SpotifyPurple, SpotifyBlue, SpotifyBlack)
  } else {
    listOf(Color(0xFF1E3A2F), Color(0xFF13231D), SpotifyBlack)
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(SpotifyBlack)
      .testTag("playlist_detail_screen"),
    contentPadding = PaddingValues(bottom = 120.dp)
  ) {
    // Header Hero Banner
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Brush.verticalGradient(headerGradient))
          .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = onBack) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SpotifyWhite
              )
            }

            if (onDeletePlaylist != null && !isLikedSongs) {
              IconButton(onClick = onDeletePlaylist) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "Delete Playlist",
                  tint = SpotifyTextSecondary
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Playlist Artwork
          Box(
            modifier = Modifier
              .size(160.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(if (isLikedSongs) SpotifyPurple else SpotifyCardElevated)
              .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isLikedSongs) Icons.Default.Favorite else Icons.Default.QueueMusic,
              contentDescription = null,
              tint = if (isLikedSongs) SpotifyRedHeart else SpotifyGreen,
              modifier = Modifier.size(72.dp)
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          Text(
            text = playlistName,
            color = SpotifyWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "${songs.size} tracks • Offline Library",
            color = SpotifyTextSecondary,
            fontSize = 13.sp
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Play & Shuffle FAB
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = {
                if (songs.isNotEmpty()) {
                  val shuffled = songs.shuffled()
                  onSongClick(shuffled.first(), shuffled)
                }
              }
            ) {
              Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle", tint = SpotifyGreen, modifier = Modifier.size(28.dp))
            }

            FloatingActionButton(
              onClick = {
                if (songs.isNotEmpty()) onSongClick(songs.first(), songs)
              },
              shape = CircleShape,
              containerColor = SpotifyGreen,
              contentColor = SpotifyBlack,
              modifier = Modifier.size(56.dp)
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play All", modifier = Modifier.size(32.dp))
            }
          }
        }
      }
    }

    // Songs List
    if (songs.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No tracks in this playlist yet.",
            color = SpotifyTextSecondary,
            fontSize = 14.sp
          )
        }
      }
    } else {
      items(songs) { song ->
        SongRowItem(
          song = song,
          onClick = { onSongClick(song, songs) }
        )
      }
    }
  }
}
