# PageGrab v6 (Chrome-style browser)

Paste ANY link (or share one from any app) to download video, audio, images, documents, or
archives from 1000+ platforms via a bundled yt-dlp + ffmpeg engine, with a page-sniffer fallback
for sites yt-dlp doesn't know. Background downloads, History, a real app icon, a proxy/language-
ready Settings screen, and signed-release support.

## New in this drop — Browser redesign
- **Chrome-style top bar**: home icon, a rounded pill search/address bar, and a `⋮` overflow menu
  — just like the reference screenshot. Typing a plain query searches Google; a URL/domain opens
  directly.
- **Temp Mail as an "extension" chip**: a small ✉ icon sits right in the address bar (only visible
  once enabled) — tap it to open/close the temp inbox panel. Still off by default; the first time
  you open the browser you still get the one-time opt-in popup, and it can also be toggled from
  the `⋮` menu.
- **Everything else moved into the `⋮` menu**, Chrome-style: Incognito toggle, Desktop site
  toggle, Open in external browser, Share this page, Settings.
- Android's system Back button now navigates web history first (back through pages), same as a
  real browser, before leaving the screen.

## Build (debug, for testing)
1. Push to `main`.
2. Actions tab -> "Build APK" -> download the `PageGrab-APK` artifact.

## Build (signed release)
See `SIGNING.md`, then `git tag v1.0.0 && git push origin v1.0.0` — the signed APK lands on the
repo's Releases page automatically.

## Honest limits
- Single tab by design (no tab switcher/counter) — this is a login/download helper, not a daily
  driver browser.
- Proxy still doesn't cover the browser/sniffer's own WebView traffic, only the engine and plain
  file downloads.
- Still arm64 only, GPL-3.0 (bundles yt-dlp), no DRM, ever.
- I can't compile this here — if Actions fails, send me the log.
