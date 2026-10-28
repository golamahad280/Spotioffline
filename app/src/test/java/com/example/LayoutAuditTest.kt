package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height as layoutHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.model.LrcLine
import com.example.data.model.Song
import com.example.ui.components.LyricsScreen
import com.example.ui.components.SettingsRow
import com.example.ui.screens.LibrarySongRow
import com.example.ui.screens.SongRowItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
class LayoutAuditTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val veryLongTitle = "Trinidad_Cardona_-_Dinero(128k)_" +
      "বাংলা_গান_রবীন্দ্র_সঙ্গীত_super_long_remix_extra_words_1234567890_extended_club_mix_edition_bonus_track"

  private val testSong = Song(
    id = "audit_song_1",
    title = veryLongTitle,
    artist = "Artist_বাংলা_নাম_Very_Long_Artist_Name_1234567890_Extra_Long",
    album = "Album",
    durationMs = 215000L,
    uriString = "content://media/external/audio/media/1",
    filePath = "/storage/emulated/0/Music/song.mp3",
    hasLyrics = true,
    isSyncedLyrics = false,
    lyricsText = "First line of plain lyrics\nSecond line of plain lyrics",
    lyricsScript = "Bengali"
  )

  @Test
  fun testSettingsRow_fontScale1_0() {
    verifySettingsRowAtScale(1.0f)
  }

  @Test
  fun testSettingsRow_fontScale1_3() {
    verifySettingsRowAtScale(1.3f)
  }

  private fun verifySettingsRowAtScale(fontScale: Float) {
    composeRule.setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(density = LocalDensity.current.density, fontScale = fontScale)
      ) {
        MaterialTheme {
          Box(modifier = Modifier.width(360.dp)) {
            SettingsRow(
              icon = Icons.Default.BugReport,
              title = "Crash & Stability Log - Very long descriptive settings title that wraps",
              subtitle = "Inspect and export runtime diagnostic information, stack traces and ANRs",
              trailing = "View Logs >",
              onClick = {}
            )
          }
        }
      }
    }

    // Trailing text must be on one line
    val trailingNode = composeRule.onNodeWithText("View Logs >", useUnmergedTree = true)
    trailingNode.assertExists()
    val trailingBounds = trailingNode.getUnclippedBoundsInRoot()
    val trailingHeight = (trailingBounds.bottom - trailingBounds.top).value
    // A single line of text at fontScale 1.3 is well under 45dp high
    assertTrue(
      "Trailing text should be single line: height was $trailingHeight",
      trailingHeight < 45f
    )

    // Row height must be < 120 dp and >= 48 dp
    val rowNode = composeRule.onNode(hasClickAction())
    rowNode.assertExists()
    val rowBounds = rowNode.getUnclippedBoundsInRoot()
    val rowHeight = (rowBounds.bottom - rowBounds.top).value
    assertTrue("Settings row height must be < 120dp: height was $rowHeight", rowHeight < 120f)
    assertTrue("Settings row min height must be >= 48dp: height was $rowHeight", rowHeight >= 48f)
  }

  @Test
  fun testLyricsScreenHeader_fontScale1_0() {
    verifyLyricsScreenHeaderAtScale(1.0f)
  }

  @Test
  fun testLyricsScreenHeader_fontScale1_3() {
    verifyLyricsScreenHeaderAtScale(1.3f)
  }

  private fun verifyLyricsScreenHeaderAtScale(fontScale: Float) {
    composeRule.setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(density = LocalDensity.current.density, fontScale = fontScale)
      ) {
        MaterialTheme {
          Box(modifier = Modifier.width(360.dp).layoutHeight(800.dp)) {
            LyricsScreen(
              song = testSong,
              lyricsLines = listOf(
                LrcLine(0L, "First line of plain lyrics"),
                LrcLine(5000L, "Second line of plain lyrics")
              ),
              activeLyricIndex = 0,
              currentPositionMs = 15000L,
              isPlaying = true,
              onDismiss = {},
              onSeekTo = {},
              onPlayPause = {},
              onUpdateOffset = {},
              onSaveManualLyrics = { _, _ -> }
            )
          }
        }
      }
    }

    // Close button
    val closeBtn = composeRule.onNodeWithContentDescription("Close Lyrics")
    closeBtn.assertExists()
    val closeBounds = closeBtn.getUnclippedBoundsInRoot()
    val closeW = (closeBounds.right - closeBounds.left).value
    val closeH = (closeBounds.bottom - closeBounds.top).value
    assertTrue("Close button width >= 40dp: was $closeW", closeW >= 40f)
    assertTrue("Close button height >= 40dp: was $closeH", closeH >= 40f)

    val syncBtn = composeRule.onNodeWithTag("lyrics_sync_toggle")
    syncBtn.assertExists()
    val syncBounds = syncBtn.getUnclippedBoundsInRoot()
    val editBtn = composeRule.onNodeWithTag("edit_lyrics_button")
    editBtn.assertExists()
    val editBounds = editBtn.getUnclippedBoundsInRoot()
    val titleNode = composeRule.onNodeWithText(veryLongTitle)
    val titleBounds = titleNode.getUnclippedBoundsInRoot()
    println("DEBUG closeBounds=$closeBounds syncBounds=$syncBounds editBounds=$editBounds titleBounds=$titleBounds")
    val editW = (editBounds.right - editBounds.left).value
    val editH = (editBounds.bottom - editBounds.top).value
    assertTrue("Edit button width >= 40dp: was $editW", editW >= 40f)
    assertTrue("Edit button height >= 40dp: was $editH", editH >= 40f)

    // Sync toggle button
    val syncW = (syncBounds.right - syncBounds.left).value
    val syncH = (syncBounds.bottom - syncBounds.top).value
    assertTrue("Sync button width >= 40dp: was $syncW", syncW >= 40f)
    assertTrue("Sync button height >= 40dp: was $syncH", syncH >= 40f)

    // Labelled action bar buttons under header
    composeRule.onNodeWithText("Edit").assertExists()
    composeRule.onNodeWithText("Tap to sync").assertExists()
    composeRule.onNodeWithText("Offset").assertExists()
    composeRule.onNodeWithText("Other versions").assertExists()

    // Title should be present
    composeRule.onNodeWithText(veryLongTitle).assertExists()
  }

  @Test
  fun testSongRowItem_fontScale1_3() {
    composeRule.setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 1.3f)
      ) {
        MaterialTheme {
          Box(modifier = Modifier.width(360.dp)) {
            SongRowItem(
              song = testSong,
              onClick = {},
              trailingContent = {
                IconButton(
                  onClick = {},
                  modifier = Modifier.testTag("song_more_button")
                ) {
                  Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
              }
            )
          }
        }
      }
    }

    val moreBtn = composeRule.onNodeWithTag("song_more_button")
    moreBtn.assertExists()
    val moreBounds = moreBtn.getUnclippedBoundsInRoot()
    val moreW = (moreBounds.right - moreBounds.left).value
    val moreH = (moreBounds.bottom - moreBounds.top).value
    assertTrue("More button width >= 40dp: was $moreW", moreW >= 40f)
    assertTrue("More button height >= 40dp: was $moreH", moreH >= 40f)
  }

  @Test
  fun testLibrarySongRow_fontScale1_3() {
    composeRule.setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 1.3f)
      ) {
        MaterialTheme {
          Box(modifier = Modifier.width(360.dp)) {
            LibrarySongRow(
              song = testSong,
              isMultiSelectMode = false,
              isSelected = false,
              onClick = {},
              onLongClick = {},
              onMoreClick = {},
              menuContent = {}
            )
          }
        }
      }
    }

    val moreBtn = composeRule.onNodeWithTag("song_more_button")
    moreBtn.assertExists()
    val moreBounds = moreBtn.getUnclippedBoundsInRoot()
    val moreW = (moreBounds.right - moreBounds.left).value
    assertTrue("Library song row more button >= 40dp: was $moreW", moreW >= 40f)
  }
}
