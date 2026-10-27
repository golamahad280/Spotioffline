package com.example.utils

object ScriptDetector {

  /**
   * Pure function to detect the dominant Unicode script in the given text.
   * Returns one of: "Bengali", "Devanagari", "Arabic", "Cyrillic", "CJK", "Japanese",
   * "Korean", "Tamil", "Telugu", "Malayalam", "Kannada", "Gujarati", "Punjabi", "Thai",
   * "Latin", or null if no identifiable letters exist.
   */
  fun detectScript(text: String?): String? {
    if (text.isNullOrBlank()) return null

    val counts = mutableMapOf<String, Int>()

    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val script = getScriptForCodePoint(cp)
      if (script != null) {
        counts[script] = (counts[script] ?: 0) + 1
      }
      i += Character.charCount(cp)
    }

    if (counts.isEmpty()) return null

    // Return the script with highest character count
    return counts.maxByOrNull { it.value }?.key
  }

  /**
   * Returns a display badge label in the native script or English (e.g. "বাংলা", "हिन्दी", "English").
   */
  fun getScriptBadge(script: String?): String? {
    return when (script) {
      "Bengali" -> "বাংলা"
      "Devanagari" -> "हिन्दी"
      "Arabic" -> "العربية"
      "Cyrillic" -> "Русский"
      "CJK" -> "中文"
      "Japanese" -> "日本語"
      "Korean" -> "한국어"
      "Tamil" -> "தமிழ்"
      "Telugu" -> "తెలుగు"
      "Malayalam" -> "മലയാളം"
      "Kannada" -> "ಕನ್ನಡ"
      "Gujarati" -> "ગુજરાતી"
      "Punjabi" -> "ਪੰਜਾਬੀ"
      "Thai" -> "ไทย"
      "Latin" -> "English"
      else -> script
    }
  }

  private fun getScriptForCodePoint(cp: Int): String? {
    return when (cp) {
      // Bengali
      in 0x0980..0x09FF -> "Bengali"
      // Devanagari (Hindi, Marathi, Sanskrit, Nepali)
      in 0x0900..0x097F -> "Devanagari"
      // Arabic / Urdu / Persian
      in 0x0600..0x06FF, in 0x0750..0x077F, in 0x08A0..0x08FF -> "Arabic"
      // Cyrillic (Russian, etc.)
      in 0x0400..0x04FF, in 0x0500..0x052F -> "Cyrillic"
      // Japanese Hiragana & Katakana
      in 0x3040..0x309F, in 0x30A0..0x30FF -> "Japanese"
      // Korean Hangul
      in 0xAC00..0xD7AF, in 0x1100..0x11FF, in 0x3130..0x318F -> "Korean"
      // CJK Ideographs (Chinese, Japanese Kanji)
      in 0x4E00..0x9FFF, in 0x3400..0x4DBF, in 0x20000..0x2A6DF -> "CJK"
      // Tamil
      in 0x0B80..0x0BFF -> "Tamil"
      // Telugu
      in 0x0C00..0x0C7F -> "Telugu"
      // Malayalam
      in 0x0D00..0x0D7F -> "Malayalam"
      // Kannada
      in 0x0C80..0x0CFF -> "Kannada"
      // Gujarati
      in 0x0A80..0x0AFF -> "Gujarati"
      // Gurmukhi / Punjabi
      in 0x0A00..0x0A7F -> "Punjabi"
      // Thai
      in 0x0E00..0x0E7F -> "Thai"
      // Latin alphabet & Latin Extended
      in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F, in 0x1E00..0x1EFF -> "Latin"
      else -> null
    }
  }
}
