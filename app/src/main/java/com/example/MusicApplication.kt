package com.example

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.utils.CrashLogger
import java.io.File

class MusicApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    // 1. Install crash logger & uncaught exception handler
    CrashLogger.install(this)
    CrashLogger.i("MusicApplication", "Application started. Build Stamp: ${BuildConfig.BUILD_STAMP}")

    // 2. Configure Coil image loader with 20% memory cache and disk cache
    try {
      val imageLoader = ImageLoader.Builder(this)
        .memoryCache {
          MemoryCache.Builder(this)
            .maxSizePercent(0.20)
            .build()
        }
        .diskCache {
          DiskCache.Builder()
            .directory(File(cacheDir, "image_cache"))
            .maxSizeBytes(100L * 1024 * 1024)
            .build()
        }
        .respectCacheHeaders(false)
        .build()
      Coil.setImageLoader(imageLoader)
    } catch (e: Exception) {
      CrashLogger.e("MusicApplication", "Failed to configure Coil ImageLoader", e)
    }
  }
}
