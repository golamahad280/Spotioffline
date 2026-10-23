package com.example.data.lyrics.sources

import com.example.data.lyrics.LyricsCandidateDebug
import com.example.data.lyrics.LyricsDebugStep

data class LyricsSourceResult(
  val syncedLyrics: String? = null,
  val plainLyrics: String? = null,
  val sourceName: String? = null,
  val candidateList: List<LyricsCandidateDebug> = emptyList(),
  val isAiGenerated: Boolean = false
)

interface LyricsSource {
  val name: String

  suspend fun fetchLyrics(
    cleanedTitle: String,
    effectiveArtist: String?,
    effectiveAlbum: String?,
    durationSec: Int,
    filePath: String?,
    onStepLogged: (LyricsDebugStep) -> Unit
  ): LyricsSourceResult?
}
