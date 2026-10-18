# PageGrab v5 (icon + signed releases)

Paste ANY link (or share one from any app) to download video, audio, images, documents, or
archives from 1000+ platforms via a bundled yt-dlp + ffmpeg engine, with a page-sniffer fallback
for sites yt-dlp doesn't know. Full in-app browser (login, incognito, temp mail opt-in),
background downloads, History, and a proxy/language-ready Settings screen.

## New in this drop
- **Real app icon**: a generated adaptive icon (accent-blue rounded square/circle + white download
  arrow), wired as both legacy (`mipmap-*`) and adaptive (API 26+, `mipmap-anydpi-v26`) icons.
- **Signed release pipeline**: a new `Signed Release` GitHub Actions workflow builds a properly
  signed APK and attaches it to a GitHub Release whenever you push a `v*` tag. See `SIGNING.md`
  for the one-time keystore setup (your private key never touches this repo).
- The regular `Build APK` debug workflow (on every push to `main`) is unchanged, for quick testing.

## Build (debug, for testing)
1. Push to `main`.
2. Actions tab -> "Build APK" -> download the `PageGrab-APK` artifact.

## Build (signed release, for real users)
See `SIGNING.md`, then:
```bash
git tag v1.0.0 && git push origin v1.0.0
```
Grab the signed APK from the repo's **Releases** page.

## Honest limits
- The icon is a clean generated mark (blue rounded square, white download arrow) — good enough to
  ship, but a designer pass is still worth doing before a store listing push.
- No multi-tab browser (by design). No visible queue screen yet (cancel/retry exist in the data
  layer; the UI only shows one status line) — next polish item.
- Still arm64 only, GPL-3.0 (bundles yt-dlp), no DRM, ever.
- I can't compile this here — if Actions fails, send me the log.
