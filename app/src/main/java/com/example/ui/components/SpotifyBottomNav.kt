package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BottomTab
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyDivider
import com.example.ui.theme.SpotifyTextMuted
import com.example.ui.theme.SpotifyWhite

@Composable
fun SpotifyBottomNav(
  currentTab: BottomTab,
  onTabSelected: (BottomTab) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(SpotifyBlack)
      .windowInsetsPadding(WindowInsets.navigationBars)
  ) {
    HorizontalDivider(thickness = 0.5.dp, color = SpotifyDivider)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      NavItem(
        label = "Home",
        selected = currentTab == BottomTab.HOME,
        activeIcon = Icons.Filled.Home,
        inactiveIcon = Icons.Outlined.Home,
        testTag = "nav_home",
        onClick = { onTabSelected(BottomTab.HOME) }
      )
      NavItem(
        label = "Search",
        selected = currentTab == BottomTab.SEARCH,
        activeIcon = Icons.Filled.Search,
        inactiveIcon = Icons.Outlined.Search,
        testTag = "nav_search",
        onClick = { onTabSelected(BottomTab.SEARCH) }
      )
      NavItem(
        label = "Your Library",
        selected = currentTab == BottomTab.LIBRARY,
        activeIcon = Icons.Filled.LibraryMusic,
        inactiveIcon = Icons.Outlined.LibraryMusic,
        testTag = "nav_library",
        onClick = { onTabSelected(BottomTab.LIBRARY) }
      )
    }
  }
}

@Composable
private fun NavItem(
  label: String,
  selected: Boolean,
  activeIcon: ImageVector,
  inactiveIcon: ImageVector,
  testTag: String,
  onClick: () -> Unit
) {
  val color = if (selected) SpotifyWhite else SpotifyTextMuted
  Column(
    modifier = Modifier
      .testTag(testTag)
      .minimumInteractiveComponentSize()
      .clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = if (selected) activeIcon else inactiveIcon,
      contentDescription = label,
      tint = color,
      modifier = Modifier.size(24.dp)
    )
    Text(
      text = label,
      color = color,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
  }
}
