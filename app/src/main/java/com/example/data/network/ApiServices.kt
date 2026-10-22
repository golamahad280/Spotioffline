package com.example.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibService {
  @GET("api/get")
  suspend fun getLyrics(
    @Query("track_name") trackName: String,
    @Query("artist_name") artistName: String,
    @Query("album_name") albumName: String? = null,
    @Query("duration") duration: Int? = null
  ): retrofit2.Response<LrcLibResponse>

  @GET("api/search")
  suspend fun searchLyrics(
    @Query("q") query: String
  ): retrofit2.Response<List<LrcLibResponse>>
}

interface ITunesService {
  @GET("search")
  suspend fun searchSong(
    @Query("term") term: String,
    @Query("entity") entity: String = "song",
    @Query("limit") limit: Int = 1
  ): retrofit2.Response<ITunesSearchResult>
}
