# SpotiOffline — Offline Music Player

A production-quality, mobile-first offline music player built for Android that looks and feels like **Spotify Premium** (dark theme `#121212`, `#181818`, Spotify Green `#1DB954`), playing audio files stored directly on your device with real-time **synced lyrics** (via LRCLIB & embedded LRC) and high-res cover art.

---

## 📱 Where is My APK?

The built Android application package (`.apk`) is located right at the root of the project:

```
📁 APK_HERE/
   └── 🎵 OfflineMusic.apk
```

You can download `OfflineMusic.apk` directly to install it on your Android phone or tablet.

---

## 🚀 How to Get Your APK

### Option A: Direct Download from this Project (Easiest)
1. In the file explorer on the left or top-level project directory, open the folder **`APK_HERE/`**.
2. Download **`OfflineMusic.apk`** to your computer or phone.

---

### Option B: Automatic GitHub Actions (No Android Studio Needed)
If you pushed or exported this repository to GitHub:
1. Go to your GitHub repository in your browser.
2. Click on the **Actions** tab at the top.
3. Click on the latest workflow run: **Build Offline Music APK**.
4. Scroll down to the **Artifacts** section at the bottom.
5. Click **OfflineMusic-APK** to download the ZIP file containing the freshly compiled `.apk`!

---

### Option C: Build from Terminal / Android Studio
If you have Android SDK / Gradle installed on your machine:
```bash
# Using npm
npm run build:apk

# Or using Gradle directly:
./gradlew assembleDebug
```
The build script automatically copies the finished APK into `APK_HERE/OfflineMusic.apk`.

---

## 📲 How to Install the APK on Your Android Device

1. **Transfer the APK to your phone:**
   - Send `OfflineMusic.apk` to your phone via USB, Google Drive, Telegram, WhatsApp, or download it directly from your phone's browser.
2. **Open the APK:**
   - Tap on `OfflineMusic.apk` in your phone's File Manager or Downloads notification.
3. **Allow Unknown Apps (one-time Android prompt):**
   - If prompted *"For your security, your phone is not allowed to install unknown apps from this source"*:
   - Tap **Settings** in the popup.
   - Toggle **Allow from this source** ON.
   - Go back and tap **Install**.
4. **Grant Audio Permissions:**
   - On first launch, tap **Allow** when the app asks for audio permissions so it can scan and display your songs.
5. **Enjoy:**
   - Tap **Scan Device** to index all music on your storage, or test right away using the 4 built-in demo tracks with synced lyrics!

---

## 🌟 Key Features

- **Spotify Premium Aesthetic:** Pixel-level matching dark theme, floating mini-player with progress line, full-screen Now Playing modal with dominant artwork color gradient.
- **Synced Lyrics:** Dynamic lyrics view with auto-scrolling, current line highlights, tap-to-seek, and per-track manual sync offset slider (±5s in 50ms steps).
- **Offline Audio Scanner:** Fast query of Android MediaStore (`mp3`, `flac`, `wav`, `m4a`, `ogg`), ID3 embedded tag extraction, and SAF folder/file import.
- **5-Band Equalizer & Presets:** Bass Boost, Vocal Boost, Rock, Pop, Electronic, Jazz, and Flat.
- **Sleep Timer:** Automatic playback shutoff presets (15m, 30m, 45m, 60m).
- **Playlists & Liked Songs:** Create playlists, add/remove songs, and heart favorite tracks.
- **Background Playback:** Foreground media service with lock-screen notification controls.
