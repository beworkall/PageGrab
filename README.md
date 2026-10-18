# PageGrab v4 (UI + full browser)

Paste ANY link (or share one to PageGrab from any app) to download video, audio, images,
documents, or archives from 1000+ platforms via a bundled yt-dlp + ffmpeg engine, with a
page-sniffer fallback for sites yt-dlp doesn't know.

## New in this drop
- **Enhanced UI**: consistent dark theme, rounded cards/buttons, accent color, icon toolbar
  (⚙ Settings, 🕘 History, 🌐 Browser) across every screen.
- **Full in-app browser** (`BrowserActivity`, replaces the old cramped login box): real address
  bar, back/forward/refresh, progress bar, Desktop-site toggle, Open-outside button.
- **Incognito mode**: toggle in the browser — while on, no cookies/history/cache are kept, and
  everything is wiped when you turn it off or close the screen.
- **Temp Mail, opt-in**: off by default; first time you open the browser you get a one-time
  popup asking if you want it on, for signup privacy. You still type the password and tap
  Sign Up/Verify yourself — nothing auto-submits.
- **OAuth popups fixed**: Google/Facebook-style "sign in" windows (`window.open()`) now open in
  a real popup instead of silently failing.
- Logging in normally (not Incognito) keeps the session cookie, which the engine reuses
  automatically on later downloads from that same site/domain.

## Build (no Android Studio needed)
1. Push this folder to your GitHub repo (branch `main`).
2. Actions tab -> "Build APK" -> download the `PageGrab-APK` artifact.
3. Unzip and install `app-debug.apk`.

## Honest limits
- This is still a hand-rolled View-based UI, not Material 3 — it's a visual pass (dark theme,
  rounded cards, icon toolbar, consistent spacing), not a redesign framework. A real Material 3
  pass with a proper icon is the next polish step before store listing.
- No multi-tab browser (by design — one reused tab keeps it lean for a download tool).
- Proxy still doesn't cover the browser/sniffer's own WebView traffic, only the engine and plain
  file downloads.
- Still arm64 only, GPL-3.0 (bundles yt-dlp), no DRM, ever.
- I can't compile this here — if Actions fails, send me the log.

## Before public release
- Real app icon + adaptive icon.
- Visible download queue screen (cancel/retry buttons), not just one status line.
- Signed release build + versioned GitHub Release, instead of the debug APK from Actions.
