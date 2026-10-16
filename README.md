# PageGrab v2

Paste ANY link (or share one to PageGrab from any app).

1. **yt-dlp engine first.** Bundled yt-dlp + ffmpeg ask what is on the link (YouTube, TikTok, Instagram, X and 1000+ more).
   You get a picker: BEST MP4, 1080/720/480/360p, audio M4A/MP3, thumbnail.
   Files are merged with ffmpeg, metadata is embedded, and the original `.info.json` is saved next to the file.
2. **Not supported by yt-dlp? The page sniffer runs.** The page loads in a hidden WebView, network traffic + DOM are scanned,
   and every video / audio / image / document / archive found is listed.
3. **Direct file links** download as-is through Android's DownloadManager.

Everything lands in `Downloads/PageGrab/`.

## Build (no Android Studio needed)
1. Push this folder to a GitHub repo (branch `main`).
2. Actions tab -> "Build APK" -> download the `PageGrab-APK` artifact.
3. Unzip and install `app-debug.apk`.

## Notes
- First launch unpacks the engine (~15 s). Keep the app open while an engine download runs (screen stays on).
- YouTube changes often: tap **UPDATE ENGINE (yt-dlp)** when downloads start failing.
- arm64 only (small APK). Add `armeabi-v7a` in `app/build.gradle.kts` for old 32-bit phones.
- No DRM content. No in-app login yet. Not for content you have no right to download.

## License
GPL-3.0 (required because it bundles youtubedl-android, which is GPL-3.0). See LICENSE.
