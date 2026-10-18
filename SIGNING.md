# Signing PageGrab for release

Run this on YOUR machine (Termux works) — the private key never leaves your device except as a
secret GitHub stores encrypted. Do this once.

## 1. Generate the keystore
```bash
keytool -genkeypair -v \
  -keystore pagegrab-release.keystore \
  -alias pagegrab \
  -keyalg RSA -keysize 2048 -validity 10000
```
It asks for a keystore password, your name/org (can be anything), and a key password
(can be the same as the keystore password). **Save both passwords somewhere safe** — losing this
keystore means you can never publish an update to the same app under the same package name again.

## 2. Base64-encode it
```bash
base64 -w 0 pagegrab-release.keystore > keystore.b64
cat keystore.b64
```
Copy the entire output (one long line).

## 3. Add 4 GitHub secrets
Repo -> Settings -> Secrets and variables -> Actions -> New repository secret. Add all four:

| Secret name          | Value                                   |
|-----------------------|------------------------------------------|
| `KEYSTORE_BASE64`     | the long string from step 2              |
| `KEYSTORE_PASSWORD`   | the keystore password you set            |
| `KEY_ALIAS`           | `pagegrab` (or whatever alias you used)  |
| `KEY_PASSWORD`        | the key password you set                 |

## 4. Cut a release
```bash
git tag v1.0.0
git push origin v1.0.0
```
The `Signed Release` workflow builds a signed `app-release.apk`, attaches it to a new GitHub
Release automatically, and deletes the keystore file from the runner when it's done — it's never
written back to the repo.

## Keep a personal backup
Keep `pagegrab-release.keystore` and both passwords somewhere safe outside GitHub too (password
manager, encrypted drive). If you ever lose it, every future release needs a new package name —
there's no recovery.
