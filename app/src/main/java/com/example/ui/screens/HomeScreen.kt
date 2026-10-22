package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ScanProgress
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyBlue
import com.example.ui.theme.SpotifyCard
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyPurple
import com.example.ui.theme.SpotifyRedHeart
import com.example.ui.theme.SpotifyTextMuted
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite
import java.util.Calendar

@Composable
fun HomeScreen(
  allSongs: List<Song>,
  likedSongs: List<Song>,
  recentlyPlayed: List<Song>,
  mostPlayed: List<Song>,
  artists: List<String>,
  scanProgress: ScanProgress,
  onSongClick: (Song, List<Song>) -> Unit,
  onArtistClick: (String) -> Unit,
  onLikedClick: () -> Unit,
  onScanClick: () -> Unit,
  onLoadDemoClick: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val greeting = remember {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    when (hour) {
      in 4..11 -> "Good morning"
      in 12..17 -> "Good afternoon"
      else -> "Good evening"
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(SpotifyBlack)
      .testTag("home_screen"),
    contentPadding = PaddingValues(bottom = 120.dp)
  ) {
    // Top Bar & Greeting
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = greeting,
          color = SpotifyWhite,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold
        )
        IconButton(
          onClick = onOpenSettings,
          modifier = Modifier.testTag("home_settings_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = SpotifyWhite
          )
        }
      }
    }

    // Scanning progress banner (if active)
    if (scanProgress.isScanning) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyCardElevated)
            .padding(12.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Scanning Device for Audio...",
                color = SpotifyWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "${scanProgress.scannedFiles}/${scanProgress.totalFiles}",
                color = SpotifyGreen,
                fontSize = 12.sp
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = scanProgress.currentFileName,
              color = SpotifyTextSecondary,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            val progFraction = if (scanProgress.totalFiles > 0) {
              scanProgress.scannedFiles.toFloat() / scanProgress.totalFiles.toFloat()
            } else 0f
            LinearProgressIndicator(
              progress = { progFraction },
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
              color = SpotifyGreen,
              trackColor = SpotifyDivider
            )
          }
        }
      }
    }

    // Top Quick-Access 6-Grid
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Liked Songs Tile
          QuickTile(
            title = "Liked Songs",
            subtitle = "${likedSongs.size} tracks",
            gradient = listOf(SpotifyPurple, SpotifyBlue),
            icon = Icons.Default.Favorite,
            iconTint = SpotifyRedHeart,
            modifier = Modifier.weight(1f),
            onClick = onLikedClick
          )
          // Most Played Tile
          QuickTile(
            title = "Heavy Rotation",
            subtitle = "Most played",
            gradient = listOf(Color(0xFF0F3E30), SpotifyGreen),
            icon = Icons.Default.GraphicEq,
            iconTint = SpotifyWhite,
            modifier = Modifier.weight(1f),
            onClick = {
              if (mostPlayed.isNotEmpty()) onSongClick(mostPlayed.first(), mostPlayed)
            }
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val song1 = allSongs.getOrNull(0)
          val song2 = allSongs.getOrNull(1)

          QuickTile(
            title = song1?.title ?: "Scan Device",
            subtitle = song1?.artist ?: "Find all audio",
            imageUrl = song1?.albumArtUri,
            icon = Icons.Default.MusicNote,
            modifier = Modifier.weight(1f),
            onClick = {
              if (song1 != null) onSongClick(song1, allSongs) else onScanClick()
            }
          )
          QuickTile(
            title = song2?.title ?: "Demo Music",
            subtitle = song2?.artist ?: "Load 4 songs",
            imageUrl = song2?.albumArtUri,
            icon = Icons.Default.PlayArrow,
            modifier = Modifier.weight(1f),
            onClick = {
              if (song2 != null) onSongClick(song2, allSongs) else onLoadDemoClick()
            }
          )
        }
      }
    }

    // Empty state callout if library has 0 songs
    if (allSongs.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SpotifyCardElevated)
            .padding(20.dp)
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "No Audio Files Loaded Yet",
              color = SpotifyWhite,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Scan your device storage for offline music or load our instant high-fidelity demo songs with synced lyrics!",
              color = SpotifyTextSecondary,
              fontSize = 13.sp,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = onScanClick,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                modifier = Modifier.weight(1f)
              ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = SpotifyBlack)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Device", color = SpotifyBlack, fontWeight = FontWeight.Bold)
              }
              OutlinedButton(
                onClick = onLoadDemoClick,
                modifier = Modifier.weight(1f)
              ) {
                Text("Load Demo", color = SpotifyWhite)
              }
            }
          }
        }
      }
    }

    // Recently Played Section
    if (recentlyPlayed.isNotEmpty()) {
      item {
        SectionHeader(title = "Recently played")
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          items(recentlyPlayed) { song ->
            SongCard(
              song = song,
              onClick = { onSongClick(song, recentlyPlayed) }
            )
          }
        }
      }
    }

    // Made for You (Most Played) Section
    if (mostPlayed.isNotEmpty()) {
      item {
        SectionHeader(title = "Made For You (Heavy Rotation)")
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          items(mostPlayed) { song ->
            SongCard(
              song = song,
              onClick = { onSongClick(song, mostPlayed) }
            )
          }
        }
      }
    }

    // Artists Section
    if (artists.isNotEmpty()) {
      item {
        SectionHeader(title = "Your Artists")
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          items(artists) { artistName ->
            ArtistCircleCard(
              artistName = artistName,
              onClick = { onArtistClick(artistName) }
            )
          }
        }
      }
    }

    // All Local Tracks Section
    if (allSongs.isNotEmpty()) {
      item {
        SectionHeader(title = "All Songs (${allSongs.size})")
      }
      items(allSongs.take(15)) { song ->
        SongRowItem(
          song = song,
          onClick = { onSongClick(song, allSongs) }
        )
      }
    }
  }
}

@Composable
fun SectionHeader(title: String) {
  Text(
    text = title,
    color = SpotifyWhite,
    fontSize = 20.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 12.dp)
  )
}

@Composable
private fun QuickTile(
  title: String,
  subtitle: String,
  gradient: List<Color>? = null,
  imageUrl: String? = null,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  iconTint: Color = SpotifyWhite,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Row(
    modifier = modifier
      .height(56.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(SpotifyCardElevated)
      .clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .then(
          if (gradient != null) Modifier.background(Brush.linearGradient(gradient))
          else Modifier.background(SpotifyDivider)
        ),
      contentAlignment = Alignment.Center
    ) {
      if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
          model = imageUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint)
      }
    }

    Spacer(modifier = Modifier.width(10.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        color = SpotifyWhite,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = subtitle,
        color = SpotifyTextSecondary,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun SongCard(
  song: Song,
  onClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .width(140.dp)
      .clickable(onClick = onClick)
  ) {
    Box(
      modifier = Modifier
        .size(140.dp)
        .clip(RoundedCornerShape(8.dp))
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
          modifier = Modifier.size(48.dp)
        )
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
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
}

@Composable
fun ArtistCircleCard(
  artistName: String,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .width(100.dp)
      .clickable(onClick = onClick)
  ) {
    Box(
      modifier = Modifier
        .size(90.dp)
        .clip(CircleShape)
        .background(
          Brush.linearGradient(listOf(SpotifyGreen.copy(alpha = 0.5f), SpotifyBlue.copy(alpha = 0.5f)))
        ),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = artistName.take(2).uppercase(),
        color = SpotifyWhite,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
      )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = artistName,
      color = SpotifyWhite,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun SongRowItem(
  song: Song,
  onClick: () -> Unit,
  trailingContent: @Composable (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(SpotifyCardElevated),
      contentAlignment = Alignment.Center
    ) {
      if (!song.albumArtUri.isNullOrBlank()) {
        AsyncImage(
          model = song.albumArtUri,
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
        Text(
          text = song.artist,
          color = SpotifyTextSecondary,
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    if (trailingContent != null) {
      trailingContent()
    }
  }
}
