# PageGrab v3 (Drop A)

Paste ANY link (or share one to PageGrab from any app).

1. **yt-dlp engine first.** Bundled yt-dlp + ffmpeg ask what is on the link (YouTube, TikTok, Instagram, X
   and 1000+ more). Pick a quality, queue it, and it downloads **in the background** via a foreground
   service with a progress notification — you can leave the app.
2. **Not supported by yt-dlp?** The page sniffer runs: hidden WebView, network + DOM scan, lists every
   video/audio/image/document/archive it finds.
3. **Direct file links** download as-is.

Everything lands in `Downloads/PageGrab/`.

## What's new in Drop A
- **Background downloads**: a foreground service (`DownloadService`) drains a job queue so downloads
  survive leaving the app, with a progress notification.
- **Queue, retry, cancel**: `JobQueue` tracks state per job; failed jobs auto-retry up to your Settings
  limit; cancel support is wired at the data layer.
- **History**: every finished job is logged locally (title, url, type, success) — see the History screen.
- **Auto-update yt-dlp**: on by default, runs quietly on launch; also a manual button in Settings.
- **Full Settings screen**: Downloads, Engine, Media, **Proxy** (HTTP/SOCKS4/SOCKS5, with a Test button),
  **Temp Mail** (opt-in), Notifications/Theme, Page Sniffer, Storage, About.
- **10-language string bundle with RTL**: English, Urdu, Arabic, Persian, Hindi, Turkish, Indonesian,
  Spanish, French, Russian. Urdu/Arabic/Persian get full RTL layout. Pick the language from
  Android's own per-app language setting (Settings > Apps > PageGrab > Language, Android 13+).
  **Translations are machine-quality** — please open a PR if something reads wrong; each language is
  one plain `strings.xml` file.
- **Manual in-app login** (`LoginActivity`): a normal WebView where **you** log into **your own**
  account on the real site. PageGrab just keeps the resulting cookies so the engine can reuse that
  session. Nothing auto-fills passwords or auto-submits login/signup forms.
- **Temp mail, opt-in only** (off by default): generates a disposable inbox (via the mail.tm API),
  copies the address to your clipboard so you can paste it into a signup form yourself, and shows a
  "Temp Inbox" you check/tap through manually to read verification emails or click a verify link —
  exactly like a normal inbox, just disposable. It does not auto-fill forms, auto-click verify links,
  or auto-create accounts in the background.

## Build (no Android Studio needed)
1. Push this folder to your GitHub repo (branch `main`).
2. Actions tab -> "Build APK" -> download the `PageGrab-APK` artifact.
3. Unzip and install `app-debug.apk`.

## Honest limits in this drop
- **Proxy** covers the engine and plain file downloads. The page sniffer's own network requests
  (inside the hidden WebView) do **not** yet route through your custom proxy — that needs a
  WebView proxy controller, which is Drop B.
- **Queue UI**: the data model (`JobQueue`) fully supports cancel/retry, but MainActivity currently
  shows one combined status line rather than a per-job list with Cancel/Retry buttons — that visible
  queue screen is the first thing in Drop B.
- Temp-mail signups fail on sites that block known disposable-mail domains — no way around that.
- Keep battery optimization off for PageGrab (Android will ask, or set it in system battery settings)
  so long background jobs aren't killed.
- Still arm64 only, GPL-3.0 (bundles yt-dlp), no DRM, ever.
- I can't compile this here — if Actions fails, send me the log.

## Next (Drop B / C)
Playlists, visible queue screen with per-job cancel/retry buttons, sizes per quality, subtitle
language picker, HLS/DASH sniffing, aria2c speed, Material 3 UI + real icon, signed release, F-Droid.
