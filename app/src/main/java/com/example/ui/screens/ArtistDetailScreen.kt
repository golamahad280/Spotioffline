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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyBlue
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyPurple
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite

@Composable
fun ArtistDetailScreen(
  artistName: String,
  songs: List<Song>,
  onBack: () -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(SpotifyBlack)
      .testTag("artist_detail_screen"),
    contentPadding = PaddingValues(bottom = 120.dp)
  ) {
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              listOf(SpotifyBlue.copy(alpha = 0.8f), SpotifyPurple.copy(alpha = 0.5f), SpotifyBlack)
            )
          )
          .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
      ) {
        Column {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = SpotifyWhite
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          Box(
            modifier = Modifier
              .size(120.dp)
              .clip(CircleShape)
              .background(SpotifyWhite.copy(alpha = 0.2f))
              .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = artistName.take(2).uppercase(),
              color = SpotifyWhite,
              fontSize = 36.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = artistName,
            color = SpotifyWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
          )

          Text(
            text = "${songs.size} local tracks on device",
            color = SpotifyTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
          )

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            FloatingActionButton(
              onClick = {
                if (songs.isNotEmpty()) onSongClick(songs.first(), songs)
              },
              shape = CircleShape,
              containerColor = SpotifyGreen,
              contentColor = SpotifyBlack,
              modifier = Modifier.size(56.dp)
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Artist", modifier = Modifier.size(32.dp))
            }
          }
        }
      }
    }

    item {
      Text(
        text = "Popular Tracks",
        color = SpotifyWhite,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
      )
    }

    items(songs) { song ->
      SongRowItem(
        song = song,
        onClick = { onSongClick(song, songs) }
      )
    }
  }
}
