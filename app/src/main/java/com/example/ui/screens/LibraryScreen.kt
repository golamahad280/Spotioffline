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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.data.model.SortOption
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCard
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite

enum class LibraryFilter {
  ALL,
  PLAYLISTS,
  ARTISTS,
  SONGS
}

@Composable
fun LibraryScreen(
  allSongs: List<Song>,
  likedSongs: List<Song>,
  playlists: List<Playlist>,
  artists: List<String>,
  onSongClick: (Song, List<Song>) -> Unit,
  onPlaylistClick: (Playlist) -> Unit,
  onArtistClick: (String) -> Unit,
  onLikedClick: () -> Unit,
  onScanClick: () -> Unit,
  onPickAudioFiles: () -> Unit,
  onCreatePlaylist: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }
  var isGridView by remember { mutableStateOf(false) }
  var currentSort by remember { mutableStateOf(SortOption.RECENTLY_ADDED) }
  var showSortMenu by remember { mutableStateOf(false) }
  var showCreatePlaylistDialog by remember { mutableStateOf(false) }
  var newPlaylistName by remember { mutableStateOf("") }

  // Sort songs
  val sortedSongs = remember(allSongs, currentSort) {
    when (currentSort) {
      SortOption.RECENTLY_ADDED -> allSongs.sortedByDescending { it.addedAt }
      SortOption.TITLE_ASC -> allSongs.sortedBy { it.title.lowercase() }
      SortOption.TITLE_DESC -> allSongs.sortedByDescending { it.title.lowercase() }
      SortOption.ARTIST_ASC -> allSongs.sortedBy { it.artist.lowercase() }
      SortOption.DURATION_DESC -> allSongs.sortedByDescending { it.durationMs }
      SortOption.MOST_PLAYED -> allSongs.sortedByDescending { it.playCount }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SpotifyBlack)
      .padding(horizontal = 16.dp)
      .testTag("library_screen")
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    // Top Header Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Your Library",
        color = SpotifyWhite,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
      )

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { showCreatePlaylistDialog = true },
          modifier = Modifier.testTag("create_playlist_btn")
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = "Create Playlist", tint = SpotifyWhite)
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Filter Chips
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(LibraryFilter.values()) { filter ->
        val selected = filter == activeFilter
        FilterChip(
          selected = selected,
          onClick = { activeFilter = filter },
          label = {
            Text(
              text = when (filter) {
                LibraryFilter.ALL -> "All"
                LibraryFilter.PLAYLISTS -> "Playlists"
                LibraryFilter.ARTISTS -> "Artists"
                LibraryFilter.SONGS -> "Songs"
              },
              fontSize = 12.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SpotifyGreen,
            selectedLabelColor = SpotifyBlack,
            containerColor = SpotifyCardElevated,
            labelColor = SpotifyWhite
          )
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Scan Device & Import Audio Action Buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onScanClick,
        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
        modifier = Modifier
          .weight(1.2f)
          .testTag("scan_device_btn")
      ) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = SpotifyBlack, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Scan Device", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      }

      OutlinedButton(
        onClick = onPickAudioFiles,
        modifier = Modifier
          .weight(1f)
          .testTag("import_audio_btn")
      ) {
        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Import", color = SpotifyWhite, fontSize = 13.sp)
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Sort & View Toggle Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box {
        Row(
          modifier = Modifier
            .clickable { showSortMenu = true }
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(imageVector = Icons.Default.Sort, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = when (currentSort) {
              SortOption.RECENTLY_ADDED -> "Recently Added"
              SortOption.TITLE_ASC -> "Title A-Z"
              SortOption.TITLE_DESC -> "Title Z-A"
              SortOption.ARTIST_ASC -> "Artist A-Z"
              SortOption.DURATION_DESC -> "Duration"
              SortOption.MOST_PLAYED -> "Most Played"
            },
            color = SpotifyWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        DropdownMenu(
          expanded = showSortMenu,
          onDismissRequest = { showSortMenu = false },
          modifier = Modifier.background(SpotifyCardElevated)
        ) {
          SortOption.values().forEach { opt ->
            DropdownMenuItem(
              text = {
                Text(
                  text = when (opt) {
                    SortOption.RECENTLY_ADDED -> "Recently Added"
                    SortOption.TITLE_ASC -> "Title A-Z"
                    SortOption.TITLE_DESC -> "Title Z-A"
                    SortOption.ARTIST_ASC -> "Artist A-Z"
                    SortOption.DURATION_DESC -> "Duration"
                    SortOption.MOST_PLAYED -> "Most Played"
                  },
                  color = if (opt == currentSort) SpotifyGreen else SpotifyWhite
                )
              },
              onClick = {
                currentSort = opt
                showSortMenu = false
              }
            )
          }
        }
      }

      IconButton(
        onClick = { isGridView = !isGridView },
        modifier = Modifier.size(32.dp)
      ) {
        Icon(
          imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
          contentDescription = "Toggle Grid/List",
          tint = SpotifyTextSecondary,
          modifier = Modifier.size(20.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Content Display based on activeFilter & isGridView
    when (activeFilter) {
      LibraryFilter.PLAYLISTS -> {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 120.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          item {
            PlaylistRowItem(
              title = "Liked Songs",
              subtitle = "${likedSongs.size} songs",
              isLikedSongs = true,
              onClick = onLikedClick
            )
          }
          items(playlists) { pl ->
            PlaylistRowItem(
              title = pl.name,
              subtitle = pl.description ?: "Playlist",
              onClick = { onPlaylistClick(pl) }
            )
          }
        }
      }

      LibraryFilter.ARTISTS -> {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 120.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(artists) { artist ->
            ArtistRowItem(
              artistName = artist,
              onClick = { onArtistClick(artist) }
            )
          }
        }
      }

      LibraryFilter.SONGS, LibraryFilter.ALL -> {
        if (isGridView) {
          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(sortedSongs) { song ->
              SongCard(
                song = song,
                onClick = { onSongClick(song, sortedSongs) }
              )
            }
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            if (activeFilter == LibraryFilter.ALL) {
              item {
                PlaylistRowItem(
                  title = "Liked Songs",
                  subtitle = "${likedSongs.size} songs",
                  isLikedSongs = true,
                  onClick = onLikedClick
                )
              }
            }
            items(sortedSongs) { song ->
              SongRowItem(
                song = song,
                onClick = { onSongClick(song, sortedSongs) }
              )
            }
          }
        }
      }
    }
  }

  // Create Playlist Dialog
  if (showCreatePlaylistDialog) {
    AlertDialog(
      onDismissRequest = { showCreatePlaylistDialog = false },
      title = { Text("Give your playlist a name", color = SpotifyWhite) },
      text = {
        OutlinedTextField(
          value = newPlaylistName,
          onValueChange = { newPlaylistName = it },
          placeholder = { Text("My Playlist #1") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (newPlaylistName.isNotBlank()) {
              onCreatePlaylist(newPlaylistName.trim())
              newPlaylistName = ""
            }
            showCreatePlaylistDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
        ) {
          Text("Create", color = SpotifyBlack, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreatePlaylistDialog = false }) {
          Text("Cancel", color = SpotifyWhite)
        }
      },
      containerColor = SpotifyCardElevated
    )
  }
}

@Composable
fun PlaylistRowItem(
  title: String,
  subtitle: String,
  isLikedSongs: Boolean = false,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(if (isLikedSongs) SpotifyGreen else SpotifyCardElevated),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.QueueMusic,
        contentDescription = null,
        tint = if (isLikedSongs) SpotifyBlack else SpotifyTextSecondary,
        modifier = Modifier.size(28.dp)
      )
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        color = SpotifyWhite,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        color = SpotifyTextSecondary,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun ArtistRowItem(
  artistName: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(54.dp)
        .clip(CircleShape)
        .background(SpotifyGreen.copy(alpha = 0.3f)),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = artistName.take(2).uppercase(),
        color = SpotifyWhite,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
      )
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = artistName,
        color = SpotifyWhite,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        text = "Artist",
        color = SpotifyTextSecondary,
        fontSize = 13.sp
      )
    }
  }
}
