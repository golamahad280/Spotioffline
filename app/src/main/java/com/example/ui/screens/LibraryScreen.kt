package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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

@OptIn(ExperimentalFoundationApi::class)
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
  onRenameSong: (String, String, String) -> Unit = { _, _, _ -> },
  onHideSong: (Song) -> Unit = {},
  onHideSongs: (List<Song>) -> Unit = {},
  onDeleteSongsFromPhone: (List<Song>) -> Unit = {},
  onChangeCover: (String, Uri) -> Unit = { _, _ -> },
  onRemoveCustomCover: (Song) -> Unit = {},
  onSearchCoverAgain: (Song) -> Unit = {},
  onSearchCoverCustomName: (Song, String) -> Unit = { _, _ -> },
  onRetrySong: (Song) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }
  var isGridView by remember { mutableStateOf(false) }
  var currentSort by remember { mutableStateOf(SortOption.RECENTLY_ADDED) }
  var showSortMenu by remember { mutableStateOf(false) }
  var showCreatePlaylistDialog by remember { mutableStateOf(false) }
  var newPlaylistName by remember { mutableStateOf("") }

  // Multi-select mode state
  var isMultiSelectMode by remember { mutableStateOf(false) }
  var selectedSongIds by remember { mutableStateOf(setOf<String>()) }
  var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }
  var showBulkHideConfirmDialog by remember { mutableStateOf(false) }

  // Single song actions state
  var songToRename by remember { mutableStateOf<Song?>(null) }
  var songToDelete by remember { mutableStateOf<Song?>(null) }
  var songForCoverPick by remember { mutableStateOf<Song?>(null) }
  var songForCustomCoverSearch by remember { mutableStateOf<Song?>(null) }
  var customCoverQuery by remember { mutableStateOf("") }

  // Photo picker launcher for custom thumbnail
  val photoPickerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null && songForCoverPick != null) {
      onChangeCover(songForCoverPick!!.id, uri)
      songForCoverPick = null
    }
  }

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

    // Top Header: Normal Header vs Multi-Select Action Bar
    if (isMultiSelectMode) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SpotifyCardElevated)
          .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = {
            isMultiSelectMode = false
            selectedSongIds = emptySet()
          }) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = SpotifyWhite)
          }
          Text(
            text = "${selectedSongIds.size} selected",
            color = SpotifyWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          // Select All / Deselect All
          IconButton(onClick = {
            selectedSongIds = if (selectedSongIds.size == sortedSongs.size) {
              emptySet()
            } else {
              sortedSongs.map { it.id }.toSet()
            }
          }) {
            Icon(
              imageVector = Icons.Default.SelectAll,
              contentDescription = "Select All",
              tint = if (selectedSongIds.size == sortedSongs.size) SpotifyGreen else SpotifyWhite
            )
          }

          // Remove from app (Hide)
          IconButton(
            onClick = {
              if (selectedSongIds.isNotEmpty()) {
                val selected = sortedSongs.filter { selectedSongIds.contains(it.id) }
                onHideSongs(selected)
                isMultiSelectMode = false
                selectedSongIds = emptySet()
              }
            },
            enabled = selectedSongIds.isNotEmpty()
          ) {
            Icon(
              imageVector = Icons.Default.VisibilityOff,
              contentDescription = "Remove from app",
              tint = if (selectedSongIds.isNotEmpty()) SpotifyWhite else SpotifyTextSecondary
            )
          }

          // Delete from phone
          IconButton(
            onClick = {
              if (selectedSongIds.isNotEmpty()) {
                showBulkDeleteConfirmDialog = true
              }
            },
            enabled = selectedSongIds.isNotEmpty()
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Delete from phone",
              tint = if (selectedSongIds.isNotEmpty()) Color(0xFFFF5252) else SpotifyTextSecondary
            )
          }
        }
      }
    } else {
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

      Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isMultiSelectMode && sortedSongs.isNotEmpty() && (activeFilter == LibraryFilter.ALL || activeFilter == LibraryFilter.SONGS)) {
          TextButton(
            onClick = { isMultiSelectMode = true },
            contentPadding = PaddingValues(horizontal = 8.dp)
          ) {
            Text("Select", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
              val isSelected = selectedSongIds.contains(song.id)
              Box(
                modifier = Modifier
                  .combinedClickable(
                    onClick = {
                      if (isMultiSelectMode) {
                        selectedSongIds = if (isSelected) selectedSongIds - song.id else selectedSongIds + song.id
                      } else {
                        onSongClick(song, sortedSongs)
                      }
                    },
                    onLongClick = {
                      if (!isMultiSelectMode) {
                        isMultiSelectMode = true
                        selectedSongIds = setOf(song.id)
                      }
                    }
                  )
              ) {
                SongCard(
                  song = song,
                  onClick = {
                    if (isMultiSelectMode) {
                      selectedSongIds = if (isSelected) selectedSongIds - song.id else selectedSongIds + song.id
                    } else {
                      onSongClick(song, sortedSongs)
                    }
                  }
                )
                if (isMultiSelectMode) {
                  Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                      selectedSongIds = if (isSelected) selectedSongIds - song.id else selectedSongIds + song.id
                    },
                    colors = CheckboxDefaults.colors(
                      checkedColor = SpotifyGreen,
                      checkmarkColor = SpotifyBlack,
                      uncheckedColor = SpotifyWhite
                    ),
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .padding(4.dp)
                  )
                }
              }
            }
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            if (activeFilter == LibraryFilter.ALL && !isMultiSelectMode) {
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
              val isSelected = selectedSongIds.contains(song.id)
              var showMenu by remember { mutableStateOf(false) }

              LibrarySongRow(
                song = song,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = isSelected,
                onClick = {
                  if (isMultiSelectMode) {
                    selectedSongIds = if (isSelected) selectedSongIds - song.id else selectedSongIds + song.id
                  } else {
                    onSongClick(song, sortedSongs)
                  }
                },
                onLongClick = {
                  if (!isMultiSelectMode) {
                    isMultiSelectMode = true
                    selectedSongIds = setOf(song.id)
                  }
                },
                onMoreClick = { showMenu = true },
                menuContent = {
                  DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SpotifyCardElevated)
                  ) {
                    if (song.playbackError == 1) {
                      DropdownMenuItem(
                        text = { Text("Retry (Clear playback error)", color = SpotifyGreen) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = SpotifyGreen) },
                        onClick = {
                          showMenu = false
                          onRetrySong(song)
                        }
                      )
                    }
                    DropdownMenuItem(
                      text = { Text("Rename", color = SpotifyWhite) },
                      leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SpotifyWhite) },
                      onClick = {
                        showMenu = false
                        songToRename = song
                      }
                    )
                    DropdownMenuItem(
                      text = { Text("Change cover", color = SpotifyWhite) },
                      leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = SpotifyWhite) },
                      onClick = {
                        showMenu = false
                        songForCoverPick = song
                        photoPickerLauncher.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                      }
                    )
                    DropdownMenuItem(
                      text = { Text("Search cover again", color = SpotifyWhite) },
                      leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = SpotifyGreen) },
                      onClick = {
                        showMenu = false
                        onSearchCoverAgain(song)
                      }
                    )
                    DropdownMenuItem(
                      text = { Text("Search with custom name", color = SpotifyWhite) },
                      leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SpotifyGreen) },
                      onClick = {
                        showMenu = false
                        songForCustomCoverSearch = song
                        customCoverQuery = "${song.artist} ${song.title}".replace("Unknown Artist", "").trim()
                      }
                    )
                    if (song.coverLocked) {
                      DropdownMenuItem(
                        text = { Text("Remove custom cover", color = SpotifyWhite) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = SpotifyTextSecondary) },
                        onClick = {
                          showMenu = false
                          onRemoveCustomCover(song)
                        }
                      )
                    }
                    DropdownMenuItem(
                      text = { Text("Remove from app", color = SpotifyWhite) },
                      leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = SpotifyWhite) },
                      onClick = {
                        showMenu = false
                        onHideSong(song)
                      }
                    )
                    DropdownMenuItem(
                      text = { Text("Delete from phone", color = Color(0xFFFF5252)) },
                      leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                      onClick = {
                        showMenu = false
                        songToDelete = song
                      }
                    )
                  }
                }
              )
            }
          }
        }
      }
    }
  }

  // Rename Dialog
  if (songToRename != null) {
    RenameSongDialog(
      song = songToRename!!,
      onConfirm = { title, artist ->
        onRenameSong(songToRename!!.id, title, artist)
        songToRename = null
      },
      onDismiss = { songToRename = null }
    )
  }

  // Delete Single Song Confirmation Dialog
  if (songToDelete != null) {
    AlertDialog(
      onDismissRequest = { songToDelete = null },
      title = { Text("Delete from phone?", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text = "Are you sure you want to permanently delete \"${songToDelete!!.title}\" from your device? This action cannot be undone.",
          color = SpotifyTextSecondary,
          fontSize = 14.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val toDelete = songToDelete!!
            songToDelete = null
            onDeleteSongsFromPhone(listOf(toDelete))
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
        ) {
          Text("Delete", color = SpotifyWhite, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { songToDelete = null }) {
          Text("Cancel", color = SpotifyWhite)
        }
      },
      containerColor = SpotifyCardElevated
    )
  }

  // Bulk Delete Confirmation Dialog
  if (showBulkDeleteConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showBulkDeleteConfirmDialog = false },
      title = { Text("Delete ${selectedSongIds.size} songs from phone?", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text = "This will permanently delete the selected audio files from your device storage. This action cannot be undone.",
          color = SpotifyTextSecondary,
          fontSize = 14.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val selected = sortedSongs.filter { selectedSongIds.contains(it.id) }
            showBulkDeleteConfirmDialog = false
            isMultiSelectMode = false
            selectedSongIds = emptySet()
            onDeleteSongsFromPhone(selected)
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
        ) {
          Text("Delete All", color = SpotifyWhite, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showBulkDeleteConfirmDialog = false }) {
          Text("Cancel", color = SpotifyWhite)
        }
      },
      containerColor = SpotifyCardElevated
    )
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

  // Custom Cover Search Dialog
  if (songForCustomCoverSearch != null) {
    AlertDialog(
      onDismissRequest = { songForCustomCoverSearch = null },
      title = { Text("Search cover online", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text(
            text = "Enter artist and song title to search across iTunes CDN, Deezer, and Cover Art Archive:",
            color = SpotifyTextSecondary,
            fontSize = 13.sp
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = customCoverQuery,
            onValueChange = { customCoverQuery = it },
            placeholder = { Text("Artist and song title", color = SpotifyTextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val targetSong = songForCustomCoverSearch!!
            val query = customCoverQuery.trim()
            songForCustomCoverSearch = null
            if (query.isNotBlank()) {
              onSearchCoverCustomName(targetSong, query)
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
        ) {
          Text("Search Cover", color = SpotifyBlack, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { songForCustomCoverSearch = null }) {
          Text("Cancel", color = SpotifyWhite)
        }
      },
      containerColor = SpotifyCardElevated
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySongRow(
  song: Song,
  isMultiSelectMode: Boolean,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onMoreClick: () -> Unit,
  menuContent: @Composable () -> Unit
) {
  val context = LocalContext.current

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(if (isSelected) SpotifyCardElevated else Color.Transparent)
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick
      )
      .padding(horizontal = 4.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (isMultiSelectMode) {
      Checkbox(
        checked = isSelected,
        onCheckedChange = { onClick() },
        colors = CheckboxDefaults.colors(
          checkedColor = SpotifyGreen,
          checkmarkColor = SpotifyBlack,
          uncheckedColor = SpotifyWhite
        )
      )
    }

    // Album Art Thumbnail (downscaled to 256x256)
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(SpotifyCardElevated),
      contentAlignment = Alignment.Center
    ) {
      if (!song.albumArtUri.isNullOrBlank()) {
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(song.albumArtUri)
            .size(256, 256)
            .crossfade(true)
            .build(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Icon(
          imageVector = Icons.Default.MusicNote,
          contentDescription = null,
          tint = SpotifyTextSecondary
        )
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Song metadata
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = song.title,
        color = SpotifyWhite,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(2.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (song.playbackError == 1) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(Color(0xFFFF5252).copy(alpha = 0.2f))
              .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            Text("UNPLAYABLE", color = Color(0xFFFF5252), fontSize = 9.sp, fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.width(6.dp))
        }
        if (song.hasLyrics) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(SpotifyGreen.copy(alpha = 0.2f))
              .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            Text("LYRICS", color = SpotifyGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.width(6.dp))
        }
        if (song.coverLocked) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(SpotifyDivider)
              .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            Text("CUSTOM", color = SpotifyWhite, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
          }
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
          text = song.artist,
          color = SpotifyTextSecondary,
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    if (!isMultiSelectMode) {
      Box {
        IconButton(onClick = onMoreClick) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Options",
            tint = SpotifyTextSecondary
          )
        }
        menuContent()
      }
    }
  }
}

@Composable
fun RenameSongDialog(
  song: Song,
  onConfirm: (String, String) -> Unit,
  onDismiss: () -> Unit
) {
  var title by remember { mutableStateOf(song.title) }
  var artist by remember { mutableStateOf(song.artist) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename Song", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Edit song title and artist. Changes will be locked from future scans.", color = SpotifyTextSecondary, fontSize = 12.sp)
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = artist,
          onValueChange = { artist = it },
          label = { Text("Artist") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (title.isNotBlank()) {
            onConfirm(title.trim(), artist.trim().ifBlank { "Unknown Artist" })
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
      ) {
        Text("Save", color = SpotifyBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = SpotifyWhite)
      }
    },
    containerColor = SpotifyCardElevated
  )
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
