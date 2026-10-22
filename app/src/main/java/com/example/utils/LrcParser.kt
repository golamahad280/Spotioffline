package com.example.utils

import com.example.data.model.LrcLine
import java.util.regex.Pattern

object LrcParser {
  private val TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
  private val OFFSET_PATTERN = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)

  fun parse(rawText: String?): List<LrcLine> {
    if (rawText.isNullOrBlank()) return emptyList()

    val result = mutableListOf<LrcLine>()
    val lines = rawText.lines()
    var fileOffsetMs = 0L

    // Check for [offset: +/-ms] tag
    for (line in lines) {
      val trimmed = line.trim()
      val offsetMatcher = OFFSET_PATTERN.matcher(trimmed)
      if (offsetMatcher.find()) {
        fileOffsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
        break
      }
    }

    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.isEmpty()) continue

      // Ignore standard ID3/LRC metadata headers
      if (trimmed.startsWith("[ti:", ignoreCase = true) ||
          trimmed.startsWith("[ar:", ignoreCase = true) ||
          trimmed.startsWith("[al:", ignoreCase = true) ||
          trimmed.startsWith("[by:", ignoreCase = true) ||
          trimmed.startsWith("[offset:", ignoreCase = true) ||
          trimmed.startsWith("[re:", ignoreCase = true) ||
          trimmed.startsWith("[ve:", ignoreCase = true)) {
        continue
      }

      val matcher = TIMESTAMP_PATTERN.matcher(trimmed)
      val timestamps = mutableListOf<Long>()
      var lastMatchEnd = 0

      while (matcher.find()) {
        val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
        val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
        val millisRaw = matcher.group(3)
        val millis = when {
          millisRaw == null -> 0L
          millisRaw.length == 1 -> (millisRaw.toLongOrNull() ?: 0L) * 100
          millisRaw.length == 2 -> (millisRaw.toLongOrNull() ?: 0L) * 10
          else -> millisRaw.take(3).toLongOrNull() ?: 0L
        }

        val totalMs = (minutes * 60 + seconds) * 1000 + millis + fileOffsetMs
        timestamps.add(totalMs.coerceAtLeast(0L))
        lastMatchEnd = matcher.end()
      }

      if (timestamps.isNotEmpty()) {
        val lyricsText = trimmed.substring(lastMatchEnd).trim()
        if (lyricsText.isNotEmpty()) {
          for (time in timestamps) {
            result.add(LrcLine(timestampMs = time, text = lyricsText))
          }
        }
      }
    }

    return result.sortedBy { it.timestampMs }
  }

  fun findActiveLineIndex(lines: List<LrcLine>, positionMs: Long, offsetMs: Long = 0L): Int {
    if (lines.isEmpty()) return -1
    val targetTime = positionMs + offsetMs
    if (targetTime < lines.first().timestampMs) return 0

    var low = 0
    var high = lines.size - 1
    var bestIndex = 0

    while (low <= high) {
      val mid = (low + high) ushr 1
      if (lines[mid].timestampMs <= targetTime) {
        bestIndex = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }

    return bestIndex
  }
}
