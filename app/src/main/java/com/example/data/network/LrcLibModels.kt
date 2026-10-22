package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LrcLibResponse(
  val id: Long? = null,
  @Json(name = "trackName") val trackName: String? = null,
  @Json(name = "artistName") val artistName: String? = null,
  @Json(name = "albumName") val albumName: String? = null,
  val duration: Double? = null,
  val instrumental: Boolean? = null,
  @Json(name = "plainLyrics") val plainLyrics: String? = null,
  @Json(name = "syncedLyrics") val syncedLyrics: String? = null
)

@JsonClass(generateAdapter = true)
data class ITunesSearchResult(
  val resultCount: Int = 0,
  val results: List<ITunesTrack> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ITunesTrack(
  @Json(name = "trackName") val trackName: String? = null,
  @Json(name = "artistName") val artistName: String? = null,
  @Json(name = "collectionName") val collectionName: String? = null,
  @Json(name = "artworkUrl100") val artworkUrl100: String? = null
)
