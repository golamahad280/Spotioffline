package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite

@Composable
fun FixMatchDialog(
  song: Song?,
  onConfirm: (songId: String, title: String, artist: String, album: String) -> Unit,
  onDismiss: () -> Unit
) {
  if (song == null) return

  var title by remember { mutableStateOf(song.title) }
  var artist by remember { mutableStateOf(song.artist) }
  var album by remember { mutableStateOf(song.album) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Fix Song Match",
        color = SpotifyWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("fix_match_dialog")
      ) {
        Text(
          text = "Correct song tags to fetch accurate synced lyrics and high-res cover art.",
          color = SpotifyTextSecondary,
          fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Song Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = artist,
          onValueChange = { artist = it },
          label = { Text("Artist") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = album,
          onValueChange = { album = it },
          label = { Text("Album") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(song.id, title.trim(), artist.trim(), album.trim())
        },
        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
      ) {
        Text("Save & Re-Fetch", color = SpotifyBlack, fontWeight = FontWeight.Bold)
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
