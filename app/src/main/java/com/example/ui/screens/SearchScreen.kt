package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyBlue
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyOrange
import com.example.ui.theme.SpotifyPink
import com.example.ui.theme.SpotifyPurple
import com.example.ui.theme.SpotifyTeal
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite
import com.example.ui.theme.SpotifyYellow

data class BrowseCategory(
  val title: String,
  val color: Color
)

@Composable
fun SearchScreen(
  allSongs: List<Song>,
  onSongClick: (Song, List<Song>) -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }

  val browseCategories = remember {
    listOf(
      BrowseCategory("Pop", SpotifyPink),
      BrowseCategory("Hip-Hop", SpotifyOrange),
      BrowseCategory("Synthwave", SpotifyPurple),
      BrowseCategory("Rock", Color(0xFFE91429)),
      BrowseCategory("Indie", SpotifyTeal),
      BrowseCategory("Lo-Fi & Chill", SpotifyBlue),
      BrowseCategory("Focus & Study", SpotifyYellow),
      BrowseCategory("Electronic", SpotifyGreen)
    )
  }

  val filteredSongs = remember(searchQuery, allSongs) {
    if (searchQuery.isBlank()) {
      emptyList()
    } else {
      val query = searchQuery.trim().lowercase()
      allSongs.filter { song ->
        song.title.lowercase().contains(query) ||
        song.artist.lowercase().contains(query) ||
        song.album.lowercase().contains(query) ||
        (song.lyricsText?.lowercase()?.contains(query) == true)
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SpotifyBlack)
      .padding(horizontal = 16.dp)
      .testTag("search_screen")
  ) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = "Search",
      color = SpotifyWhite,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(14.dp))

    // Spotify-style Search Input Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = {
        Text("What do you want to listen to?", color = SpotifyBlack, fontSize = 14.sp)
      },
      leadingIcon = {
        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = SpotifyBlack)
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = SpotifyBlack)
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(8.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SpotifyWhite,
        unfocusedContainerColor = SpotifyWhite,
        focusedTextColor = SpotifyBlack,
        unfocusedTextColor = SpotifyBlack,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent
      ),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("search_input")
    )

    Spacer(modifier = Modifier.height(18.dp))

    if (searchQuery.isNotBlank()) {
      Text(
        text = "Top results (${filteredSongs.size})",
        color = SpotifyWhite,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
      )

      if (filteredSongs.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No tracks or lyrics found matching \"$searchQuery\"",
            color = SpotifyTextSecondary,
            fontSize = 14.sp
          )
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 120.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          items(filteredSongs) { song ->
            SongRowItem(
              song = song,
              onClick = { onSongClick(song, filteredSongs) }
            )
          }
        }
      }
    } else {
      // Browse All Categories Grid
      Text(
        text = "Browse all",
        color = SpotifyWhite,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp)
      )

      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(browseCategories) { cat ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(96.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(cat.color)
              .clickable { searchQuery = cat.title }
              .padding(12.dp)
          ) {
            Text(
              text = cat.title,
              color = SpotifyWhite,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.align(Alignment.TopStart)
            )
          }
        }
      }
    }
  }
}
