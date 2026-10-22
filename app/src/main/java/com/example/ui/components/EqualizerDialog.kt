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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.EqualizerBand
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyCardElevated
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyTextSecondary
import com.example.ui.theme.SpotifyWhite

@Composable
fun EqualizerDialog(
  bands: List<EqualizerBand>,
  currentPreset: String,
  availablePresets: List<String>,
  onBandChange: (Short, Short) -> Unit,
  onPresetChange: (String) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Audio Equalizer",
        color = SpotifyWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("equalizer_dialog")
      ) {
        Text(
          text = "PRESETS",
          color = SpotifyTextSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Preset Chips
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(availablePresets) { preset ->
            val isSelected = preset == currentPreset
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) SpotifyGreen else SpotifyDivider)
                .clickable { onPresetChange(preset) }
                .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
              Text(
                text = preset,
                color = if (isSelected) SpotifyBlack else SpotifyWhite,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
          text = "FREQUENCY BANDS",
          color = SpotifyTextSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (bands.isEmpty()) {
          Text(
            text = "Equalizer is active during playback.",
            color = SpotifyTextSecondary,
            fontSize = 13.sp
          )
        } else {
          bands.forEach { band ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = formatFreq(band.centerFreqHz),
                  color = SpotifyWhite,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "${band.currentLevelMilliBels / 100} dB",
                  color = SpotifyGreen,
                  fontSize = 12.sp
                )
              }
              Slider(
                value = band.currentLevelMilliBels.toFloat(),
                onValueChange = { onBandChange(band.bandIndex, it.toInt().toShort()) },
                valueRange = band.minLevelMilliBels.toFloat()..band.maxLevelMilliBels.toFloat(),
                colors = SliderDefaults.colors(
                  thumbColor = SpotifyGreen,
                  activeTrackColor = SpotifyGreen,
                  inactiveTrackColor = SpotifyDivider
                )
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
      ) {
        Text("Done", color = SpotifyBlack, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = SpotifyCardElevated
  )
}

private fun formatFreq(freqKHz: Int): String {
  return if (freqKHz >= 1000) {
    "${freqKHz / 1000} kHz"
  } else {
    "$freqKHz Hz"
  }
}
