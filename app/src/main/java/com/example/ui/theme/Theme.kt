package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SpotifyColorScheme = darkColorScheme(
  primary = SpotifyGreen,
  onPrimary = SpotifyBlack,
  primaryContainer = SpotifyCardElevated,
  onPrimaryContainer = SpotifyGreenBright,
  secondary = SpotifyGreenBright,
  onSecondary = SpotifyBlack,
  background = SpotifyBlack,
  onBackground = SpotifyWhite,
  surface = SpotifyCard,
  onSurface = SpotifyWhite,
  surfaceVariant = SpotifyCardElevated,
  onSurfaceVariant = SpotifyTextSecondary,
  outline = SpotifyDivider
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = SpotifyColorScheme,
    typography = Typography,
    content = content
  )
}
