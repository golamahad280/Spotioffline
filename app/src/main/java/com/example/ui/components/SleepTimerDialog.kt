package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyWhite

@Composable
fun SleepTimerDialog(
  currentMinutesRemaining: Int,
  onSelectMinutes: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  val options = listOf(
    Pair("15 minutes", 15),
    Pair("30 minutes", 30),
    Pair("45 minutes", 45),
    Pair("60 minutes", 60),
    Pair("Turn off timer", 0)
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Sleep Timer",
        color = SpotifyWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sleep_timer_dialog"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        options.forEach { (label, minutes) ->
          val isSelected = (minutes > 0 && currentMinutesRemaining > 0 && minutes == currentMinutesRemaining) ||
              (minutes == 0 && currentMinutesRemaining == 0)

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(if (isSelected) SpotifyGreen.copy(alpha = 0.2f) else SpotifyDivider.copy(alpha = 0.4f))
              .clickable { onSelectMinutes(minutes) }
              .padding(horizontal = 16.dp, vertical = 14.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = label,
                color = if (isSelected) SpotifyGreen else SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
              )
              if (isSelected && minutes > 0) {
                Text(
                  text = "${currentMinutesRemaining}m left",
                  color = SpotifyGreen,
                  fontSize = 12.sp
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = SpotifyWhite)
      }
    },
    containerColor = SpotifyCardElevated
  )
}
