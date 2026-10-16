# PageGrab

Paste ANY link. If it's a direct file, it downloads. If it's a web page, PageGrab loads it
in a hidden WebView (JavaScript runs), sniffs the network traffic + the page itself, and lists
every video / audio / image / document / archive it finds, with size and type.
Tick what you want, hit DOWNLOAD. Files are saved byte-for-byte (original metadata intact)
to `Downloads/PageGrab/`, plus a `.json` log (page URL, title, per-file URL/type/size/last-modified).

Also works from the Android Share menu: share a link to PageGrab and it scans instantly.

## Build (no Android Studio needed)
1. Push this folder to a GitHub repo (branch `main`).
2. Actions tab -> "Build APK" runs automatically -> download the `PageGrab-APK` artifact.
3. Unzip, install `app-debug.apk`.

Local: open in Android Studio, or run `gradle assembleDebug` (Gradle 8.9, JDK 17).

## Lite v1 limits
- No HLS/DASH (.m3u8/.mpd) merging yet: they are listed but skipped.
- No DRM content. Ever.
- No in-app login yet (only cookies the hidden WebView already got).
- Zero third-party libraries on purpose: tiny APK.

## Roadmap
v2: yt-dlp + ffmpeg for streams, in-app login, queue, metadata viewer.
