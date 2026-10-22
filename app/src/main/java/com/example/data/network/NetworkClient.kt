package com.example.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .addInterceptor { chain ->
      val original = chain.request()
      val request = original.newBuilder()
        .header("User-Agent", "SpotiOffline/1.0 (https://github.com/aistudio/spotioffline; contact@example.com)")
        .header("Accept", "application/json")
        .build()
      chain.proceed(request)
    }
    .build()

  private val lrcLibRetrofit: Retrofit by lazy {
    Retrofit.Builder()
      .baseUrl("https://lrclib.net/")
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }

  private val iTunesRetrofit: Retrofit by lazy {
    Retrofit.Builder()
      .baseUrl("https://itunes.apple.com/")
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }

  val lrcLibService: LrcLibService by lazy {
    lrcLibRetrofit.create(LrcLibService::class.java)
  }

  val iTunesService: ITunesService by lazy {
    iTunesRetrofit.create(ITunesService::class.java)
  }
}
